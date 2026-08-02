package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.dto.AddHistoryRequest
import com.hippo.anotherviewer.web.service.GalleryService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
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
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

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
    fun `addToHistory rejects non-positive gid with 400 without calling the service`() {
        val response = controller.addToHistory(0L, AddHistoryRequest("a1b2c3d4e5"))

        assertEquals(400, response.statusCode.value())
        assertFalse(response.body!!["success"] as Boolean)
        assertFalse((response.body!!["message"] as String).isNullOrEmpty())
        verify(galleryService, never()).addToHistory(anyLong(), anyString(), any())
    }

    @Test
    fun `addToHistory rejects blank token with 400 json via bean validation`() {
        mockMvc.perform(
            post("/api/v1/gallery/history/123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":""}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").isNotEmpty)
        verify(galleryService, never()).addToHistory(anyLong(), anyString(), any())
    }

    @Test
    fun `addToHistory rejects missing token with 400 json via bean validation`() {
        mockMvc.perform(
            post("/api/v1/gallery/history/123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"No Token"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").isNotEmpty)
        verify(galleryService, never()).addToHistory(anyLong(), anyString(), any())
    }

    @Test
    fun `addToHistory rejects overlong token with 400 json`() {
        mockMvc.perform(
            post("/api/v1/gallery/history/123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"${"a".repeat(65)}"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").isNotEmpty)
        verify(galleryService, never()).addToHistory(anyLong(), anyString(), any())
    }

    @Test
    fun `addToHistory rejects overlong title with 400 json`() {
        mockMvc.perform(
            post("/api/v1/gallery/history/123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"a1b2c3d4e5","title":"${"x".repeat(257)}"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").isNotEmpty)
        verify(galleryService, never()).addToHistory(anyLong(), anyString(), any())
    }
}
