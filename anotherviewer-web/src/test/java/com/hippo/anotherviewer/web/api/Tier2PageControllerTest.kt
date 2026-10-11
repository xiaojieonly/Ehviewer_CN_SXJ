package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.service.Tier2PageService
import com.hippo.anotherviewer.web.service.Tier2PageService.Tier2PageResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * Pins the Tier-2 page API HTTP contract: host whitelist (400 VALIDATION_ERROR
 * outside the Gallery Site family), the X-Cache header (fresh on origin
 * success, stale on cached fallback), the 404 EH_UNAVAILABLE envelope when the
 * origin is unreachable with nothing cached, and verbatim pass-through of
 * upstream non-2xx/5xx statuses (no X-Cache on those).
 */
class Tier2PageControllerTest {

    private lateinit var tier2PageService: Tier2PageService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        tier2PageService = mock(Tier2PageService::class.java)
        mockMvc = MockMvcBuilders.standaloneSetup(Tier2PageController(tier2PageService))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    // ------------------------------------------------------------------
    // whitelist / validation
    // ------------------------------------------------------------------

    @Test
    fun `non-EH host is rejected with 400 VALIDATION_ERROR and never fetched`() {
        mockMvc.perform(get("/api/v1/tier2/page").param("url", "https://evil.example.com/steal"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.traceId").exists())

        verify(tier2PageService, never()).fetchPage(anyString())
    }

    @Test
    fun `malformed url is rejected with 400 VALIDATION_ERROR`() {
        mockMvc.perform(get("/api/v1/tier2/page").param("url", "not a url"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))

        verify(tier2PageService, never()).fetchPage(anyString())
    }

    @Test
    fun `lookalike hosts are not whitelisted`() {
        for (bad in listOf("https://e-hentai.org.evil.com/", "https://not-exhentai.org/g/1/a/")) {
            mockMvc.perform(get("/api/v1/tier2/page").param("url", bad))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        }
        verify(tier2PageService, never()).fetchPage(anyString())
    }

    // ------------------------------------------------------------------
    // fresh / stale / 404 contract
    // ------------------------------------------------------------------

    @Test
    fun `origin success serves the raw body with X-Cache fresh`() {
        val html = "<table class=\"itg\"><tr>gallery row</tr></table>"
        `when`(tier2PageService.fetchPage("https://e-hentai.org/?f_search=alpha&page=2"))
            .thenReturn(Tier2PageResult.Served(html, "text/html; charset=UTF-8", stale = false))

        mockMvc.perform(get("/api/v1/tier2/page").param("url", "https://e-hentai.org/?f_search=alpha&page=2"))
            .andExpect(status().isOk)
            .andExpect(header().string("X-Cache", "fresh"))
            .andExpect(header().string("Content-Type", "text/html; charset=UTF-8"))
            .andExpect(content().string(html))
    }

    @Test
    fun `origin unreachable with a cached copy serves it with X-Cache stale`() {
        val cached = "<table class=\"itg\"><tr>cached row</tr></table>"
        `when`(tier2PageService.fetchPage("https://exhentai.org/g/99/token/"))
            .thenReturn(Tier2PageResult.Served(cached, "text/html; charset=UTF-8", stale = true))

        mockMvc.perform(get("/api/v1/tier2/page").param("url", "https://exhentai.org/g/99/token/"))
            .andExpect(status().isOk)
            .andExpect(header().string("X-Cache", "stale"))
            .andExpect(content().string(cached))
    }

    @Test
    fun `origin unreachable without a cached copy is the uniform 404 envelope`() {
        `when`(tier2PageService.fetchPage("https://e-hentai.org/g/1/a/"))
            .thenReturn(Tier2PageResult.Unavailable)

        mockMvc.perform(get("/api/v1/tier2/page").param("url", "https://e-hentai.org/g/1/a/"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("EH_UNAVAILABLE"))
            .andExpect(jsonPath("$.error.traceId").exists())
            .andExpect(jsonPath("$.error.status").value(404))
    }

    @Test
    fun `body is served byte-exact for non-ascii pages`() {
        val html = "<html><title>画廊</title></html>"
        // 服务契约：body 是把原始字节按 Latin-1(每字节 1:1 映射字符)装箱的字符串，
        // controller 再以 ISO_8859_1 还原原始字节——fake 必须按同一契约装箱
        val packed = String(html.toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)
        `when`(tier2PageService.fetchPage(anyString()))
            .thenReturn(Tier2PageResult.Served(packed, "text/html; charset=UTF-8", stale = false))

        val result = mockMvc.perform(get("/api/v1/tier2/page").param("url", "https://e-hentai.org/"))
            .andExpect(status().isOk)
            .andReturn()

        assertEquals(html, String(result.response.contentAsByteArray, Charsets.UTF_8))
    }

    // ------------------------------------------------------------------
    // upstream 4xx pass-through (origin reachable, no cache semantics)
    // ------------------------------------------------------------------

    @Test
    fun `upstream 404 passes status and body through without X-Cache`() {
        `when`(tier2PageService.fetchPage("https://e-hentai.org/g/9999/zzzz/"))
            .thenReturn(Tier2PageResult.UpstreamError(404, "<div>Gallery not found</div>", "text/html; charset=UTF-8"))

        mockMvc.perform(get("/api/v1/tier2/page").param("url", "https://e-hentai.org/g/9999/zzzz/"))
            .andExpect(status().isNotFound)
            .andExpect(header().doesNotExist("X-Cache"))
            .andExpect(content().string("<div>Gallery not found</div>"))
    }

    @Test
    fun `missing url param yields the uniform 400 envelope`() {
        mockMvc.perform(get("/api/v1/tier2/page"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
    }

    // Keep the media type of JSON envelopes pinned (ApiErrors style).
    @Test
    fun `error envelopes are json`() {
        mockMvc.perform(get("/api/v1/tier2/page").param("url", "https://evil.example.com/x"))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
    }
}
