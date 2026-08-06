package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.dto.FavoriteAddRequest
import com.hippo.anotherviewer.web.dto.FavoriteListResponse
import com.hippo.anotherviewer.web.dto.FavoriteRemoveRequest
import com.hippo.anotherviewer.web.service.FavoriteService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
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

    @Test
    fun `list without params forwards defaults`() {
        `when`(favoriteService.listFavorites(anyInt(), anyInt(), anyInt(), nullable(String::class.java), anyBoolean()))
            .thenReturn(FavoriteListResponse(emptyList(), 0, 1))

        mockMvc.perform(get("/api/v1/favorite/list"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.favorites").isArray)
        verify(favoriteService).listFavorites(anyInt(), anyInt(), anyInt(), nullable(String::class.java), anyBoolean())
    }

    @Test
    fun `list forwards q and regex params`() {
        `when`(favoriteService.listFavorites(anyInt(), anyInt(), anyInt(), eq("futa"), eq(true)))
            .thenReturn(FavoriteListResponse(emptyList(), 0, 1))

        mockMvc.perform(get("/api/v1/favorite/list").param("q", "futa").param("regex", "true"))
            .andExpect(status().isOk)
        verify(favoriteService).listFavorites(anyInt(), anyInt(), anyInt(), eq("futa"), eq(true))

        mockMvc.perform(get("/api/v1/favorite/list").param("q", "futa"))
            .andExpect(status().isOk)
        verify(favoriteService).listFavorites(anyInt(), anyInt(), anyInt(), eq("futa"), eq(false))
    }

    @Test
    fun `list invalid regex yields 400 REGEX_INVALID envelope`() {
        `when`(favoriteService.listFavorites(anyInt(), anyInt(), anyInt(), any(), anyBoolean()))
            .thenThrow(IllegalArgumentException("正则表达式无效: Unclosed group near index 1"))

        mockMvc.perform(get("/api/v1/favorite/list").param("q", "(").param("regex", "true"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.status").value(400))
            .andExpect(jsonPath("$.error.code").value("REGEX_INVALID"))
            .andExpect(jsonPath("$.error.traceId").exists())
    }
}
