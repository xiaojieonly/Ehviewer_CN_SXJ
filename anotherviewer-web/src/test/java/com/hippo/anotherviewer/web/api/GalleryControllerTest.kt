package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.dto.AddHistoryRequest
import com.hippo.anotherviewer.web.dto.GalleryListResponse
import com.hippo.anotherviewer.web.dto.TopListFeedItemDto
import com.hippo.anotherviewer.web.dto.TopListResponse
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
        verify(galleryService).addToHistory(123L, "a1b2c3d4e5", "Some Gallery", 0, null)
    }

    @Test
    fun `addToHistory forwards null title`() {
        val response = controller.addToHistory(123L, AddHistoryRequest("a1b2c3d4e5"))

        assertEquals(200, response.statusCode.value())
        verify(galleryService).addToHistory(123L, "a1b2c3d4e5", null, 0, null)
    }

    @Test
    fun `addToHistory forwards reading mode (R4-4)`() {
        val response = controller.addToHistory(123L, AddHistoryRequest("a1b2c3d4e5", "Some Gallery", mode = 5))

        assertEquals(200, response.statusCode.value())
        verify(galleryService).addToHistory(123L, "a1b2c3d4e5", "Some Gallery", 5, null)
    }

    @Test
    fun `addToHistory rejects non-positive gid with 400 envelope without calling the service`() {
        val response = controller.addToHistory(0L, AddHistoryRequest("a1b2c3d4e5"))

        assertEquals(400, response.statusCode.value())
        val body = response.body as com.hippo.anotherviewer.web.config.ApiErrorEnvelope
        assertEquals("VALIDATION_ERROR", body.error.code)
        assertTrue(body.error.traceId.isNotBlank())
        verify(galleryService, never()).addToHistory(anyLong(), anyString(), any(), anyInt(), any())
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
        verify(galleryService, never()).addToHistory(anyLong(), anyString(), any(), anyInt(), any())
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
        verify(galleryService, never()).addToHistory(anyLong(), anyString(), any(), anyInt(), any())
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
        verify(galleryService, never()).addToHistory(anyLong(), anyString(), any(), anyInt(), any())
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
        verify(galleryService, never()).addToHistory(anyLong(), anyString(), any(), anyInt(), any())
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
        verify(galleryService, never()).addToHistory(anyLong(), anyString(), any(), anyInt(), any())
    }

    // ---------------------------------------------------------------------
    // S6: page 透传——JSON 缺省（null）与显式 0 语义不同，靠判空区分
    // ---------------------------------------------------------------------

    @Test
    fun `addToHistory without page forwards null (keep stored progress)`() {
        val response = controller.addToHistory(123L, AddHistoryRequest("a1b2c3d4e5", "Some Gallery"))

        assertEquals(200, response.statusCode.value())
        // 缺省 page → null → 服务层不改写已存进度。
        verify(galleryService).addToHistory(123L, "a1b2c3d4e5", "Some Gallery", 0, null)
    }

    @Test
    fun `addToHistory with explicit page 0 forwards zero (reread)`() {
        val response = controller.addToHistory(123L, AddHistoryRequest("a1b2c3d4e5", "Some Gallery", page = 0))

        assertEquals(200, response.statusCode.value())
        // 显式 0 = 重读写 0，与缺省 null 可区分（不是判 0）。
        verify(galleryService).addToHistory(123L, "a1b2c3d4e5", "Some Gallery", 0, 0)
    }

    @Test
    fun `addToHistory forwards explicit page through the json body`() {
        mockMvc.perform(
            post("/api/v1/gallery/history/123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"a1b2c3d4e5","title":"Some Gallery","page":37}""")
        )
            .andExpect(status().isOk)
        verify(galleryService).addToHistory(123L, "a1b2c3d4e5", "Some Gallery", 0, 37)
    }

    @Test
    fun `addToHistory json without page deserializes to null page`() {
        mockMvc.perform(
            post("/api/v1/gallery/history/123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"a1b2c3d4e5","title":"Some Gallery"}""")
        )
            .andExpect(status().isOk)
        verify(galleryService).addToHistory(123L, "a1b2c3d4e5", "Some Gallery", 0, null)
    }

    // ---------------------------------------------------------------------
    // M-5: pageSize / page clamping on paginated GET endpoints
    // ---------------------------------------------------------------------

    @Test
    fun `search clamps oversized pageSize to 200`() {
        mockMvc.perform(get("/api/v1/gallery/search").param("pageSize", "999"))
            .andExpect(status().isOk)
        verify(galleryService).searchGallery(null, null, 0, 200, 0, null, null, 0, false, false, false, false)
    }

    @Test
    fun `search clamps negative pageSize up to 1`() {
        mockMvc.perform(get("/api/v1/gallery/search").param("pageSize", "0").param("page", "-3"))
            .andExpect(status().isOk)
        verify(galleryService).searchGallery(null, null, 0, 1, 0, null, null, 0, false, false, false, false)
    }

    @Test
    fun `search clamps negative page up to 0`() {
        mockMvc.perform(get("/api/v1/gallery/search").param("page", "-5"))
            .andExpect(status().isOk)
        verify(galleryService).searchGallery(null, null, 0, 20, 0, null, null, 0, false, false, false, false)
    }

    @Test
    fun `search passes in-range values through untouched`() {
        mockMvc.perform(get("/api/v1/gallery/search").param("page", "2").param("pageSize", "40"))
            .andExpect(status().isOk)
        verify(galleryService).searchGallery(null, null, 2, 40, 0, null, null, 0, false, false, false, false)
    }

    // ---------------------------------------------------------------------
    // Extended search params (openapi GET /api/v1/gallery/search v1.1)
    // ---------------------------------------------------------------------

    @Test
    fun `search passes extended params through to the service`() {
        mockMvc.perform(
            get("/api/v1/gallery/search")
                .param("keyword", "alpha")
                .param("category", "2")
                .param("sort", "2")
                .param("pageMin", "5")
                .param("pageMax", "9")
                .param("minRating", "3")
                .param("searchName", "true")
                .param("searchTags", "true")
                .param("searchDesc", "false")
                .param("searchTorrents", "true")
        )
            .andExpect(status().isOk)
        verify(galleryService).searchGallery("alpha", 2, 0, 20, 2, 5, 9, 3, true, true, false, true)
    }

    @Test
    fun `search treats absent extended params as contract defaults`() {
        mockMvc.perform(get("/api/v1/gallery/search").param("keyword", "alpha"))
            .andExpect(status().isOk)
        verify(galleryService).searchGallery("alpha", null, 0, 20, 0, null, null, 0, false, false, false, false)
    }

    @Test
    fun `search clamps out-of-contract extended params instead of rejecting`() {
        mockMvc.perform(
            get("/api/v1/gallery/search")
                .param("keyword", "alpha")
                .param("sort", "7")
                .param("pageMin", "-2")
                .param("pageMax", "-1")
                .param("minRating", "9")
        )
            .andExpect(status().isOk)
        verify(galleryService).searchGallery("alpha", null, 0, 20, 0, 0, 0, 5, false, false, false, false)
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

    // W3 R4-10: presets carry the full AdvanceSearchTable bitmask (the old
    // 0..1 validation rejected every scope/higher-bit combination).
    @Test
    fun `createQuickSearch accepts the full advanceSearch bitmask`() {
        mockMvc.perform(
            post("/api/v1/gallery/quick-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":0,"name":"wide","mode":0,"category":0,"advanceSearch":2047,"minRating":0,"pageFrom":0,"pageTo":0}""")
        )
            .andExpect(status().isOk)
        verify(galleryService).createQuickSearch(any())
    }

    @Test
    fun `createQuickSearch rejects advanceSearch beyond the bitmask with 400 envelope`() {
        mockMvc.perform(
            post("/api/v1/gallery/quick-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":0,"name":"wide","mode":0,"category":0,"advanceSearch":2048,"minRating":0,"pageFrom":0,"pageTo":0}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        verify(galleryService, never()).createQuickSearch(any())
    }

    // W3 R4-11: presets persist the sort order (contracts QuickSearchDto.sort).
    @Test
    fun `createQuickSearch accepts sort 0 to 3 and defaults absent sort`() {
        for (sort in 0..3) {
            mockMvc.perform(
                post("/api/v1/gallery/quick-search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"id":0,"name":"sorted","mode":0,"category":0,"advanceSearch":0,"minRating":0,"pageFrom":0,"pageTo":0,"sort":$sort}""")
            )
                .andExpect(status().isOk)
        }
        mockMvc.perform(
            post("/api/v1/gallery/quick-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":0,"name":"legacy","mode":0,"category":0,"advanceSearch":0,"minRating":0,"pageFrom":0,"pageTo":0}""")
        )
            .andExpect(status().isOk)
        verify(galleryService, times(5)).createQuickSearch(any())
    }

    @Test
    fun `createQuickSearch rejects out-of-range sort with 400 envelope`() {
        mockMvc.perform(
            post("/api/v1/gallery/quick-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":0,"name":"sorted","mode":0,"category":0,"advanceSearch":0,"minRating":0,"pageFrom":0,"pageTo":0,"sort":4}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        verify(galleryService, never()).createQuickSearch(any())
    }

    // ---------------------------------------------------------------------
    // feed (contracts/openapi.yaml GET /api/v1/gallery/feed)
    // ---------------------------------------------------------------------

    @Test
    fun `feed subscription forwards mode page and pageSize to the service`() {
        `when`(galleryService.feedGallery("subscription", 2, 40))
            .thenReturn(GalleryListResponse(success = true, data = emptyList(), total = 0))

        mockMvc.perform(
            get("/api/v1/gallery/feed")
                .param("mode", "subscription")
                .param("page", "2")
                .param("pageSize", "40")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
        verify(galleryService).feedGallery("subscription", 2, 40)
    }

    @Test
    fun `feed popular forwards mode with default pagination`() {
        mockMvc.perform(get("/api/v1/gallery/feed").param("mode", "popular"))
            .andExpect(status().isOk)
        verify(galleryService).feedGallery("popular", 0, 20)
    }

    @Test
    fun `feed toplist calls topListFeed and serializes TopListResponse`() {
        `when`(galleryService.topListFeed()).thenReturn(
            TopListResponse(
                success = true,
                data = listOf(
                    TopListFeedItemDto(gid = "1", token = "t1", tag = "pt1", value = "Alpha", href = "/g/1/t1")
                ),
                total = 1
            )
        )

        mockMvc.perform(get("/api/v1/gallery/feed").param("mode", "toplist"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.data[0].gid").value("1"))
            .andExpect(jsonPath("$.data[0].token").value("t1"))
            .andExpect(jsonPath("$.data[0].tag").value("pt1"))
            .andExpect(jsonPath("$.data[0].value").value("Alpha"))
            .andExpect(jsonPath("$.data[0].href").value("/g/1/t1"))
        verify(galleryService).topListFeed()
    }

    @Test
    fun `feed rejects invalid mode with frozen 400 success-false body`() {
        mockMvc.perform(get("/api/v1/gallery/feed").param("mode", "bogus"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").isNotEmpty)
        verify(galleryService, never()).feedGallery(any(), anyInt(), anyInt())
        verify(galleryService, never()).topListFeed()
    }

    @Test
    fun `feed rejects missing mode with frozen 400 success-false body`() {
        mockMvc.perform(get("/api/v1/gallery/feed"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").isNotEmpty)
        verify(galleryService, never()).feedGallery(any(), anyInt(), anyInt())
        verify(galleryService, never()).topListFeed()
    }

    @Test
    fun `feed clamps oversized pageSize to 200`() {
        mockMvc.perform(get("/api/v1/gallery/feed").param("mode", "popular").param("pageSize", "999"))
            .andExpect(status().isOk)
        verify(galleryService).feedGallery("popular", 0, 200)
    }

    @Test
    fun `feed clamps negative page up to 0 and pageSize up to 1`() {
        mockMvc.perform(
            get("/api/v1/gallery/feed").param("mode", "subscription").param("page", "-5").param("pageSize", "0")
        )
            .andExpect(status().isOk)
        verify(galleryService).feedGallery("subscription", 0, 1)
    }
}
