package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.client.SiteEngine
import com.hippo.anotherviewer.client.SiteUrl
import com.hippo.anotherviewer.client.data.GalleryInfo
import com.hippo.anotherviewer.client.data.ListUrlBuilder
import com.hippo.anotherviewer.client.data.SiteTopListDetail
import com.hippo.anotherviewer.client.data.topList.TopListInfo
import com.hippo.anotherviewer.client.data.topList.TopListItem
import com.hippo.anotherviewer.client.data.topList.TopListItemArray
import com.hippo.anotherviewer.client.parser.GalleryListParser
import com.hippo.anotherviewer.web.any
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.springframework.data.domain.PageImpl
import java.io.IOException

/**
 * Pins the WebUI feed endpoints (contracts/openapi.yaml GET
 * /api/v1/gallery/feed): subscription/popular reuse the search-shaped
 * GalleryListResponse via the core ListUrlBuilder + SiteEngine.getGalleryList,
 * toplist flattens the first non-empty time slot of the gallery top list.
 *
 * E2E-6 failure semantics preserved: an unreachable site surfaces
 * success=false with empty data and total 0 (same as /search).
 */
class GalleryFeedServiceTest {

    private data class Harness(val service: GalleryService, val client: OkHttpClient, val availability: EhAvailabilityService, val serverConfig: ServerConfigService)

    private fun harness(serverConfig: ServerConfigService = mock(ServerConfigService::class.java)): Harness {
        val sessionManager = mock(SiteSessionManager::class.java)
        val client = OkHttpClient()
        `when`(sessionManager.okHttpClient).thenReturn(client)
        val availability = EhAvailabilityService(mock(com.hippo.anotherviewer.web.service.WebProxyManager::class.java), "https://e-hentai.org", 5000)
        return Harness(
            GalleryService(
                mock(com.hippo.anotherviewer.web.repository.HistoryInfoRepository::class.java),
                mock(com.hippo.anotherviewer.web.repository.QuickSearchRepository::class.java),
                mock(com.hippo.anotherviewer.web.repository.GalleryTagsRepository::class.java),
                mock(com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository::class.java),
                sessionManager,
                mock(com.hippo.anotherviewer.web.repository.DownloadInfoRepository::class.java),
                com.hippo.anotherviewer.web.config.SiteCoreConfigProperties(),
                mock(GalleryLookupService::class.java),
                availability,
                mock(DownloadDirIndex::class.java),
                serverConfig,
            ),
            client,
            availability,
            serverConfig
        )
    }

    // ------------------------------------------------------------------
    // buildFeedUrl (URL assembly through the shared core ListUrlBuilder)
    // ------------------------------------------------------------------

    @Test
    fun `subscription feed url is the watched url with page param`() {
        assertEquals(SiteUrl.getWatchedUrl(), harness().service.buildFeedUrl("subscription", 0))
        assertEquals(SiteUrl.getWatchedUrl() + "?page=2", harness().service.buildFeedUrl("subscription", 2))
    }

    @Test
    fun `popular feed url is the what's hot url and ignores page`() {
        assertEquals(SiteUrl.getPopularUrl(), harness().service.buildFeedUrl("popular", 0))
        assertEquals(SiteUrl.getPopularUrl(), harness().service.buildFeedUrl("popular", 7))
    }

    // ------------------------------------------------------------------
    // subscription / popular (SiteEngine.getGalleryList)
    // ------------------------------------------------------------------

