package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.dto.AddHistoryRequest
import com.hippo.anotherviewer.web.service.GalleryService
import org.junit.jupiter.api.Assertions.*
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

class GalleryControllerTest {

    private lateinit var galleryService: GalleryService
    private lateinit var controller: GalleryController
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        galleryService = mock(GalleryService::class.java)
        controller = GalleryController(galleryService)
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    // ---------------------------------------------------------------------
    // addToHistory
    // ---------------------------------------------------------------------

    @Test
    fun `addToHistory returns success and forwards token and title`() {
        val response = controller.addToHistory(123L, AddHistoryRequest("a1b2c3d4e5", "Some Gallery"))

        assertEquals(200, response.statusCode.value())
        assertEquals(mapOf("success" to true), response.body)
        verify(galleryService).addToHistory(123L, "a1b2c3d4e5", "Some Gallery")
    }

    @Test
    fun `addToHistory forwards null title`() {
        val response = controller.addToHistory(123L, AddHistoryRequest("a1b2c3d4e5"))

        assertEquals(200, response.statusCode.value())
        verify(galleryService).addToHistory(123L, "a1b2c3d4e5", null)
    }

    @Test
    fun `addToHistory rejects non-positive gid with 400 envelope without calling the service`() {
        val response = controller.addToHistory(0L, AddHistoryRequest("a1b2c3d4e5"))

        assertEquals(400, response.statusCode.value())
        val body = response.body as com.hippo.anotherviewer.web.config.ApiErrorEnvelope
        assertEquals("VALIDATION_ERROR", body.error.code)
        assertTrue(body.error.traceId.isNotBlank())
        verify(galleryService, never()).addToHistory(anyLong(), anyString(), any())
    }

    @Test
    fun `addToHistory rejects blank token with 400 envelope via bean validation`() {
        mockMvc.perform(
            post("/api/v1/gallery/history/123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":""}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.status").value(400))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").isNotEmpty)
            .andExpect(jsonPath("$.error.traceId").exists())
        verify(galleryService, never()).addToHistory(anyLong(), anyString(), any())
    }

    @Test
    fun `addToHistory rejects missing token with 400 envelope via bean validation`() {
        mockMvc.perform(
            post("/api/v1/gallery/history/123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"No Token"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.status").value(400))
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.error.message").value("Malformed request body"))
            .andExpect(jsonPath("$.error.traceId").exists())
        verify(galleryService, never()).addToHistory(anyLong(), anyString(), any())
    }

    @Test
    fun `addToHistory rejects overlong token with 400 envelope`() {
        mockMvc.perform(
            post("/api/v1/gallery/history/123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"${"a".repeat(65)}"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.traceId").exists())
        verify(galleryService, never()).addToHistory(anyLong(), anyString(), any())
    }

    @Test
    fun `addToHistory rejects overlong title with 400 envelope`() {
        mockMvc.perform(
            post("/api/v1/gallery/history/123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"a1b2c3d4e5","title":"${"x".repeat(257)}"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.traceId").exists())
        verify(galleryService, never()).addToHistory(anyLong(), anyString(), any())
    }

    @Test
    fun `addToHistory rejects unreadable body with 400 BAD_REQUEST envelope`() {
        mockMvc.perform(
            post("/api/v1/gallery/history/123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"broken""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.error.message").value("Malformed request body"))
            .andExpect(jsonPath("$.error.traceId").exists())
        verify(galleryService, never()).addToHistory(anyLong(), anyString(), any())
    }

    // ---------------------------------------------------------------------
    // M-5: pageSize / page clamping on paginated GET endpoints
    // ---------------------------------------------------------------------

    @Test
    fun `search clamps oversized pageSize to 200`() {
        mockMvc.perform(get("/api/v1/gallery/search").param("pageSize", "999"))
            .andExpect(status().isOk)
        verify(galleryService).searchGallery(null, null, 0, 200)
    }

    @Test
    fun `search clamps negative pageSize up to 1`() {
        mockMvc.perform(get("/api/v1/gallery/search").param("pageSize", "0").param("page", "-3"))
            .andExpect(status().isOk)
        verify(galleryService).searchGallery(null, null, 0, 1)
    }

    @Test
    fun `search clamps negative page up to 0`() {
        mockMvc.perform(get("/api/v1/gallery/search").param("page", "-5"))
            .andExpect(status().isOk)
        verify(galleryService).searchGallery(null, null, 0, 20)
    }

    @Test
    fun `search passes in-range values through untouched`() {
        mockMvc.perform(get("/api/v1/gallery/search").param("page", "2").param("pageSize", "40"))
            .andExpect(status().isOk)
        verify(galleryService).searchGallery(null, null, 2, 40)
    }

    @Test
    fun `getHistory clamps oversized pageSize to 200`() {
        mockMvc.perform(get("/api/v1/gallery/history").param("pageSize", "5000"))
            .andExpect(status().isOk)
        verify(galleryService).getHistory(0, 200)
    }

    @Test
    fun `getHistory clamps negative page up to 0`() {
        mockMvc.perform(get("/api/v1/gallery/history").param("page", "-1"))
            .andExpect(status().isOk)
        verify(galleryService).getHistory(0, 20)
    }

    // ---------------------------------------------------------------------
    // getDetail 404 envelope
    // ---------------------------------------------------------------------

    @Test
    fun `getDetail returns 404 envelope when gallery is unknown`() {
        `when`(galleryService.getGalleryDetail(404L)).thenReturn(null)

        mockMvc.perform(get("/api/v1/gallery/404"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.status").value(404))
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.error.traceId").exists())
    }

    // ---------------------------------------------------------------------
    // quick-search
    // ---------------------------------------------------------------------

    @Test
    fun `createQuickSearch rejects blank name with 400 envelope`() {
        mockMvc.perform(
            post("/api/v1/gallery/quick-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":0,"name":"","mode":0,"category":0,"advanceSearch":0,"minRating":0,"pageFrom":0,"pageTo":0}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.traceId").exists())
        verify(galleryService, never()).createQuickSearch(any())
    }

    @Test
    fun `createQuickSearch rejects out-of-range minRating with 400 envelope`() {
        mockMvc.perform(
            post("/api/v1/gallery/quick-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":0,"name":"favs","mode":0,"category":0,"advanceSearch":0,"minRating":9,"pageFrom":0,"pageTo":0}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        verify(galleryService, never()).createQuickSearch(any())
    }

    @Test
    fun `createQuickSearch accepts a valid payload`() {
        mockMvc.perform(
            post("/api/v1/gallery/quick-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":0,"name":"favs","mode":0,"category":0,"advanceSearch":1,"minRating":3,"pageFrom":0,"pageTo":5}""")
        )
            .andExpect(status().isOk)
        verify(galleryService).createQuickSearch(any())
    }
}
