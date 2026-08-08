package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.client.SiteEngine
import com.hippo.anotherviewer.client.SiteUrl
import com.hippo.anotherviewer.client.data.GalleryDetail
import com.hippo.anotherviewer.client.data.NormalPreviewSet
import com.hippo.anotherviewer.client.exception.SiteException
import com.hippo.anotherviewer.client.parser.GalleryPageParser
import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.entity.DownloadInfoEntity
import com.hippo.anotherviewer.web.entity.HistoryInfoEntity
import com.hippo.anotherviewer.web.entity.LocalFavoriteInfoEntity
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import com.hippo.anotherviewer.web.repository.HistoryInfoRepository
import com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pins [GalleryLookupService]: token resolution order (downloads → history →
 * favorites), the gid-keyed detail cache, and the EH per-page token fix where
 * `fetchImageUrl` must drive `SiteEngine.getGalleryPage` with the preview
 * page's own `/s/` URL instead of the gallery detail token.
 */
class GalleryLookupServiceTest {

    private val GID = 12345L
    private val DETAIL_TOKEN = "detailToken"
    private val PAGE_URL_A = "https://exhentai.org/s/pageTokenA/12345-1"
    private val PAGE_URL_B = "https://exhentai.org/s/pageTokenB/12345-2"
    private val IMAGE_URL = "https://ehgt.org/w/x.jpg"

    private data class Harness(
        val service: GalleryLookupService,
        val client: OkHttpClient,
        val downloads: DownloadInfoRepository,
        val history: HistoryInfoRepository,
        val favorites: LocalFavoriteInfoRepository,
    )

    private fun harness(): Harness {
        val sessionManager = mock(SiteSessionManager::class.java)
        val client = OkHttpClient()
        `when`(sessionManager.okHttpClient).thenReturn(client)
        val downloads = mock(DownloadInfoRepository::class.java)
        val history = mock(HistoryInfoRepository::class.java)
        val favorites = mock(LocalFavoriteInfoRepository::class.java)
        return Harness(
            GalleryLookupService(downloads, history, favorites, sessionManager),
            client, downloads, history, favorites
        )
    }

    /** Two-page gallery detail: each page carries its own `/s/` page token. */
    private fun twoPageDetail(): GalleryDetail {
        val previews = NormalPreviewSet().apply {
            addItem(1, "https://ehgt.org/thumbs/1.jpg", 0, 0, 250, 350, PAGE_URL_A)
            addItem(2, "https://ehgt.org/thumbs/2.jpg", 0, 0, 250, 350, PAGE_URL_B)
        }
        return GalleryDetail().apply {
            pages = 2
            previewSet = previews
        }
    }

    private fun pageResult(): GalleryPageParser.Result =
        GalleryPageParser.Result().apply { imageUrl = IMAGE_URL }

    // ------------------------------------------------------------------
    // fetchImageUrl: per-page /s/ URL carries its own token
    // ------------------------------------------------------------------

