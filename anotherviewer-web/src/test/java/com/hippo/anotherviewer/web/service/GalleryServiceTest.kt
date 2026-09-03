package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.client.SiteEngine
import com.hippo.anotherviewer.client.data.GalleryDetail
import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.entity.DownloadInfoEntity
import com.hippo.anotherviewer.web.entity.GalleryTagsEntity
import com.hippo.anotherviewer.web.entity.HistoryInfoEntity
import com.hippo.anotherviewer.web.entity.LocalFavoriteInfoEntity
import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import com.hippo.anotherviewer.web.repository.GalleryTagsRepository
import com.hippo.anotherviewer.web.repository.HistoryInfoRepository
import com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository
import com.hippo.anotherviewer.web.repository.QuickSearchRepository
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.verify

/**
 * Pins the EH-DOWN short-circuit of [GalleryService] (plan-2026-08-30 §3.2)
 * and the corrected getGalleryDetail source order (§3.4.0 P-C):
 * download → history → upstream (only when reachable) → favorite → null.
 */
class GalleryServiceTest {

    private val GID = 12345L
    private val TOKEN = "0123456789abcdef"

    private data class Harness(
        val service: GalleryService,
        val availability: EhAvailabilityService,
        val downloads: DownloadInfoRepository,
        val history: HistoryInfoRepository,
        val historyTags: GalleryTagsRepository,
        val favorites: LocalFavoriteInfoRepository,
        val galleryLookup: GalleryLookupService,
    )

    private fun harness(): Harness {
        val sessionManager = mock(SiteSessionManager::class.java)
        `when`(sessionManager.okHttpClient).thenReturn(OkHttpClient())
        val downloads = mock(DownloadInfoRepository::class.java)
        val history = mock(HistoryInfoRepository::class.java)
        val historyTags = mock(GalleryTagsRepository::class.java)
        val favorites = mock(LocalFavoriteInfoRepository::class.java)
        val galleryLookup = mock(GalleryLookupService::class.java)
        val availability = EhAvailabilityService(mock(com.hippo.anotherviewer.web.service.WebProxyManager::class.java), "https://e-hentai.org", 5000)
        val service = GalleryService(
            history,
            mock(QuickSearchRepository::class.java),
            historyTags,
            favorites,
            sessionManager,
            downloads,
            SiteCoreConfigProperties(),
            galleryLookup,
            availability,
            mock(DownloadDirIndex::class.java),
        )
        return Harness(service, availability, downloads, history, historyTags, favorites, galleryLookup)
    }

    private fun downloadRow(): DownloadInfoEntity = DownloadInfoEntity().apply {
        gid = GID
        token = TOKEN
        title = "Download title"
        thumb = "https://ehgt.org/download-thumb.jpg"
        total = 10
    }

    private fun historyRow(): HistoryInfoEntity = HistoryInfoEntity().apply {
        gid = GID
        token = TOKEN
        title = "History title"
        thumb = "https://ehgt.org/history-thumb.jpg"
        pages = 5
    }

    private fun favoriteRow(): LocalFavoriteInfoEntity = LocalFavoriteInfoEntity().apply {
        gid = GID
        token = TOKEN
        title = "Favorite title"
        thumb = "https://ehgt.org/favorite-thumb.jpg"
    }

    private fun stubNoLocalRows(h: Harness) {
        `when`(h.downloads.findByGid(GID)).thenReturn(null)
        `when`(h.history.findByGid(GID)).thenReturn(null)
        `when`(h.favorites.findByGid(GID)).thenReturn(null)
    }

    // ── getGalleryDetail source order (P-C) ────────────────────

    @Test
    fun `detail prefers the download row and never touches the site`() {
        val h = harness()
        `when`(h.downloads.findByGid(GID)).thenReturn(downloadRow())

        mockStatic(SiteEngine::class.java).use { engine ->
            val detail = h.service.getGalleryDetail(GID, TOKEN)

            assertNotNull(detail)
            assertEquals("Download title", detail!!.title)
            assertEquals(10, detail.pages)
            engine.verifyNoInteractions()
        }
    }

    @Test
    fun `download detail falls back to upstream page count when total and pushed files are zero`() {
        // 2026-08-30 导入 .db：download.total=0 且 downloads/ 目录为空 —— 页数将
        // 经 galleryLookup.resolvePageCount（detailCache+上游）救回，而非 0 页
        // 触发阅读器「只读第一页」。
        val h = harness()
        val row = downloadRow().apply { total = 0 }
        `when`(h.downloads.findByGid(GID)).thenReturn(row)
        `when`(h.galleryLookup.resolvePageCount(GID)).thenReturn(47)

        val detail = h.service.getGalleryDetail(GID, TOKEN)

        assertNotNull(detail)
        assertEquals(47, detail!!.pages)
        assertEquals("Download title", detail.title)
    }