    @Test
    fun `feed subscription maps gallery list and totals pages times 25`() {
        val result = GalleryListParser.Result().apply {
            pages = 3
            galleryInfoList = listOf(
                GalleryInfo().apply {
                    gid = 11L
                    token = "tok1"
                    title = "First"
                    thumb = "https://thumbs.example/11.jpg"
                    simpleTags = arrayOf("female:fox", "artist:unknown")
                },
                GalleryInfo().apply { gid = 22L; token = "tok2"; title = "Second" }
            )
        }
        val (service, client) = harness()
        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<GalleryListParser.Result> {
                SiteEngine.getGalleryList(any(), any(), anyString(), anyInt())
            }.thenReturn(result)

            val response = service.feedGallery("subscription", 2, 20)

            assertTrue(response.success)
            assertEquals(2, response.data.size)
            assertEquals(75, response.total)
            assertEquals(11L, response.data[0].gid)
            assertEquals("tok1", response.data[0].token)
            assertEquals("First", response.data[0].title)
            assertEquals("https://thumbs.example/11.jpg", response.data[0].thumb)
            assertEquals(listOf("female:fox", "artist:unknown"), response.data[0].simpleTags)
            assertEquals(22L, response.data[1].gid)

            engine.verify {
                SiteEngine.getGalleryList(
                    null, client, SiteUrl.getWatchedUrl() + "?page=2", ListUrlBuilder.MODE_SUBSCRIPTION
                )
            }
        }
    }

    @Test
    fun `feed popular uses the what's hot mode`() {
        val result = GalleryListParser.Result().apply {
            galleryInfoList = listOf(GalleryInfo().apply { gid = 5L; token = "t" })
        }
        val (service, client) = harness()
        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<GalleryListParser.Result> {
                SiteEngine.getGalleryList(any(), any(), anyString(), anyInt())
            }.thenReturn(result)

            val response = service.feedGallery("popular", 0, 20)

            assertTrue(response.success)
            assertEquals(1, response.data.size)
            assertEquals(1, response.total)
            engine.verify {
                SiteEngine.getGalleryList(null, client, SiteUrl.getPopularUrl(), ListUrlBuilder.MODE_WHATS_HOT)
            }
        }
    }

    @Test
    fun `unreachable feed site yields success=false and empty data`() {
        // The feed URL targets the real Gallery Site host; in a
        // sandboxed/offline environment the unreachable path is exercised
        // end-to-end through SiteEngine (E2E-6, same as /search).
        val response = harness().service.feedGallery("subscription", 0, 20)

        assertFalse(response.success)
        assertTrue(response.data.isEmpty())
        assertEquals(0, response.total)
    }

    // ------------------------------------------------------------------
    // toplist (SiteEngine.getTopList)
    // ------------------------------------------------------------------

    private fun slot(items: Array<TopListItem?>): TopListItemArray =
        TopListItemArray().apply { itemArray = items }

    private fun item(gid: String, token: String? = null, tag: String? = null, value: String, href: String? = null) =
        TopListItem().apply {
            this.gid = gid
            this.token = token
            this.tag = tag
            this.value = value
            this.href = href
        }

    private fun detailWith(slots: List<Array<TopListItem?>>): SiteTopListDetail {
        val info = TopListInfo().apply {
            yesterdayTopList = slot(slots[0])
            pastMonthTopList = slot(slots[1])
            pastYearTopList = slot(slots[2])
            allTimeTopList = slot(slots[3])
        }
        return SiteTopListDetail().apply { galleryTopListInfo = info }
    }

    @Test
    fun `toplist flattens the yesterday slot when non-empty`() {
        val detail = detailWith(
            listOf(
                arrayOf(item("1", token = "t1", value = "Alpha", href = "/g/1/t1")),
                arrayOf(item("2", value = "Beta")),
                arrayOfNulls(0),
                arrayOfNulls(0)
            )
        )
        val (service, client) = harness()
        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<SiteTopListDetail> {
                SiteEngine.getTopList(any(), any(), any())
            }.thenReturn(detail)

            val response = service.topListFeed()

            assertTrue(response.success)
            assertEquals(1, response.total)
            assertEquals(1, response.data.size)
            assertEquals("1", response.data[0].gid)
            assertEquals("t1", response.data[0].token)
            assertEquals("Alpha", response.data[0].value)
            assertEquals("/g/1/t1", response.data[0].href)

            engine.verify {
                SiteEngine.getTopList(null, client, SiteUrl.getTopListUrl())
            }
        }
    }

    @Test
    fun `toplist falls back to past month when yesterday is empty`() {
        val detail = detailWith(
            listOf(
                arrayOfNulls(0),
                arrayOf(item("9", value = "PastMonthTop")),
                arrayOf(item("8", value = "PastYearTop")),
                arrayOf(item("7", value = "AllTimeTop"))
            )
        )
        val (service, _) = harness()
        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<SiteTopListDetail> {
                SiteEngine.getTopList(any(), any(), any())
            }.thenReturn(detail)

            val response = service.topListFeed()

            assertTrue(response.success)
            assertEquals(1, response.data.size)
            assertEquals("9", response.data[0].gid)
            assertEquals("PastMonthTop", response.data[0].value)
        }
    }

    @Test
    fun `toplist maps all five fields and drops null parser slots`() {
        val detail = detailWith(
            listOf(
                arrayOf(item("1", token = "t1", tag = "pt1", value = "A", href = "h1"), null, item("2", value = "B")),
                arrayOfNulls(0),
                arrayOfNulls(0),
                arrayOfNulls(0)
            )
        )
        val (service, _) = harness()
        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<SiteTopListDetail> {
                SiteEngine.getTopList(any(), any(), any())
            }.thenReturn(detail)

            val response = service.topListFeed()

            assertTrue(response.success)
            assertEquals(2, response.total)
            assertEquals("t1", response.data[0].token)
            assertEquals("pt1", response.data[0].tag)
            assertEquals("A", response.data[0].value)
            assertEquals("h1", response.data[0].href)
            assertEquals("B", response.data[1].value)
        }
    }

    @Test
    fun `toplist with no top list info yields empty data`() {
        val detail = SiteTopListDetail()
        val (service, _) = harness()
        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<SiteTopListDetail> {
                SiteEngine.getTopList(any(), any(), any())
            }.thenReturn(detail)

            val response = service.topListFeed()

            assertTrue(response.success)
            assertTrue(response.data.isEmpty())
            assertEquals(0, response.total)
        }
    }

    @Test
    fun `unreachable top list site yields success=false and empty data`() {
        // The network is reachable in this environment (curl executor + proxy),
        // so pin the failure path with a mock instead of relying on external
        // connectivity: an upstream failure must degrade to success=false.
        val (service, _) = harness()
        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<SiteTopListDetail> {
                SiteEngine.getTopList(any(), any(), any())
            }.thenThrow(IOException("unreachable"))

            val response = service.topListFeed()

            assertFalse(response.success)
            assertTrue(response.data.isEmpty())
            assertEquals(0, response.total)
        }
    }

    // ------------------------------------------------------------------
    // EH DOWN 熔断：list 端点秒回 success=false + cause，不触网（§3.2）
    // ------------------------------------------------------------------

    @Test
    fun `search with keyword returns success=false and cause EH_UNAVAILABLE while blocked`() {
        val (service, _, availability) = harness()
        availability.recordFailure("connect timed out")

        mockStatic(SiteEngine::class.java).use { engine ->
            val response = service.searchGallery("alpha", null, 0, 20)

            assertFalse(response.success)
            assertTrue(response.data.isEmpty())
            assertEquals(0, response.total)
            assertEquals("EH_UNAVAILABLE", response.cause)
            engine.verifyNoInteractions()
        }
    }

    @Test
    fun `blank keyword with local history is served locally while blocked`() {
        val historyRepository = mock(com.hippo.anotherviewer.web.repository.HistoryInfoRepository::class.java)
        `when`(historyRepository.findHistoryPaged(any())).thenReturn(
            PageImpl(
                listOf(com.hippo.anotherviewer.web.entity.HistoryInfoEntity().apply {
                    gid = 1L
                    token = "t1"
                    title = "Local"
                }),
                org.springframework.data.domain.PageRequest.of(0, 20),
                1,
            )
        )
        val sessionManager = mock(SiteSessionManager::class.java)
        `when`(sessionManager.okHttpClient).thenReturn(OkHttpClient())
        val availability = EhAvailabilityService(mock(com.hippo.anotherviewer.web.service.WebProxyManager::class.java), "https://e-hentai.org", 5000)
        availability.recordFailure("connect timed out")
        val service = GalleryService(
            historyRepository,
            mock(com.hippo.anotherviewer.web.repository.QuickSearchRepository::class.java),
            mock(com.hippo.anotherviewer.web.repository.GalleryTagsRepository::class.java),
            mock(com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository::class.java),
            sessionManager,
            mock(com.hippo.anotherviewer.web.repository.DownloadInfoRepository::class.java),
            com.hippo.anotherviewer.web.config.SiteCoreConfigProperties(),
            mock(GalleryLookupService::class.java),
            availability,
            mock(DownloadDirIndex::class.java),
            mock(ServerConfigService::class.java),        )

        val response = service.searchGallery(null, null, 0, 20)

        assertTrue(response.success)
        assertEquals(1, response.data.size)
        assertEquals("Local", response.data[0].title)
    }

    @Test
    fun `feed returns success=false and cause EH_UNAVAILABLE while blocked`() {
        val (service, _, availability) = harness()
        availability.recordFailure("connect timed out")

        mockStatic(SiteEngine::class.java).use { engine ->
            val response = service.feedGallery("subscription", 0, 20)

            assertFalse(response.success)
            assertTrue(response.data.isEmpty())
            assertEquals("EH_UNAVAILABLE", response.cause)
            engine.verifyNoInteractions()
        }
    }

    @Test
    fun `toplist is redacted to gid ids when mask flag on (no parsed titles out)`() {
        val serverConfig = mock(ServerConfigService::class.java)
        `when`(serverConfig.getBoolean(ServerConfigService.KEY_PRIVACY_MASK)).thenReturn(true)
        val (service, _, _, _) = harness(serverConfig)
        // 生产解析器形态：gid/token/tag 均为 null，value = 锚文本（完整标题），
        // href = 站点地址（含 token）。
        val raw = TopListItem().apply {
            this.gid = null
            this.value = "Real Explicit Title"
            this.href = "https://e-hentai.org/g/1382450/tok/"
        }
        val detail = detailWith(listOf(arrayOf(raw), arrayOfNulls(0), arrayOfNulls(0), arrayOfNulls(0)))
        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<SiteTopListDetail> {
                SiteEngine.getTopList(any(), any(), any())
            }.thenReturn(detail)

            val response = service.topListFeed()

            assertTrue(response.success)
            assertEquals("#1382450", response.data[0].value)
            assertEquals("1382450", response.data[0].gid)
            assertEquals("", response.data[0].href)
        }
    }

    @Test
    fun `toplist keeps raw values when mask flag off`() {
        val serverConfig = mock(ServerConfigService::class.java)
        `when`(serverConfig.getBoolean(ServerConfigService.KEY_PRIVACY_MASK)).thenReturn(false)
        val (service, _, _, _) = harness(serverConfig)
        val raw = TopListItem().apply {
            this.gid = null
            this.value = "Real Explicit Title"
            this.href = "https://e-hentai.org/g/1382450/tok/"
        }
        val detail = detailWith(listOf(arrayOf(raw), arrayOfNulls(0), arrayOfNulls(0), arrayOfNulls(0)))
        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<SiteTopListDetail> {
                SiteEngine.getTopList(any(), any(), any())
            }.thenReturn(detail)

            val response = service.topListFeed()

            assertEquals("Real Explicit Title", response.data[0].value)
            assertEquals("https://e-hentai.org/g/1382450/tok/", response.data[0].href)
        }
    }

    @Test
    fun `topListFeed returns success=false and cause EH_UNAVAILABLE while blocked`() {
        val (service, _, availability) = harness()
        availability.recordFailure("connect timed out")

        mockStatic(SiteEngine::class.java).use { engine ->
            val response = service.topListFeed()

            assertFalse(response.success)
            assertTrue(response.data.isEmpty())
            assertEquals("EH_UNAVAILABLE", response.cause)
            engine.verifyNoInteractions()
        }
    }
}
