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

        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<GalleryDetail> {
                SiteEngine.getGalleryDetail(any(), any(), anyString())
            }.thenThrow(RuntimeException("site still broken"))

            val detail = h.service.getGalleryDetail(GID, TOKEN)

            assertNotNull(detail)
            assertEquals("History title", detail!!.title)
            engine.verify {
                SiteEngine.getGalleryDetail(any(), any(), anyString())
            }
        }
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
}