    @Test
    fun `download detail stays open with zero pages when upstream cannot resolve either`() {
        val h = harness()
        val row = downloadRow().apply { total = 348 }
        `when`(h.downloads.findByGid(GID)).thenReturn(row)
        `when`(h.galleryLookup.resolvePageCount(GID)).thenReturn(348)

        val detail = h.service.getGalleryDetail(GID, TOKEN)

        assertNotNull(detail)
        assertEquals(348, detail!!.pages)
    }

    @Test
    fun `detail with a history row is served locally while blocked without upstream`() {
        val h = harness()
        `when`(h.downloads.findByGid(GID)).thenReturn(null)
        `when`(h.history.findByGid(GID)).thenReturn(historyRow())
        h.availability.recordFailure("connect timed out")

        mockStatic(SiteEngine::class.java).use { engine ->
            val detail = h.service.getGalleryDetail(GID, TOKEN)

            assertNotNull(detail)
            assertEquals("History title", detail!!.title)
            assertEquals(5, detail.pages)
            engine.verifyNoInteractions()
        }
    }

    @Test
    fun `history enrichment is attempted while reachable and falls back on failure`() {
        val h = harness()
        `when`(h.downloads.findByGid(GID)).thenReturn(null)
        `when`(h.history.findByGid(GID)).thenReturn(historyRow())
        `when`(h.historyTags.findByGid(GID)).thenReturn(emptyList<GalleryTagsEntity>())
        // P1: 补强改走 GalleryLookupService.getDetailCached（内部 detailCache）；
        // 上游失败 → null → 本地 DTO 原样返回（E2E-6 语义不变）。
        `when`(h.galleryLookup.getDetailCached(GID, TOKEN)).thenThrow(RuntimeException("site still broken"))

        val detail = h.service.getGalleryDetail(GID, TOKEN)

        assertNotNull(detail)
        assertEquals("History title", detail!!.title)
        verify(h.galleryLookup).getDetailCached(GID, TOKEN)
    }

    @Test
    fun `history enrichment uses the cached detail with comments`() {
        val h = harness()
        `when`(h.downloads.findByGid(GID)).thenReturn(null)
        `when`(h.history.findByGid(GID)).thenReturn(historyRow())
        `when`(h.historyTags.findByGid(GID)).thenReturn(emptyList<GalleryTagsEntity>())
        // P1: 二次点击零上游——detail 由 getDetailCached 提供（含站点真实评论）。
        `when`(h.galleryLookup.getDetailCached(GID, TOKEN)).thenReturn(GalleryDetail().apply {
            token = TOKEN
            title = "Site title"
            pages = 42
            comments = com.hippo.anotherviewer.client.data.GalleryCommentList(
                arrayOf(
                    com.hippo.anotherviewer.client.data.GalleryComment().apply {
                        id = 1L
                        user = "uploader"
                        comment = "nice"
                        time = 0L
                    }
                ),
                false
            )
        })

        val detail = h.service.getGalleryDetail(GID, TOKEN)

        assertNotNull(detail)
        assertEquals("History title", detail!!.title) // 本地标题优先，站点仅补缺
        assertEquals(5, detail.pages)
        assertEquals(1, detail.comments.size)
        verify(h.galleryLookup).getDetailCached(GID, TOKEN)
    }

