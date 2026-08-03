package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.service.BackupService
import com.hippo.anotherviewer.web.service.BackupStateHolder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupControllerTest {

    private lateinit var backupService: BackupService
    private lateinit var backupState: BackupStateHolder
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        backupService = mock(BackupService::class.java)
        backupState = BackupStateHolder()
        mockMvc = MockMvcBuilders.standaloneSetup(BackupController(backupService, backupState))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

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

    // ---------------------------------------------------------------------
    // R4-2: restore 运行态感知（state 端点 + restorePending 标志）
    // ---------------------------------------------------------------------

    @Test
    fun `fresh instance state endpoint reports restorePending false`() {
        mockMvc.perform(get("/api/v1/backup/state"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.restorePending").value(false))
    }

    @Test
    fun `successful restore flips state to restorePending true`() {
        val file = MockMultipartFile("file", "backup.zip", "application/zip", validBackupZip())

        mockMvc.perform(multipart("/api/v1/backup/restore").file(file))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

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
    fun `backup state holder new instance defaults to not pending`() {
        // 直接验证 holder 语义：新实例（= 服务重启后）restorePending 复位为 false。
        val fresh = BackupStateHolder()
        org.junit.jupiter.api.Assertions.assertFalse(fresh.restorePending)
        fresh.restorePending = true
        org.junit.jupiter.api.Assertions.assertTrue(fresh.restorePending)
    }
}