    @Test
    fun `fetchImageUrl uses the preview page url with its own token, not the detail token`() {
        val (service, client) = harness()
        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<GalleryDetail> {
                SiteEngine.getGalleryDetail(any(), any(), anyString())
            }.thenReturn(twoPageDetail())
            engine.`when`<GalleryPageParser.Result> {
                SiteEngine.getGalleryPage(any(), any(), anyString(), anyLong(), anyString())
            }.thenReturn(pageResult())

            assertEquals(IMAGE_URL, service.fetchImageUrl(GID, DETAIL_TOKEN, 1))
            assertEquals(IMAGE_URL, service.fetchImageUrl(GID, DETAIL_TOKEN, 2))

            // getGalleryPage must receive each page's own /s/ URL (pageTokenA/B),
            // never a URL derived from the gallery detail token.
            engine.verify {
                SiteEngine.getGalleryDetail(null, client, SiteUrl.getGalleryDetailUrl(GID, DETAIL_TOKEN))
                SiteEngine.getGalleryPage(null, client, PAGE_URL_A, GID, DETAIL_TOKEN)
                SiteEngine.getGalleryPage(null, client, PAGE_URL_B, GID, DETAIL_TOKEN)
            }
        }
    }

    @Test
    fun `fetchImageUrl throws SiteException for out of range page`() {
        val (service, _) = harness()
        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<GalleryDetail> {
                SiteEngine.getGalleryDetail(any(), any(), anyString())
            }.thenReturn(twoPageDetail())

            assertThrows(SiteException::class.java) { service.fetchImageUrl(GID, DETAIL_TOKEN, 0) }
            assertThrows(SiteException::class.java) { service.fetchImageUrl(GID, DETAIL_TOKEN, 3) }
        }
    }

    // ------------------------------------------------------------------
    // detail cache (gid keyed, shared by fetchImageUrl and fetchPageCount)
    // ------------------------------------------------------------------

    @Test
    fun `detail page is fetched once across two fetchImageUrl calls`() {
        val (service, _) = harness()
        val detailCalls = AtomicInteger(0)
        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<GalleryDetail> {
                SiteEngine.getGalleryDetail(any(), any(), anyString())
            }.thenAnswer {
                detailCalls.incrementAndGet()
                twoPageDetail()
            }
            engine.`when`<GalleryPageParser.Result> {
                SiteEngine.getGalleryPage(any(), any(), anyString(), anyLong(), anyString())
            }.thenReturn(pageResult())

            service.fetchImageUrl(GID, DETAIL_TOKEN, 1)
            service.fetchImageUrl(GID, DETAIL_TOKEN, 2)

            assertEquals(1, detailCalls.get())
        }
    }

    @Test
    fun `fetchPageCount and fetchImageUrl share the detail cache`() {
        val (service, _) = harness()
        val detailCalls = AtomicInteger(0)
        mockStatic(SiteEngine::class.java).use { engine ->
            engine.`when`<GalleryDetail> {
                SiteEngine.getGalleryDetail(any(), any(), anyString())
            }.thenAnswer {
                detailCalls.incrementAndGet()
                twoPageDetail()
            }
            engine.`when`<GalleryPageParser.Result> {
                SiteEngine.getGalleryPage(any(), any(), anyString(), anyLong(), anyString())
            }.thenReturn(pageResult())

            assertEquals(2, service.fetchPageCount(GID, DETAIL_TOKEN))
            assertEquals(IMAGE_URL, service.fetchImageUrl(GID, DETAIL_TOKEN, 1))

            assertEquals(1, detailCalls.get())
        }
    }

    // ------------------------------------------------------------------
    // findToken / resolvePageCount (local rows only, no site traffic)
    // ------------------------------------------------------------------

    @Test
    fun `findToken prefers download then history then favorite`() {
        val h = harness()
        val service = h.service
        val downloads = h.downloads
        val history = h.history
        val favorites = h.favorites

        val download = DownloadInfoEntity().apply { token = "download-token" }
        val historyRow = HistoryInfoEntity().apply { token = "history-token" }
        val favorite = LocalFavoriteInfoEntity().apply { token = "favorite-token" }

        // download only
        `when`(downloads.findByGid(GID)).thenReturn(download)
        `when`(history.findByGid(GID)).thenReturn(null)
        `when`(favorites.findByGid(GID)).thenReturn(null)
        assertEquals("download-token", service.findToken(GID))

        // download missing → history
        `when`(downloads.findByGid(GID)).thenReturn(null)
        `when`(history.findByGid(GID)).thenReturn(historyRow)
        assertEquals("history-token", service.findToken(GID))

        // download + history missing → favorite
        `when`(downloads.findByGid(GID)).thenReturn(null)
        `when`(history.findByGid(GID)).thenReturn(null)
        `when`(favorites.findByGid(GID)).thenReturn(favorite)
        assertEquals("favorite-token", service.findToken(GID))

        // all present → download still wins
        `when`(downloads.findByGid(GID)).thenReturn(download)
        `when`(history.findByGid(GID)).thenReturn(historyRow)
        `when`(favorites.findByGid(GID)).thenReturn(favorite)
        assertEquals("download-token", service.findToken(GID))

        // none present → null
        `when`(downloads.findByGid(GID)).thenReturn(null)
        `when`(history.findByGid(GID)).thenReturn(null)
        `when`(favorites.findByGid(GID)).thenReturn(null)
        assertNull(service.findToken(GID))
    }

    @Test
    fun `resolvePageCount returns null when no token is known`() {
        val h = harness()
        `when`(h.downloads.findByGid(GID)).thenReturn(null)
        `when`(h.history.findByGid(GID)).thenReturn(null)
        `when`(h.favorites.findByGid(GID)).thenReturn(null)

        assertNull(h.service.resolvePageCount(GID))
    }
}
