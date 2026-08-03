package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.service.BackupService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class BackupControllerTest {

    private lateinit var backupService: BackupService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        backupService = mock(BackupService::class.java)
        mockMvc = MockMvcBuilders.standaloneSetup(BackupController(backupService))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
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
}
