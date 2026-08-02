package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.client.SiteEngine
import com.hippo.anotherviewer.client.SiteUrl
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import com.hippo.anotherviewer.web.repository.HistoryInfoRepository
import com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service

/**
 * Shared gateway for resolving Gallery Site gallery metadata through the single
 * [SiteSessionManager] client: token lookup (downloads → history → favorites),
 * gallery page counts (via `SiteEngine.getGalleryDetail`) and per-page image
 * URLs (via `SiteEngine.getGalleryPage`, EH page numbers are 1-based).
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
    private val sessionManager: SiteSessionManager,
) {
    private val client get() = sessionManager.okHttpClient

    @PostConstruct
    fun init() {
        // SiteEngine.getGalleryList applies the shared filter; make sure the
        // filter singleton exists before any list call (web process never
        // goes through the Android initializer).
        SiteEngine.initialize()
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
     * Fetch the total page count from the Gallery Site gallery detail page.
     * Throws when the upstream request or parse fails.
     */
    fun fetchPageCount(gid: Long, token: String): Int {
        val url = SiteUrl.getGalleryDetailUrl(gid, token)
        val detail = SiteEngine.getGalleryDetail(null, client, url)
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
     * @param page 1-based Gallery Site page number
     */
    fun fetchImageUrl(gid: Long, token: String, page: Int): String {
        val pageUrl = SiteUrl.getHost() + "s/" + token + "/" + gid + "-" + page
        val result = SiteEngine.getGalleryPage(null, client, pageUrl, gid, token)
        return result.imageUrl
    }
}
