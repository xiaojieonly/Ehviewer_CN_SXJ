package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.service.SettingsService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class SettingsControllerTest {

    private lateinit var settingsService: SettingsService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        settingsService = mock(SettingsService::class.java)
        mockMvc = MockMvcBuilders.standaloneSetup(SettingsController(settingsService))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `update accepts a valid settings payload`() {
        `when`(settingsService.updateSettings(any())).thenReturn(true)

        mockMvc.perform(
            put("/api/v1/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"download":{"path":"/data","workerCount":7,"downloadDelay":100,"downloadTimeout":60,"maxConcurrentGalleries":3,"maxConcurrentImages":3}}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").value(true))
        verify(settingsService).updateSettings(any())
    }

    @Test
    fun `update rejects workerCount above the UI stepper bound with 400 envelope`() {
        mockMvc.perform(
            put("/api/v1/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"download":{"path":"/data","workerCount":99999,"downloadDelay":0,"downloadTimeout":60,"maxConcurrentGalleries":3,"maxConcurrentImages":3}}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.status").value(400))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("workerCount must be between 1 and 10"))
            .andExpect(jsonPath("$.error.traceId").exists())
        verify(settingsService, never()).updateSettings(any())
    }

    @Test
    fun `update rejects proxy port out of range with 400 envelope`() {
        mockMvc.perform(
            put("/api/v1/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"proxy":{"enabled":true,"type":"http","host":"127.0.0.1","port":70000,"username":"","password":""}}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("port must be between 0 and 65535"))
        verify(settingsService, never()).updateSettings(any())
    }
}
