package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.dto.MaintenanceCleanResponse
import com.hippo.anotherviewer.web.dto.MaintenanceDownloadIssue
import com.hippo.anotherviewer.web.dto.MaintenanceFileIssue
import com.hippo.anotherviewer.web.dto.MaintenanceKind
import com.hippo.anotherviewer.web.dto.MaintenancePreviewResponse
import com.hippo.anotherviewer.web.service.DownloadMaintenanceService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class MaintenanceControllerTest {

    private lateinit var maintenanceService: DownloadMaintenanceService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        maintenanceService = mock(DownloadMaintenanceService::class.java)
        mockMvc = MockMvcBuilders.standaloneSetup(MaintenanceController(maintenanceService))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `preview returns the scan result`() {
        `when`(maintenanceService.preview()).thenReturn(
            MaintenancePreviewResponse(
                redundantFiles = listOf(MaintenanceFileIssue("777", 42)),
                invalidDownloads = listOf(MaintenanceDownloadIssue(1, 300, "T", "content_dir_missing"))
            )
        )

        mockMvc.perform(get("/api/v1/download/maintenance/preview"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.redundantFiles[0].path").value("777"))
            .andExpect(jsonPath("$.redundantFiles[0].sizeBytes").value(42))
            .andExpect(jsonPath("$.invalidDownloads[0].id").value(1))
            .andExpect(jsonPath("$.invalidDownloads[0].reason").value("content_dir_missing"))
    }

    @Test
    fun `clean forwards the kind and returns the outcome`() {
        `when`(maintenanceService.clean(MaintenanceKind.INVALID_DOWNLOADS)).thenReturn(
            MaintenanceCleanResponse(MaintenanceKind.INVALID_DOWNLOADS, 0, 3, 128)
        )

        mockMvc.perform(
            post("/api/v1/download/maintenance/clean")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"kind":"INVALID_DOWNLOADS"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.kind").value("INVALID_DOWNLOADS"))
            .andExpect(jsonPath("$.removedDownloads").value(3))
            .andExpect(jsonPath("$.removedFiles").value(0))
            .andExpect(jsonPath("$.freedBytes").value(128))

        verify(maintenanceService).clean(MaintenanceKind.INVALID_DOWNLOADS)
    }

    @Test
    fun `clean with null kind yields 400 VALIDATION_ERROR`() {
        mockMvc.perform(
            post("/api/v1/download/maintenance/clean")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
    }

    @Test
    fun `clean with unknown kind yields 400 with unparseable-body envelope`() {
        // 未知枚举值 = 消息不可读，走 GlobalExceptionHandler 的
        // HttpMessageNotReadableException 分支（400 / code=BAD_REQUEST）。
        mockMvc.perform(
            post("/api/v1/download/maintenance/clean")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"kind":"NOT_A_KIND"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))

        verifyNoInteractions(maintenanceService)
    }
}
