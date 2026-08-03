package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.processing.ImageProcessingService
import com.hippo.anotherviewer.web.processing.ProcessingTaskStatus
import com.hippo.anotherviewer.web.processing.TaskState
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

class ProcessingControllerTest {

    private lateinit var processingService: ImageProcessingService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        processingService = mock(ImageProcessingService::class.java)
        mockMvc = MockMvcBuilders.standaloneSetup(ProcessingController(processingService))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `trigger accepts a valid payload and returns the task`() {
        `when`(processingService.resolvePageCount(123L)).thenReturn(5)
        `when`(processingService.submitGallery(anyLong(), any(), any())).thenReturn("task-1")
        val status = ProcessingTaskStatus(
            taskId = "task-1", galleryId = 123L, state = TaskState.PROCESSING,
            totalPages = 5, processedPages = 0, failedPages = 0, currentPage = 0,
            startedAt = null, completedAt = null, error = null
        )
        `when`(processingService.getTaskStatus("task-1")).thenReturn(status)

        mockMvc.perform(
            post("/api/v1/process/gallery/123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"type":"UPSCALE_2X","outputFormat":"png","quality":90}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.taskId").value("task-1"))
    }

    @Test
    fun `trigger rejects quality out of range with 400 envelope`() {
        mockMvc.perform(
            post("/api/v1/process/gallery/123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"type":"UPSCALE_2X","outputFormat":"png","quality":0}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.status").value(400))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("quality must be between 1 and 100"))
            .andExpect(jsonPath("$.error.traceId").exists())
        verify(processingService, never()).resolvePageCount(anyLong())
    }

    @Test
    fun `trigger returns 404 envelope when the gallery page count cannot be resolved`() {
        `when`(processingService.resolvePageCount(404L)).thenReturn(null)

        mockMvc.perform(
            post("/api/v1/process/gallery/404")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{}""")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.status").value(404))
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.error.traceId").exists())
    }

    @Test
    fun `trigger returns 409 envelope when a task is already running`() {
        `when`(processingService.resolvePageCount(123L)).thenReturn(5)
        `when`(processingService.submitGallery(anyLong(), any(), any()))
            .thenThrow(IllegalStateException("already running"))

        mockMvc.perform(
            post("/api/v1/process/gallery/123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.status").value(409))
            .andExpect(jsonPath("$.error.code").value("CONFLICT"))
            .andExpect(jsonPath("$.error.traceId").exists())
    }

    @Test
    fun `status returns 404 envelope for an unknown task`() {
        `when`(processingService.getTaskStatus("nope")).thenReturn(null)

        mockMvc.perform(get("/api/v1/process/status/nope"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.error.traceId").exists())
    }
}
