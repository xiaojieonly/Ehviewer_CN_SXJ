package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.client.SiteRequestBuilder
import com.hippo.anotherviewer.client.SiteUrl
import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore

/**
 * Reader prefetch: after a gallery page P is served on a cache miss, warm the
 * image cache for P+1 .. min(P+prefetchPages, pageCount) in the background.
 *
 * All work runs on a small daemon pool ([MAX_PREFETCH_THREADS]) guarded by an
 * in-flight [Semaphore] ([MAX_IN_FLIGHT_PREFETCHES]) so prefetching can never
 * compete with user-facing fetches. Failures (509, network, parse) abort the
 * remaining pages of that chain silently — prefetching is best-effort and must
 * never surface to a request.
 */
@Service
class PrefetchService(
    private val galleryLookupService: GalleryLookupService,
    private val imageCacheService: ImageCacheService,
    private val sessionManager: SiteSessionManager,
    private val config: SiteCoreConfigProperties,
    private val availability: EhAvailabilityService,
) {
    private val logger = LoggerFactory.getLogger(PrefetchService::class.java)

    companion object {
        private const val MAX_PREFETCH_THREADS = 2
        private const val MAX_IN_FLIGHT_PREFETCHES = 8
    }

    private val client get() = sessionManager.okHttpClient

    private val executor: ExecutorService =
        Executors.newFixedThreadPool(MAX_PREFETCH_THREADS) { runnable ->
            Thread(runnable, "reader-prefetch").apply { isDaemon = true }
        }

    private val inFlight = Semaphore(MAX_IN_FLIGHT_PREFETCHES)

    private val prefetchPages: Int get() = config.reader.prefetchPages

    /**
     * Fire-and-forget prefetch for the pages after [page] (0-based). Never
     * blocks the caller and never throws. No-op when prefetching is disabled,
     * the in-flight budget is exhausted, or the gallery/page lookup fails.
     */
    fun prefetchAround(galleryId: Long, page: Int) {
        // EH DOWN 熔断：不发起任何预取（否则每次翻页都触发 4 个上游请求被短路）。
        if (availability.isBlocked()) {
            logger.debug("Reader prefetch skipped for gid={} page={}: EH unavailable", galleryId, page)
            return
        }
        val pages = prefetchPages
        if (pages <= 0) return
        if (!inFlight.tryAcquire()) {
            logger.debug("Reader prefetch skipped for gid={} page={}: in-flight limit reached", galleryId, page)
            return
        }
        try {
            executor.submit {
                try {
                    runPrefetch(galleryId, page, pages)
                } catch (e: Exception) {
                    logger.debug("Reader prefetch failed for gid={} page={}", galleryId, page, e)
                } finally {
                    inFlight.release()
                }
            }
        } catch (e: RejectedExecutionException) {
            logger.debug("Reader prefetch rejected for gid={} page={}", galleryId, page)
            inFlight.release()
        }
    }

    private fun runPrefetch(galleryId: Long, page: Int, pages: Int) {
        val token = galleryLookupService.findToken(galleryId)
        if (token == null) {
            logger.debug("Reader prefetch skipped for gid={}: gallery not found", galleryId)
            return
        }
        val pageCount = try {
            galleryLookupService.fetchPageCount(galleryId, token)
        } catch (e: Exception) {
            logger.debug("Reader prefetch stopped for gid={}: page count lookup failed", galleryId)
            return
        }
        if (pageCount <= 0) return

        // Pages are 0-based in the cache/API; pageCount is the 1-based total.
        val lastPage = minOf(page + pages, pageCount - 1)
        for (target in page + 1..lastPage) {
            if (isCached(galleryId, target)) {
                logger.debug("Reader prefetch skips gid={} page={}: already cached", galleryId, target)
                continue
            }
            val imageUrl = try {
                // EH page numbers are 1-based.
                galleryLookupService.fetchImageUrl(galleryId, token, target + 1)
            } catch (e: Exception) {
                logger.debug("Reader prefetch stopped at gid={} page={}: URL lookup failed", galleryId, target)
                return
            }
            val fetched = fetchPageImage(imageUrl)
            if (fetched == null) {
                logger.debug("Reader prefetch stopped at gid={} page={}: image fetch failed", galleryId, target)
                return
            }
            imageCacheService.cacheImageByKey(galleryId, target, fetched.bytes, fetched.extension)
        }
    }

    private fun isCached(galleryId: Long, page: Int): Boolean =
        imageCacheService.findCachedPageFile(galleryId, page) != null ||
            imageCacheService.getCachedImageByKey(galleryId, page) != null

    /** Download the image via the shared session client; null on any failure. */
    private fun fetchPageImage(url: String): FetchedImage? {
        return try {
            val response = client.newCall(SiteRequestBuilder(url, SiteUrl.getReferer()).build()).execute()
            try {
                if (response.code == 509 || !response.isSuccessful) return null
                val bytes = response.body?.bytes()
                if (bytes == null || bytes.isEmpty()) return null
                val contentType = response.header(HttpHeaders.CONTENT_TYPE) ?: MediaType.IMAGE_JPEG_VALUE
                FetchedImage(bytes, extensionFromMime(contentType))
            } finally {
                response.close()
            }
        } catch (e: Exception) {
            logger.debug("Prefetch fetch failed for {}: {}", url, e.message)
            null
        }
    }

    private data class FetchedImage(val bytes: ByteArray, val extension: String)

    private fun extensionFromMime(contentType: String): String {
        val mime = contentType.substringBefore(';').trim().lowercase()
        return when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
    }
}
