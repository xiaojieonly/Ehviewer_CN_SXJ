package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.dto.HistoryListResponse
import com.hippo.anotherviewer.web.service.HistoryService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mockito.*
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class HistoryControllerTest {

    private lateinit var historyService: HistoryService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        historyService = mock(HistoryService::class.java)
        mockMvc = MockMvcBuilders.standaloneSetup(HistoryController(historyService))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `list without params forwards nulls`() {
        `when`(historyService.listHistory(nullable(Int::class.java), nullable(Int::class.java), nullable(String::class.java), eq(false)))
            .thenReturn(HistoryListResponse(emptyList(), 0))

        mockMvc.perform(get("/api/v1/history/list"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.history").isArray)
        verify(historyService).listHistory(nullable(Int::class.java), nullable(Int::class.java), nullable(String::class.java), eq(false))
    }

    @Test
    fun `list forwards q and regex params`() {
        `when`(historyService.listHistory(nullable(Int::class.java), nullable(Int::class.java), eq("futa"), eq(true)))
            .thenReturn(HistoryListResponse(emptyList(), 0))

        mockMvc.perform(get("/api/v1/history/list").param("q", "futa").param("regex", "true"))
            .andExpect(status().isOk)
        verify(historyService).listHistory(nullable(Int::class.java), nullable(Int::class.java), eq("futa"), eq(true))

        mockMvc.perform(get("/api/v1/history/list").param("q", "futa"))
            .andExpect(status().isOk)
        verify(historyService).listHistory(nullable(Int::class.java), nullable(Int::class.java), eq("futa"), eq(false))
    }

    @Test
    fun `list invalid regex yields 400 REGEX_INVALID envelope`() {
        `when`(historyService.listHistory(nullable(Int::class.java), nullable(Int::class.java), any(), anyBoolean()))
            .thenThrow(IllegalArgumentException("正则表达式无效: Unclosed group near index 1"))

        mockMvc.perform(get("/api/v1/history/list").param("q", "(").param("regex", "true"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.status").value(400))
            .andExpect(jsonPath("$.error.code").value("REGEX_INVALID"))
            .andExpect(jsonPath("$.error.traceId").exists())
    }
}