    @Test
    fun `detail with a token fetches upstream and writes the history row`() {
        val h = harness()
        stubNoLocalRows(h)
        `when`(h.history.save(any(HistoryInfoEntity::class.java))).thenAnswer { it.getArgument(0) }

        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<GalleryDetail> {
                SiteEngine.getGalleryDetail(any(), any(), anyString())
            }.thenReturn(GalleryDetail().apply {
                token = TOKEN
                title = "Site title"
                pages = 42
            })

            val detail = h.service.getGalleryDetail(GID, TOKEN)

            assertNotNull(detail)
            assertEquals("Site title", detail!!.title)
            assertEquals(42, detail.pages)
            verify(h.history).save(any(HistoryInfoEntity::class.java))
        }
    }

    @Test
    fun `detail with a token is skipped while blocked and falls through to the favorite row`() {
        val h = harness()
        `when`(h.downloads.findByGid(GID)).thenReturn(null)
        `when`(h.history.findByGid(GID)).thenReturn(null)
        `when`(h.favorites.findByGid(GID)).thenReturn(favoriteRow())
        `when`(h.galleryLookup.resolvePageCount(GID)).thenReturn(null)
        h.availability.recordFailure("connect timed out")

        mockStatic(SiteEngine::class.java).use { engine ->
            val detail = h.service.getGalleryDetail(GID, TOKEN)

            assertNotNull(detail)
            assertEquals("Favorite title", detail!!.title)
            assertEquals("https://ehgt.org/favorite-thumb.jpg", detail.thumb)
            engine.verifyNoInteractions()
        }
    }

    @Test
    fun `detail with a token while reachable wins over the favorite row`() {
        val h = harness()
        `when`(h.downloads.findByGid(GID)).thenReturn(null)
        `when`(h.history.findByGid(GID)).thenReturn(null)
        `when`(h.favorites.findByGid(GID)).thenReturn(favoriteRow())

        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<GalleryDetail> {
                SiteEngine.getGalleryDetail(any(), any(), anyString())
            }.thenReturn(GalleryDetail().apply {
                token = TOKEN
                title = "Site title"
                pages = 42
            })

            val detail = h.service.getGalleryDetail(GID, TOKEN)

            // 顺序 3 在 4 之前：可达 + token → 站点直取（收藏分支不触发）。
            assertEquals("Site title", detail!!.title)
            assertEquals(42, detail.pages)
        }
    }

    @Test
    fun `favorite detail pageCount comes from resolvePageCount when reachable`() {
        val h = harness()
        `when`(h.downloads.findByGid(GID)).thenReturn(null)
        `when`(h.history.findByGid(GID)).thenReturn(null)
        `when`(h.favorites.findByGid(GID)).thenReturn(favoriteRow())
        `when`(h.galleryLookup.resolvePageCount(GID)).thenReturn(33)

        val detail = h.service.getGalleryDetail(GID, null)

        assertNotNull(detail)
        assertEquals(33, detail!!.pages)
    }

    @Test
    fun `detail returns null when no local source and no token`() {
        val h = harness()
        stubNoLocalRows(h)

        assertNull(h.service.getGalleryDetail(GID, null))
    }

    // ── blocked search short circuit (success=false + cause) ──

    @Test
    fun `searchGallery short-circuits while blocked with cause EH_UNAVAILABLE`() {
        val h = harness()
        h.availability.recordFailure("connect timed out")

        mockStatic(SiteEngine::class.java).use { engine ->
            val response = h.service.searchGallery("alpha", null, 0, 20)

            assertFalse(response.success)
            assertTrue(response.data.isEmpty())
            assertEquals(0, response.total)
            assertEquals("EH_UNAVAILABLE", response.cause)
            engine.verifyNoInteractions()
        }
    }

    // ── S5: detail 四路构建器 readProgress ──────────────────────

    @Test
    fun `download detail carries the stored read progress`() {
        val h = harness()
        `when`(h.downloads.findByGid(GID)).thenReturn(downloadRow())
        `when`(h.history.findByGid(GID)).thenReturn(historyRow().apply { page = 21 })

        val detail = h.service.getGalleryDetail(GID, TOKEN)

        assertNotNull(detail)
        assertEquals(21, detail!!.readProgress)
    }

    @Test
    fun `history detail carries the row page without a second lookup`() {
        val h = harness()
        `when`(h.downloads.findByGid(GID)).thenReturn(null)
        `when`(h.history.findByGid(GID)).thenReturn(historyRow().apply { page = 13 })
        `when`(h.historyTags.findByGid(GID)).thenReturn(emptyList<GalleryTagsEntity>())
        h.availability.recordFailure("connect timed out") // blocked → 纯本地 DTO

        val detail = h.service.getGalleryDetail(GID, TOKEN)

        assertNotNull(detail)
        assertEquals(13, detail!!.readProgress)
    }

    @Test
    fun `favorite detail reports zero progress when no history row exists`() {
        val h = harness()
        `when`(h.downloads.findByGid(GID)).thenReturn(null)
        `when`(h.history.findByGid(GID)).thenReturn(null)
        `when`(h.favorites.findByGid(GID)).thenReturn(favoriteRow())
        `when`(h.galleryLookup.resolvePageCount(GID)).thenReturn(null)
        h.availability.recordFailure("connect timed out")

        val detail = h.service.getGalleryDetail(GID, TOKEN)

        assertNotNull(detail)
        assertEquals("Favorite title", detail!!.title)
        // 收藏分支仅在无历史行时走到（历史分支优先），readProgressOf = 0。
        assertEquals(0, detail.readProgress)
    }

    @Test
    fun `upstream detail path leaves readProgress null (no history row existed)`() {
        val h = harness()
        stubNoLocalRows(h)

        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<GalleryDetail> {
                SiteEngine.getGalleryDetail(any(), any(), anyString())
            }.thenReturn(GalleryDetail().apply {
                token = TOKEN
                title = "Site title"
                pages = 42
            })

            val detail = h.service.getGalleryDetail(GID, TOKEN)

            // S5⑤: 上游拉取路径必无历史行（进度恒 0），DTO 缺省 null 即可。
            assertNotNull(detail)
            assertEquals("Site title", detail!!.title)
            assertNull(detail.readProgress)
        }
    }

    @Test
    fun `history detail keeps readProgress through upstream enrichment`() {
        val h = harness()
        `when`(h.downloads.findByGid(GID)).thenReturn(null)
        `when`(h.history.findByGid(GID)).thenReturn(historyRow().apply { page = 13; pages = 0 })
        `when`(h.historyTags.findByGid(GID)).thenReturn(emptyList<GalleryTagsEntity>())
        `when`(h.galleryLookup.getDetailCached(GID, TOKEN)).thenReturn(GalleryDetail().apply {
            token = TOKEN
            title = "Site title"
            pages = 42
        })

        val detail = h.service.getGalleryDetail(GID, TOKEN)

        assertNotNull(detail)
        assertEquals("History title", detail!!.title) // 本地标题优先
        assertEquals(42, detail.pages) // 本地 pages=0 → 站点补缺
        assertEquals(13, detail.readProgress) // enrichment copy() 不得丢进度
    }

    // ── S5⑥⑦: 列表 readProgress 填充 ───────────────────────────

    @Test
    fun `getHistory fills readProgress from the rows`() {
        val h = harness()
        val rows = listOf(historyRow().apply { gid = 1; page = 3 }, historyRow().apply { gid = 2; page = 0 })
        `when`(h.history.findAllByOrderByTimeDesc()).thenReturn(rows)

        val response = h.service.getHistory(0, 20)

        assertTrue(response.success)
        assertEquals(listOf(3, 0), response.data.map { it.readProgress })
    }

    @Test
    fun `getLocalFavorites fills readProgress in one batched query`() {
        val h = harness()
        val fav1 = favoriteRow().apply { gid = 1 }
        val fav2 = favoriteRow().apply { gid = 2 }
        `when`(h.favorites.findAllByOrderByTimeDesc()).thenReturn(listOf(fav1, fav2))
        `when`(h.history.findByGidIn(listOf(1L, 2L))).thenReturn(
            listOf(historyRow().apply { gid = 1; page = 8 })
        )

        val response = h.service.getLocalFavorites()

        assertTrue(response.success)
        val byGid = response.data.associateBy { it.gid }
        assertEquals(8, byGid[1L]!!.readProgress)
        // 无历史行 → 0（未读），与详情路径 readProgressOf 语义一致。
        assertEquals(0, byGid[2L]!!.readProgress)
        verify(h.history).findByGidIn(listOf(1L, 2L)) // 单次批量，非逐行 N+1
    }

    // ── P2: toplist / search 站点结果缓存 ───────────────────────

    @Test
    fun `second toplist call within ttl does not touch the site`() {
        val h = harness()
        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<com.hippo.anotherviewer.client.data.SiteTopListDetail> {
                SiteEngine.getTopList(any(), any(), any())
            }.thenReturn(com.hippo.anotherviewer.client.data.SiteTopListDetail().apply {
                galleryTopListInfo = com.hippo.anotherviewer.client.data.topList.TopListInfo().apply {
                    yesterdayTopList = com.hippo.anotherviewer.client.data.topList.TopListItemArray().apply {
                        itemArray = arrayOf(
                            com.hippo.anotherviewer.client.data.topList.TopListItem().apply {
                                gid = "1"; token = "t1"; value = "Alpha"
                            }
                        )
                    }
                }
            })

            val first = h.service.topListFeed()
            val second = h.service.topListFeed()

            assertTrue(first.success)
            assertTrue(second.success)
            assertEquals(first.data, second.data)
            // 只打了一次上游（P2: 5min TTL 缓存命中）。
            engine.verify { SiteEngine.getTopList(any(), any(), any()) }
        }
    }

    @Test
    fun `toplist cache is still served while EH is blocked`() {
        val h = harness()
        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<com.hippo.anotherviewer.client.data.SiteTopListDetail> {
                SiteEngine.getTopList(any(), any(), any())
            }.thenReturn(com.hippo.anotherviewer.client.data.SiteTopListDetail().apply {
                galleryTopListInfo = com.hippo.anotherviewer.client.data.topList.TopListInfo().apply {
                    yesterdayTopList = com.hippo.anotherviewer.client.data.topList.TopListItemArray().apply {
                        itemArray = arrayOf(
                            com.hippo.anotherviewer.client.data.topList.TopListItem().apply {
                                gid = "1"; token = "t1"; value = "Alpha"
                            }
                        )
                    }
                }
            })

            assertTrue(h.service.topListFeed().success)
            h.availability.recordFailure("connect timed out")

            // P2: 缓存查询在 isBlocked() 之前——DOWN 期间命中缓存照常返回陈旧内容。
            val stale = h.service.topListFeed()
            assertTrue(stale.success)
            assertEquals(1, stale.total)
        }
    }

    @Test
    fun `toplist empty result is not cached`() {
        val h = harness()
        val upstreamCalls = java.util.concurrent.atomic.AtomicInteger(0)
        mockStatic(SiteEngine::class.java).use { engine ->
            // 第一次：上游可达但空 → success=true 且非空才缓存，空结果不落缓存。
            engine.`when`<com.hippo.anotherviewer.client.data.SiteTopListDetail> {
                SiteEngine.getTopList(any(), any(), any())
            }.thenAnswer {
                upstreamCalls.incrementAndGet()
                com.hippo.anotherviewer.client.data.SiteTopListDetail()
            }

            assertTrue(h.service.topListFeed().success)
            assertTrue(h.service.topListFeed().data.isEmpty())
            // 两次都打到了上游（若空结果被缓存则第二次不再触网）。
            assertEquals(2, upstreamCalls.get())
        }
    }

    @Test
    fun `second identical search does not touch the site within ttl`() {
        val h = harness()
        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<com.hippo.anotherviewer.client.parser.GalleryListParser.Result> {
                SiteEngine.getGalleryList(any(), any(), anyString(), anyInt())
            }.thenReturn(com.hippo.anotherviewer.client.parser.GalleryListParser.Result().apply {
                pages = 1
                galleryInfoList = listOf(
                    com.hippo.anotherviewer.client.data.GalleryInfo().apply {
                        gid = 5L; token = "t5"; title = "Result"
                    }
                )
            })

            val first = h.service.searchGallery("alpha", null, 0, 20)
            val second = h.service.searchGallery("alpha", null, 0, 20)

            assertTrue(first.success)
            assertTrue(second.success)
            assertEquals(first.data, second.data)
            engine.verify { SiteEngine.getGalleryList(any(), any(), anyString(), anyInt()) }
        }
    }

    @Test
    fun `search cache is still served while EH is blocked`() {
        val h = harness()
        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<com.hippo.anotherviewer.client.parser.GalleryListParser.Result> {
                SiteEngine.getGalleryList(any(), any(), anyString(), anyInt())
            }.thenReturn(com.hippo.anotherviewer.client.parser.GalleryListParser.Result().apply {
                pages = 1
                galleryInfoList = listOf(
                    com.hippo.anotherviewer.client.data.GalleryInfo().apply {
                        gid = 5L; token = "t5"; title = "Result"
                    }
                )
            })

            val first = h.service.searchGallery("alpha", null, 0, 20)
            assertTrue(first.success)
            h.availability.recordFailure("connect timed out")

            // P2: DOWN 期间同 URL 命中缓存，返回陈旧成功结果而非 EH_UNAVAILABLE。
            val stale = h.service.searchGallery("alpha", null, 0, 20)
            assertTrue(stale.success)
            assertEquals(first.data, stale.data)
        }
    }

    @Test
    fun `search empty result is not cached`() {
        val h = harness()
        val upstreamCalls = java.util.concurrent.atomic.AtomicInteger(0)
        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<com.hippo.anotherviewer.client.parser.GalleryListParser.Result> {
                SiteEngine.getGalleryList(any(), any(), anyString(), anyInt())
            }.thenAnswer {
                upstreamCalls.incrementAndGet()
                com.hippo.anotherviewer.client.parser.GalleryListParser.Result()
            }

            val first = h.service.searchGallery("alpha", null, 0, 20)
            val second = h.service.searchGallery("alpha", null, 0, 20)

            assertTrue(first.success)
            assertTrue(first.data.isEmpty())
            assertTrue(second.success)
            assertTrue(second.data.isEmpty())
            // 空结果两次都触网（未缓存）。
            assertEquals(2, upstreamCalls.get())
        }
    }
}
