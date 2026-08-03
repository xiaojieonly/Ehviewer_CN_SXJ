package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.service.GalleryLookupService
import com.hippo.anotherviewer.web.service.ImageCacheService
import com.hippo.anotherviewer.web.service.PrefetchService
import com.hippo.anotherviewer.web.service.SiteSessionManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ImageProxyControllerTest {

    private lateinit var imageCacheService: ImageCacheService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        imageCacheService = mock(ImageCacheService::class.java)
        val galleryLookupService = mock(GalleryLookupService::class.java)
        val sessionManager = mock(SiteSessionManager::class.java)
        val prefetchService = mock(PrefetchService::class.java)
        mockMvc = MockMvcBuilders.standaloneSetup(
            ImageProxyController(imageCacheService, galleryLookupService, sessionManager, prefetchService)
        )
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `proxyImage returns 404 uniform envelope when the url is not cached`() {
        `when`(imageCacheService.getCachedImage("https://gallery.test/some/img.jpg")).thenReturn(null)

        mockMvc.perform(get("/api/v1/image/proxy").param("url", "https://gallery.test/some/img.jpg"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.status").value(404))
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.error.traceId").exists())
    }

    @Test
    fun `streamGalleryImage rejects a negative page with 404 uniform envelope`() {
        mockMvc.perform(get("/api/v1/image/123/-1"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.status").value(404))
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.error.traceId").exists())
    }
}
