package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.dto.FavoriteAddRequest
import com.hippo.anotherviewer.web.dto.FavoriteRemoveRequest
import com.hippo.anotherviewer.web.service.FavoriteService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class FavoriteControllerTest {

    private lateinit var favoriteService: FavoriteService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        favoriteService = mock(FavoriteService::class.java)
        mockMvc = MockMvcBuilders.standaloneSetup(FavoriteController(favoriteService))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `add forwards gid token category and slot`() {
        `when`(favoriteService.addFavorite(123L, "a1b2c3d4e5", null, 2, -1)).thenReturn(true)

        mockMvc.perform(
            post("/api/v1/favorite/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gid":123,"token":"a1b2c3d4e5","category":2}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
        verify(favoriteService).addFavorite(123L, "a1b2c3d4e5", null, 2, -1)
    }

    @Test
    fun `add rejects non-positive gid with 400 envelope without calling the service`() {
        mockMvc.perform(
            post("/api/v1/favorite/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gid":0,"token":"a1b2c3d4e5","category":2}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.status").value(400))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("gid must be a positive number"))
            .andExpect(jsonPath("$.error.traceId").exists())
        verify(favoriteService, never()).addFavorite(anyLong(), any(), any(), anyInt(), anyInt())
    }

    @Test
    fun `add rejects overlong token with 400 envelope`() {
        mockMvc.perform(
            post("/api/v1/favorite/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gid":123,"token":"${"t".repeat(65)}","category":2}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        verify(favoriteService, never()).addFavorite(anyLong(), any(), any(), anyInt(), anyInt())
    }

    @Test
    fun `remove forwards gid and returns success`() {
        `when`(favoriteService.removeFavorite(123L)).thenReturn(true)

        mockMvc.perform(
            delete("/api/v1/favorite/remove")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gid":123,"token":"","category":0}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
        verify(favoriteService).removeFavorite(123L)
    }

    @Test
    fun `remove rejects non-positive gid with 400 envelope`() {
        mockMvc.perform(
            delete("/api/v1/favorite/remove")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gid":-3,"token":"","category":0}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        verify(favoriteService, never()).removeFavorite(anyLong())
    }
}
