package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.web.repository.DownloadInfoRepository
import com.hippo.ehviewer.web.repository.HistoryInfoRepository
import com.hippo.ehviewer.web.repository.LocalFavoriteInfoRepository
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service

/**
 * Shared gateway for resolving E-Hentai gallery metadata through the single
 * [EhSessionManager] client: token lookup (downloads → history → favorites),
 * gallery page counts (via `EhEngine.getGalleryDetail`) and per-page image
 * URLs (via `EhEngine.getGalleryPage`, EH page numbers are 1-based).
 *
 * Used by the image streaming endpoint, the processing pipeline and the
 * torrent/archive services so every upstream request shares one cookie jar
 * and one browser fingerprint.
 */
@Service
class GalleryLookupService(
    private val downloadRepository: DownloadInfoRepository,
    private val historyRepository: HistoryInfoRepository,
    private val favoriteRepository: LocalFavoriteInfoRepository,
    private val sessionManager: EhSessionManager,
) {
    private val client get() = sessionManager.okHttpClient

    @PostConstruct
    fun init() {
        // EhEngine.getGalleryList applies the shared filter; make sure the
        // filter singleton exists before any list call (web process never
        // goes through the Android initializer).
        EhEngine.initialize()
    }

    /**
     * Resolve the gallery token from locally stored rows. Returns null when
     * the server has no record of the gallery (no download/history/favorite).
     */
    fun findToken(gid: Long): String? =
        downloadRepository.findByGid(gid)?.token
            ?: historyRepository.findByGid(gid)?.token
            ?: favoriteRepository.findByGid(gid)?.token

    /**
     * Fetch the total page count from the E-Hentai gallery detail page.
     * Throws when the upstream request or parse fails.
     */
    fun fetchPageCount(gid: Long, token: String): Int {
        val url = EhUrl.getGalleryDetailUrl(gid, token)
        val detail = EhEngine.getGalleryDetail(null, client, url)
        return detail.pages
    }

    /**
     * Resolve the page count for a gallery known to the server
     * (token from local rows). Returns null when the gallery is unknown or
     * the count cannot be fetched.
     */
    fun resolvePageCount(gid: Long): Int? {
        val token = findToken(gid) ?: return null
        return try {
            fetchPageCount(gid, token).takeIf { it > 0 }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolve the original image URL for a gallery page.
     *
     * @param page 1-based E-Hentai page number
     */
    fun fetchImageUrl(gid: Long, token: String, page: Int): String {
        val pageUrl = EhUrl.getHost() + "s/" + token + "/" + gid + "-" + page
        val result = EhEngine.getGalleryPage(null, client, pageUrl, gid, token)
        return result.imageUrl
    }
}
