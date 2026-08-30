package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.dto.JobType
import com.hippo.anotherviewer.web.repository.BlackListRepository
import com.hippo.anotherviewer.web.repository.BookmarkInfoRepository
import com.hippo.anotherviewer.web.repository.DownloadDirnameRepository
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import com.hippo.anotherviewer.web.repository.DownloadLabelRepository
import com.hippo.anotherviewer.web.repository.FilterRepository
import com.hippo.anotherviewer.web.repository.GalleryTagsRepository
import com.hippo.anotherviewer.web.repository.HistoryInfoRepository
import com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository
import com.hippo.anotherviewer.web.repository.QuickSearchRepository
import com.hippo.anotherviewer.web.service.EhImportService
import com.hippo.anotherviewer.web.service.InMemoryJobStore
import com.hippo.anotherviewer.web.service.Job
import com.hippo.anotherviewer.web.service.JobService
import com.hippo.anotherviewer.web.service.SiteSessionManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.context.ApplicationEventPublisher
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * POST /api/v1/backup/import-ehviewer 异步化契约测试（方案附录 A2）。
 *
 * - 202 + {jobId, state}：JobService 接受提交（prep/worker 为 mock，不真正执行）。
 * - 活跃任务 → 409 code=CONFLICT（IllegalStateException 映射）。
 * - 空文件 → 400 code=IMPORT_FAILED：用真实 EhImportService + 真实 JobService 走
 *   真实 prep 路径（prepareImport 的空文件校验），证明校验落在 prep 里。
 */
class ImportControllerTest {

    private lateinit var ehImportService: EhImportService
    private lateinit var jobService: JobService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        ehImportService = mock(EhImportService::class.java)
        jobService = mock(JobService::class.java)
        mockMvc = MockMvcBuilders.standaloneSetup(ImportController(ehImportService, jobService))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    private fun principal(name: String): RequestPostProcessor = RequestPostProcessor { request ->
        request.userPrincipal = UsernamePasswordAuthenticationToken(
            name, null, listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
        request
    }

    private fun jobDb(): MockMultipartFile =
        MockMultipartFile("file", "ehviewer.db", "application/octet-stream", ByteArray(16) { 1 })

    @Test
    fun `submit returns 202 with jobId and state`() {
        val job = Job("job-abc12345", JobType.IMPORT)
        `when`(jobService.submit(any(), any(), any())).thenReturn(job)

        mockMvc.perform(multipart("/api/v1/backup/import-ehviewer").file(jobDb()).with(principal("alice")))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.jobId").value("job-abc12345"))
            .andExpect(jsonPath("$.state").value("PENDING"))
    }

    @Test
    fun `submit while an import job is active returns 409 CONFLICT envelope`() {
        `when`(jobService.submit(any(), any(), any()))
            .thenThrow(IllegalStateException("已有 IMPORT 任务进行中"))

        mockMvc.perform(multipart("/api/v1/backup/import-ehviewer").file(jobDb()).with(principal("alice")))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.status").value(409))
            .andExpect(jsonPath("$.error.code").value("CONFLICT"))
            .andExpect(jsonPath("$.error.message").value("已有 IMPORT 任务进行中"))
            .andExpect(jsonPath("$.error.traceId").exists())
    }

    @Test
    fun `empty file is rejected with 400 IMPORT_FAILED from prep`() {
        // 真实服务 + 真实 JobService：prep 内的 prepareImport 空文件校验抛 400。
        val realService = EhImportService(
            mock(LocalFavoriteInfoRepository::class.java),
            mock(HistoryInfoRepository::class.java),
            mock(DownloadInfoRepository::class.java),
            mock(BookmarkInfoRepository::class.java),
            mock(FilterRepository::class.java),
            mock(QuickSearchRepository::class.java),
            mock(DownloadLabelRepository::class.java),
            mock(DownloadDirnameRepository::class.java),
            mock(BlackListRepository::class.java),
            mock(GalleryTagsRepository::class.java),
            mock(SiteSessionManager::class.java),
            mock(com.hippo.anotherviewer.web.service.DownloadDirIndex::class.java),
        )
        val realJobService = JobService(InMemoryJobStore(), mock(ApplicationEventPublisher::class.java))
        val mvc = MockMvcBuilders.standaloneSetup(ImportController(realService, realJobService))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

        val empty = MockMultipartFile("file", "empty.db", "application/octet-stream", ByteArray(0))
        mvc.perform(multipart("/api/v1/backup/import-ehviewer").file(empty).with(principal("alice")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.status").value(400))
            .andExpect(jsonPath("$.error.code").value("IMPORT_FAILED"))
            .andExpect(jsonPath("$.error.message").value("上传的 EhViewer 备份文件为空"))
            .andExpect(jsonPath("$.error.traceId").exists())
    }
}
