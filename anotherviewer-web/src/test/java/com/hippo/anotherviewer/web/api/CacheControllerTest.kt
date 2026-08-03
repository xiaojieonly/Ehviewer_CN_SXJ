package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.service.ImageCacheService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class CacheControllerTest {

    private lateinit var imageCacheService: ImageCacheService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        imageCacheService = mock(ImageCacheService::class.java)
        mockMvc = MockMvcBuilders.standaloneSetup(CacheController(imageCacheService))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `clearGalleryCache returns success when entries were removed`() {
        `when`(imageCacheService.clearGalleryCache(123L)).thenReturn(true)

        mockMvc.perform(delete("/api/v1/cache/gallery/123"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
        verify(imageCacheService).clearGalleryCache(123L)
    }

    @Test
    fun `clearGalleryCache returns 404 uniform envelope when nothing was removed`() {
        `when`(imageCacheService.clearGalleryCache(404L)).thenReturn(false)

        mockMvc.perform(delete("/api/v1/cache/gallery/404"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.status").value(404))
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.error.traceId").exists())
    }
}
