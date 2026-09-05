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
import org.mockito.Mockito.never
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

    private fun harness(realLookup: Boolean = false): Harness {
        val sessionManager = mock(SiteSessionManager::class.java)
        `when`(sessionManager.okHttpClient).thenReturn(OkHttpClient())
        val downloads = mock(DownloadInfoRepository::class.java)
        val history = mock(HistoryInfoRepository::class.java)
        val historyTags = mock(GalleryTagsRepository::class.java)
        val favorites = mock(LocalFavoriteInfoRepository::class.java)
        val availability = EhAvailabilityService(mock(com.hippo.anotherviewer.web.service.WebProxyManager::class.java), "https://e-hentai.org", 5000)
        // P1: 验证直开路径的上游复用/缓存共享时需要真实 GalleryLookupService
        // （内部 detailCache 生效，与 GalleryService 共享同一批仓储 mock）；
        // 其余测试用 mock 隔离上游。
        val galleryLookup = if (realLookup) {
            GalleryLookupService(downloads, history, favorites, sessionManager, availability)
        } else {
            mock(GalleryLookupService::class.java)
        }
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
            mock(ServerConfigService::class.java),        )
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
        // P1: 直开改走 GalleryLookupService.getDetailCached（内部 detailCache）；
        // 服务本身不再直调 SiteEngine。
        `when`(h.galleryLookup.getDetailCached(GID, TOKEN)).thenReturn(GalleryDetail().apply {
            token = TOKEN
            title = "Site title"
            pages = 42
        })

        mockStatic(SiteEngine::class.java).use { engine ->
            val detail = h.service.getGalleryDetail(GID, TOKEN)

            assertNotNull(detail)
            assertEquals("Site title", detail!!.title)
            assertEquals(42, detail.pages)
            verify(h.history).save(any(HistoryInfoEntity::class.java))
            engine.verifyNoInteractions()
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
        // P1: 顺序 3 的上游直取改经 getDetailCached（mock 提供 detail）。
        `when`(h.galleryLookup.getDetailCached(GID, TOKEN)).thenReturn(GalleryDetail().apply {
            token = TOKEN
            title = "Site title"
            pages = 42
        })

        mockStatic(SiteEngine::class.java).use { engine ->
            val detail = h.service.getGalleryDetail(GID, TOKEN)

            // 顺序 3 在 4 之前：可达 + token → getDetailCached 非空（收藏分支不触发）。
            assertEquals("Site title", detail!!.title)
            assertEquals(42, detail.pages)
            engine.verifyNoInteractions()
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
        // P1: 上游拉取改经 getDetailCached（mock 提供 detail）。
        `when`(h.galleryLookup.getDetailCached(GID, TOKEN)).thenReturn(GalleryDetail().apply {
            token = TOKEN
            title = "Site title"
            pages = 42
        })

        mockStatic(SiteEngine::class.java).use { engine ->
            val detail = h.service.getGalleryDetail(GID, TOKEN)

            // S5⑤: 上游拉取路径必无历史行（进度恒 0），DTO 缺省 null 即可。
            assertNotNull(detail)
            assertEquals("Site title", detail!!.title)
            assertNull(detail.readProgress)
            engine.verifyNoInteractions()
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

    @Test
    fun `favorite add is visible in the detail favoriteSlot through the history branch`() {
        // 任务 D（收藏按钮只加不减）端到端：addFavorite 成功回写历史行后，
        // 重进详情走历史分支（enrichHistoryDetail:554 favoriteSlot=history.favoriteSlot），
        // 按钮呈已收藏态；enrichment copy() 不覆盖 favoriteSlot。
        val h = harness()
        val history = historyRow().apply { favoriteSlot = -2 }
        `when`(h.downloads.findByGid(GID)).thenReturn(null)
        `when`(h.history.findByGid(GID)).thenReturn(history)
        `when`(h.historyTags.findByGid(GID)).thenReturn(emptyList<GalleryTagsEntity>())
        `when`(h.favorites.findByGid(GID)).thenReturn(null)
        val favoriteService = FavoriteService(h.favorites, h.history, h.downloads, HistoryService(h.history))

        assertTrue(favoriteService.addFavorite(GID, TOKEN, "Favorite title", 512, slot = 3))

        // 详情历史分支读到的 favoriteSlot 与收藏行一致（≥0，非恒 -2）。
        val detail = h.service.getGalleryDetail(GID, TOKEN)
        assertNotNull(detail)
        assertEquals(3, detail!!.favoriteSlot)
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

    // ── P1: feed 站点结果缓存（2min，key = "$mode:$page:$pageSize"）──

    /** 单条结果的站点列表解析产物（feed 缓存测试共用）。 */
    private fun listResult(vararg gids: Long) =
        com.hippo.anotherviewer.client.parser.GalleryListParser.Result().apply {
            pages = 1
            galleryInfoList = gids.map { gid ->
                com.hippo.anotherviewer.client.data.GalleryInfo().apply {
                    this.gid = gid
                    token = "t$gid"
                    title = "Result $gid"
                }
            }
        }

    @Test
    fun `second feed call within ttl does not touch the site`() {
        val h = harness()
        val upstreamCalls = java.util.concurrent.atomic.AtomicInteger(0)
        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<com.hippo.anotherviewer.client.parser.GalleryListParser.Result> {
                SiteEngine.getGalleryList(any(), any(), anyString(), anyInt())
            }.thenAnswer {
                upstreamCalls.incrementAndGet()
                listResult(5L)
            }

            val first = h.service.feedGallery("popular", 0, 20)
            val second = h.service.feedGallery("popular", 0, 20)

            assertTrue(first.success)
            assertTrue(second.success)
            assertEquals(first.data, second.data)
            // P1: 2min TTL 缓存命中——两次 feed 只打一次上游。
            assertEquals(1, upstreamCalls.get())
        }
    }

    @Test
    fun `feed cache is still served while EH is blocked`() {
        val h = harness()
        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<com.hippo.anotherviewer.client.parser.GalleryListParser.Result> {
                SiteEngine.getGalleryList(any(), any(), anyString(), anyInt())
            }.thenReturn(listResult(5L))

            val first = h.service.feedGallery("subscription", 0, 20)
            assertTrue(first.success)
            h.availability.recordFailure("connect timed out")

            // P1: 缓存查询在 isBlocked() 之前——DOWN 期间命中缓存返回陈旧成功结果。
            val stale = h.service.feedGallery("subscription", 0, 20)
            assertTrue(stale.success)
            assertEquals(first.data, stale.data)
        }
    }

    @Test
    fun `feed returns the EH_UNAVAILABLE envelope on cache miss while blocked`() {
        val h = harness()
        h.availability.recordFailure("connect timed out")

        mockStatic(SiteEngine::class.java).use { engine ->
            // 缓存为空（miss）→ isBlocked() 兜底保留在 miss 路径：秒回 envelope，不触网。
            val response = h.service.feedGallery("subscription", 0, 20)

            assertFalse(response.success)
            assertTrue(response.data.isEmpty())
            assertEquals("EH_UNAVAILABLE", response.cause)
            engine.verifyNoInteractions()
        }
    }

    @Test
    fun `feed empty result is not cached`() {
        val h = harness()
        val upstreamCalls = java.util.concurrent.atomic.AtomicInteger(0)
        mockStatic(SiteEngine::class.java).use { engine ->
            // 上游可达但空 → success=true 且非空才缓存，空结果不落缓存。
            engine.`when`<com.hippo.anotherviewer.client.parser.GalleryListParser.Result> {
                SiteEngine.getGalleryList(any(), any(), anyString(), anyInt())
            }.thenAnswer {
                upstreamCalls.incrementAndGet()
                com.hippo.anotherviewer.client.parser.GalleryListParser.Result()
            }

            val first = h.service.feedGallery("popular", 0, 20)
            val second = h.service.feedGallery("popular", 0, 20)

            assertTrue(first.success)
            assertTrue(first.data.isEmpty())
            assertTrue(second.success)
            assertTrue(second.data.isEmpty())
            // 空结果两次都触网（未缓存）。
            assertEquals(2, upstreamCalls.get())
        }
    }

    @Test
    fun `feed failure result is not cached`() {
        val h = harness()
        val upstreamCalls = java.util.concurrent.atomic.AtomicInteger(0)
        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<com.hippo.anotherviewer.client.parser.GalleryListParser.Result> {
                SiteEngine.getGalleryList(any(), any(), anyString(), anyInt())
            }.thenAnswer {
                upstreamCalls.incrementAndGet()
                throw java.io.IOException("unreachable")
            }

            val first = h.service.feedGallery("popular", 0, 20)
            val second = h.service.feedGallery("popular", 0, 20)

            // 失败结果不落缓存：两次都触网且均按 E2E-6 语义 success=false。
            assertFalse(first.success)
            assertFalse(second.success)
            assertEquals(2, upstreamCalls.get())
        }
    }

    // ── P1: 详情直开走 detailCache（getDetailCached）────────────

    @Test
    fun `direct detail open hits upstream once across two calls`() {
        val h = harness(realLookup = true)
        stubNoLocalRows(h)
        val upstreamCalls = java.util.concurrent.atomic.AtomicInteger(0)
        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<GalleryDetail> {
                SiteEngine.getGalleryDetail(any(), any(), anyString())
            }.thenAnswer {
                upstreamCalls.incrementAndGet()
                GalleryDetail().apply {
                    token = TOKEN
                    title = "Site title"
                    pages = 42
                }
            }

            val first = h.service.getGalleryDetail(GID, TOKEN)
            val second = h.service.getGalleryDetail(GID, TOKEN)

            assertNotNull(first)
            assertNotNull(second)
            assertEquals("Site title", second!!.title)
            // P1: detailCache（gid 键 10min）命中——两次直开只打一次上游。
            assertEquals(1, upstreamCalls.get())
        }
    }

    @Test
    fun `direct open and history enrichment share the cached detail`() {
        val h = harness(realLookup = true)
        stubNoLocalRows(h)
        `when`(h.historyTags.findByGid(GID)).thenReturn(emptyList<GalleryTagsEntity>())
        val upstreamCalls = java.util.concurrent.atomic.AtomicInteger(0)
        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<GalleryDetail> {
                SiteEngine.getGalleryDetail(any(), any(), anyString())
            }.thenAnswer {
                upstreamCalls.incrementAndGet()
                GalleryDetail().apply {
                    token = TOKEN
                    title = "Site title"
                    pages = 42
                }
            }

            // 第一次：无本地行 → 直开路径拉上游并回填 detailCache。
            assertNotNull(h.service.getGalleryDetail(GID, TOKEN))
            assertEquals(1, upstreamCalls.get())

            // 第二次：出现历史行 → enrichHistoryDetail 走 getDetailCached，
            // 命中直开路径回填的同一缓存条目（同 gid 两路径只打一次上游）。
            `when`(h.history.findByGid(GID)).thenReturn(historyRow())
            val enriched = h.service.getGalleryDetail(GID, TOKEN)

            assertNotNull(enriched)
            assertEquals("History title", enriched!!.title) // 本地标题优先，站点仅补缺
            assertEquals(5, enriched.pages) // 本地 pages>0 不被站点值覆盖
            assertEquals(1, upstreamCalls.get())
        }
    }

    @Test
    fun `direct detail open returns null when the cached lookup fails`() {
        val h = harness()
        stubNoLocalRows(h)
        // getDetailCached 上游失败吞异常返回 null（mock 默认即 null）→ 直开按
        // 原语义返回 null，不落历史、不伪造本地数据。
        mockStatic(SiteEngine::class.java).use { engine ->
            val detail = h.service.getGalleryDetail(GID, TOKEN)

            assertNull(detail)
            verify(h.history, never()).save(any(HistoryInfoEntity::class.java))
            engine.verifyNoInteractions()
        }
    }

    @Test
    fun `detail with a blank token falls through to the favorite row`() {
        val h = harness()
        `when`(h.downloads.findByGid(GID)).thenReturn(null)
        `when`(h.history.findByGid(GID)).thenReturn(null)
        `when`(h.favorites.findByGid(GID)).thenReturn(favoriteRow())
        `when`(h.galleryLookup.resolvePageCount(GID)).thenReturn(null)

        val detail = h.service.getGalleryDetail(GID, "   ")

        // token 为空白同样跳过上游分支（isNullOrBlank），收藏行兜底不变。
        assertNotNull(detail)
        assertEquals("Favorite title", detail!!.title)
        verify(h.galleryLookup, never()).getDetailCached(anyLong(), anyString())
    }
}
