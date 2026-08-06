package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.dto.BackupManifest
import com.hippo.anotherviewer.web.dto.JobState
import com.hippo.anotherviewer.web.dto.JobType
import com.hippo.anotherviewer.web.service.BackupService
import com.hippo.anotherviewer.web.service.BackupStateHolder
import com.hippo.anotherviewer.web.service.InMemoryJobStore
import com.hippo.anotherviewer.web.service.Job
import com.hippo.anotherviewer.web.service.JobService
import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.context.ApplicationEventPublisher
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupControllerTest {

    private lateinit var backupService: BackupService
    private lateinit var backupState: BackupStateHolder
    private lateinit var jobService: JobService
    private lateinit var mockMvc: MockMvc
    private val exportedFiles = mutableListOf<File>()

    @BeforeEach
    fun setUp() {
        backupService = mock(BackupService::class.java)
        backupState = BackupStateHolder()
        jobService = JobService(InMemoryJobStore(), mock(ApplicationEventPublisher::class.java))
        mockMvc = buildMockMvc(jobService)
    }

    @AfterEach
    fun tearDown() {
        exportedFiles.forEach { Files.deleteIfExists(it.toPath()) }
        exportedFiles.clear()
    }

    private fun buildMockMvc(jobService: JobService): MockMvc =
        MockMvcBuilders.standaloneSetup(BackupController(backupService, backupState, jobService))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

    private fun awaitCondition(timeoutMs: Long = 5000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(10)
        }
        throw AssertionError("条件未在 ${timeoutMs}ms 内满足")
    }

    private fun jobIdFrom(response: MockHttpServletResponse): String =
        ObjectMapper().readTree(response.contentAsString).get("jobId").asText()

    /** 最小合法备份 zip：仅含可解析的 manifest.json（slices 为空）。 */
    private fun validBackupZip(): ByteArray {
        val manifest = """
            {"formatVersion":1,"exportedAt":"2026-08-04T00:00:00","appVersion":"test",
             "slices":[],"includesDownloads":false}
        """.trimIndent()
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
        }
        return bos.toByteArray()
    }

    // ---------------------------------------------------------------------
    // restore：异步提交（202）→ 终态置 restorePending
    // ---------------------------------------------------------------------

    @Test
    fun `restore rejects an empty upload with 400 uniform envelope`() {
        val empty = MockMultipartFile("file", "empty.zip", "application/zip", ByteArray(0))

        mockMvc.perform(multipart("/api/v1/backup/restore").file(empty))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.status").value(400))
            .andExpect(jsonPath("$.error.code").value("RESTORE_FAILED"))
            .andExpect(jsonPath("$.error.message").value("上传的备份文件为空"))
            .andExpect(jsonPath("$.error.traceId").exists())
    }

    @Test
    fun `restore submits a RESTORE job and returns 202`() {
        val file = MockMultipartFile("file", "backup.zip", "application/zip", validBackupZip())

        val response = mockMvc.perform(multipart("/api/v1/backup/restore").file(file))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.jobId").value(Matchers.startsWith("job-")))
            .andExpect(jsonPath("$.state").value(Matchers.anyOf(
                Matchers.equalTo("PENDING"),
                Matchers.equalTo("RUNNING"),
            )))
            .andReturn().response

        val jobId = jobIdFrom(response)
        awaitCondition { jobService.getJob(jobId)?.state == JobState.COMPLETED }
        assertEquals(JobType.RESTORE, jobService.getJob(jobId)?.type)
    }

    @Test
    fun `successful restore flips state to restorePending true`() {
        // worker 在 restore 上阻塞，确保 202 响应读取 state 时任务尚未终态（避免竞态）。
        val latch = CountDownLatch(1)
        `when`(backupService.restore(any(BackupManifest::class.java), any(), any()))
            .thenAnswer { latch.await(5, TimeUnit.SECONDS); true }
        val file = MockMultipartFile("file", "backup.zip", "application/zip", validBackupZip())

        val response = mockMvc.perform(multipart("/api/v1/backup/restore").file(file))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.jobId").exists())
            .andExpect(jsonPath("$.state").value(Matchers.anyOf(
                Matchers.equalTo("PENDING"),
                Matchers.equalTo("RUNNING"),
            )))
            .andReturn().response

        val jobId = jobIdFrom(response)
        latch.countDown()
        awaitCondition { backupState.restorePending }
        awaitCondition { jobService.getJob(jobId)?.state == JobState.COMPLETED }

        mockMvc.perform(get("/api/v1/backup/state"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.restorePending").value(true))
    }

    @Test
    fun `failed restore leaves restorePending false`() {
        val empty = MockMultipartFile("file", "empty.zip", "application/zip", ByteArray(0))

        mockMvc.perform(multipart("/api/v1/backup/restore").file(empty))
            .andExpect(status().isBadRequest)

        mockMvc.perform(get("/api/v1/backup/state"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.restorePending").value(false))
    }

    @Test
    fun `fresh instance state endpoint reports restorePending false`() {
        mockMvc.perform(get("/api/v1/backup/state"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.restorePending").value(false))
    }

    // ---------------------------------------------------------------------
    // export/async：202 + 409
    // ---------------------------------------------------------------------

    @Test
    fun `export async submits an EXPORT job and returns 202`() {
        val mockJobService = mock(JobService::class.java)
        val running = Job("job-export0001", JobType.EXPORT).apply { state = JobState.RUNNING }
        `when`(mockJobService.submit(any(), any(), any())).thenReturn(running)
        val mvc = buildMockMvc(mockJobService)

        mvc.perform(post("/api/v1/backup/export/async"))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.jobId").value("job-export0001"))
            .andExpect(jsonPath("$.state").value("RUNNING"))
    }

    @Test
    fun `export async returns 409 when an export job is already active`() {
        val mockJobService = mock(JobService::class.java)
        `when`(mockJobService.submit(any(), any(), any()))
            .thenThrow(IllegalStateException("已有 EXPORT 任务进行中"))
        val mvc = buildMockMvc(mockJobService)

        mvc.perform(post("/api/v1/backup/export/async"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("CONFLICT"))
    }

    // ---------------------------------------------------------------------
    // GET /export/{jobId}：404 / 409 / 200
    // ---------------------------------------------------------------------

    @Test
    fun `download export returns 404 envelope for unknown job`() {
        val mockJobService = mock(JobService::class.java)
        `when`(mockJobService.getJob("job-nope0000")).thenReturn(null)
        val mvc = buildMockMvc(mockJobService)

        val result = mvc.perform(get("/api/v1/backup/export/job-nope0000"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted())
            .andReturn()
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(result))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("EXPORT_NOT_FOUND"))
            .andExpect(jsonPath("$.error.status").value(404))
    }

    @Test
    fun `download export returns 409 envelope while job is not completed`() {
        val mockJobService = mock(JobService::class.java)
        `when`(mockJobService.getJob("job-export0001")).thenReturn(
            Job("job-export0001", JobType.EXPORT).apply { state = JobState.RUNNING }
        )
        val mvc = buildMockMvc(mockJobService)

        val result = mvc.perform(get("/api/v1/backup/export/job-export0001"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted())
            .andReturn()
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(result))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("EXPORT_NOT_READY"))
    }

    @Test
    fun `download export streams the packed zip for a completed job`() {
        val jobId = "job-export0001"
        val zipFile = File(System.getProperty("java.io.tmpdir"), "anotherviewer-export-job-$jobId.zip")
        Files.write(zipFile.toPath(), byteArrayOf(1, 2, 3, 4))
        exportedFiles += zipFile
        val mockJobService = mock(JobService::class.java)
        `when`(mockJobService.getJob(jobId)).thenReturn(
            Job(jobId, JobType.EXPORT).apply {
                state = JobState.COMPLETED
                result = mapOf(
                    "downloadUrl" to "/api/v1/backup/export/$jobId",
                    "filename" to "anotherviewer-backup-20260806-120000.zip",
                    "sizeBytes" to zipFile.length(),
                )
            }
        )
        val mvc = buildMockMvc(mockJobService)

        val result = mvc.perform(get("/api/v1/backup/export/$jobId"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted())
            .andReturn()
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(result))
            .andExpect(status().isOk)
            .andExpect(content().contentType("application/zip"))
            .andExpect(header().string(
                "Content-Disposition",
                Matchers.containsString("anotherviewer-backup-20260806-120000.zip")
            ))
            .andExpect(content().bytes(byteArrayOf(1, 2, 3, 4)))
        org.junit.jupiter.api.Assertions.assertFalse(zipFile.exists(), "流式下发后临时文件应被删除")
    }

    @Test
    fun `backup state holder new instance defaults to not pending`() {
        // 直接验证 holder 语义：新实例（= 服务重启后）restorePending 复位为 false。
        val fresh = BackupStateHolder()
        org.junit.jupiter.api.Assertions.assertFalse(fresh.restorePending)
        fresh.restorePending = true
        assertTrue(fresh.restorePending)
    }
}
