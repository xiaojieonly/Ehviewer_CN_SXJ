package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.dto.SmbConfigUpdateRequest
import com.hippo.anotherviewer.web.service.SmbBackupService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class SmbControllerTest {

    private lateinit var smbBackupService: SmbBackupService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        smbBackupService = mock(SmbBackupService::class.java)
        mockMvc = MockMvcBuilders.standaloneSetup(SmbController(smbBackupService))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `updateConfig accepts a valid payload and returns true`() {
        `when`(smbBackupService.updateConfig(any())).thenReturn(true)

        mockMvc.perform(
            put("/api/v1/smb/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"host":"nas.local","port":445,"share":"backup","loginMode":"GUEST","enabled":true}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").value(true))
        verify(smbBackupService).updateConfig(
            SmbConfigUpdateRequest(host = "nas.local", port = 445, share = "backup", loginMode = "GUEST", enabled = true)
        )
    }

    @Test
    fun `updateConfig rejects a port out of range with 400 envelope`() {
        mockMvc.perform(
            put("/api/v1/smb/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"host":"nas.local","port":70000,"share":"backup"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.status").value(400))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("port must be between 1 and 65535"))
            .andExpect(jsonPath("$.error.traceId").exists())
        verify(smbBackupService, never()).updateConfig(any())
    }

    @Test
    fun `testConnection rejects an overlong host with 400 envelope`() {
        mockMvc.perform(
            post("/api/v1/smb/test-connection")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"host":"${"h".repeat(256)}","port":445,"share":"backup"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        verify(smbBackupService, never()).testConnection(any())
    }
}
