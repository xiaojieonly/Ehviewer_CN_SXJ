package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.service.WebProxyManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ProxyControllerTest {

    private lateinit var proxyManager: WebProxyManager
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        proxyManager = mock(WebProxyManager::class.java)
        mockMvc = MockMvcBuilders.standaloneSetup(ProxyController(proxyManager))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `test rejects a port out of range with 400 envelope before any network call`() {
        mockMvc.perform(
            post("/api/v1/proxy/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"host":"127.0.0.1","port":99999}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.status").value(400))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("port must be between 0 and 65535"))
            .andExpect(jsonPath("$.error.traceId").exists())
    }

    @Test
    fun `test accepts a valid partial payload without crashing`() {
        `when`(proxyManager.settings()).thenReturn(
            com.hippo.anotherviewer.web.dto.ProxySettings()
        )

        mockMvc.perform(
            post("/api/v1/proxy/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"host":"127.0.0.1","port":8080,"type":"http"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").exists())
    }
}
