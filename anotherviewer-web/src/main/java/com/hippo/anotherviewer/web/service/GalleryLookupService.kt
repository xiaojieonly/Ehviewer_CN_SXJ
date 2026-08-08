package com.hippo.anotherviewer.web.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.hippo.anotherviewer.client.SiteEngine
import com.hippo.anotherviewer.client.SiteUrl
import com.hippo.anotherviewer.client.data.GalleryDetail
import com.hippo.anotherviewer.client.exception.SiteException
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import com.hippo.anotherviewer.web.repository.HistoryInfoRepository
import com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * Shared gateway for resolving Gallery Site gallery metadata through the single
 * [SiteSessionManager] client: token lookup (downloads → history → favorites),
 * gallery page counts (via `SiteEngine.getGalleryDetail`) and per-page image
 * URLs (via `SiteEngine.getGalleryPage`, EH page numbers are 1-based).
 *
 * Used by the image streaming endpoint, the processing pipeline and the
 * torrent/archive services so every upstream request shares one cookie jar
 * and one browser fingerprint.
 *
 * Image URLs: EH serves every image page under its own token
 * (`/s/<pageToken>/<gid>-<page>`, distinct from the gallery detail token).
 * The per-page URLs are parsed from the gallery detail page's preview data
 * ([GalleryDetail.previewSet]); the detail page is cached per gid so page
 * fetches (reader paging, downloads) do not re-fetch it for every page.
 */
@Service
class GalleryLookupService(
    private val downloadRepository: DownloadInfoRepository,
    private val historyRepository: HistoryInfoRepository,
    private val favoriteRepository: LocalFavoriteInfoRepository,
    private val sessionManager: SiteSessionManager,
) {
    private val client get() = sessionManager.okHttpClient

    /**
     * Detail-page cache keyed by gid. The preview data carries every page's
     * `/s/` URL (each with its own token); a short TTL bounds token staleness
     * while keeping reader paging and batch downloads off the detail page.
     */
    private val detailCache: Cache<Long, GalleryDetail> = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .maximumSize(200)
        .build()

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
    fun fetchPageCount(gid: Long, token: String): Int =
        detail(gid, token).pages

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
        val previewSet = detail(gid, token).previewSet
        if (page < 1 || page > previewSet.size()) {
            throw SiteException("Invalid page.")
        }
        // The page URL carries the page-specific /s/ token from the detail
        // page preview data; the gallery detail token is only the Referer.
        val pageUrl = previewSet.getPageUrlAt(page - 1)
        val result = SiteEngine.getGalleryPage(null, client, pageUrl, gid, token)
        return result.imageUrl
    }

    /**
     * Gallery detail (page count + per-page preview URLs), cached per gid.
     * On cache miss the detail page is fetched through the shared client.
     */
    private fun detail(gid: Long, token: String): GalleryDetail {
        val cached = detailCache.getIfPresent(gid)
        if (cached != null) return cached
        val detail = SiteEngine.getGalleryDetail(null, client, SiteUrl.getGalleryDetailUrl(gid, token))
        detailCache.put(gid, detail)
        return detail
    }
}
