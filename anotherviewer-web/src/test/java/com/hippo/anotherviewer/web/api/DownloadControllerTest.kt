package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.dto.DownloadAddRequest
import com.hippo.anotherviewer.web.service.DownloadService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class DownloadControllerTest {

    private lateinit var downloadService: DownloadService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        downloadService = mock(DownloadService::class.java)
        mockMvc = MockMvcBuilders.standaloneSetup(DownloadController(downloadService))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `add forwards the request and returns true`() {
        `when`(downloadService.addDownload(any())).thenReturn(true)

        mockMvc.perform(
            post("/api/v1/download/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gid":123,"token":"a1b2c3d4e5","title":"T","thumb":null,"label":0}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").value(true))
        verify(downloadService).addDownload(DownloadAddRequest(123, "a1b2c3d4e5", "T", null, 0))
    }

    @Test
    fun `add rejects blank token with 400 envelope`() {
        mockMvc.perform(
            post("/api/v1/download/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gid":123,"token":"","title":"T"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.status").value(400))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("token is required"))
            .andExpect(jsonPath("$.error.traceId").exists())
        verify(downloadService, never()).addDownload(any())
    }

    @Test
    fun `add rejects non-positive gid with 400 envelope`() {
        mockMvc.perform(
            post("/api/v1/download/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gid":0,"token":"a1b2c3d4e5"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        verify(downloadService, never()).addDownload(any())
    }

    @Test
    fun `createLabel accepts a valid label`() {
        `when`(downloadService.createLabel("MyLabel")).thenReturn(true)

        mockMvc.perform(
            post("/api/v1/download/label")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"label":"MyLabel"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").value(true))
        verify(downloadService).createLabel("MyLabel")
    }

    @Test
    fun `createLabel rejects blank label with 400 envelope`() {
        mockMvc.perform(
            post("/api/v1/download/label")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"label":""}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        verify(downloadService, never()).createLabel(any())
    }
}
