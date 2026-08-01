package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.client.EhRequestBuilder
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.web.service.EhSessionManager
import com.hippo.ehviewer.web.service.GalleryLookupService
import com.hippo.ehviewer.web.service.ImageCacheService
import com.hippo.network.StatusCodeException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/**
 * Image delivery endpoints.
 *
 * - `GET /api/v1/image/{galleryId}/{page}` — stream a gallery page image
 *   (0-based page, on-demand fetch + cache, Range/206, optional enhanced
 *   variant). See contracts/openapi.yaml `streamGalleryImage`.
 * - `GET /api/v1/image/proxy` — legacy URL-keyed cache lookup (kept intact).
 * - `GET /api/v1/image/cache/status`, `POST /api/v1/image/cache/clear`.
 */
@RestController
@RequestMapping("/api/v1/image")
class ImageProxyController(
    private val imageCacheService: ImageCacheService,
    private val galleryLookupService: GalleryLookupService,
    private val sessionManager: EhSessionManager,
) {
    private val logger = LoggerFactory.getLogger(ImageProxyController::class.java)

    companion object {
        private const val MAX_CONCURRENT_PAGE_FETCHES = 2
        private const val CACHE_MAX_AGE = "max-age=86400"
        private val IMAGE_MIME_BY_EXT = mapOf(
            "jpg" to MediaType.IMAGE_JPEG_VALUE,
            "jpeg" to MediaType.IMAGE_JPEG_VALUE,
            "png" to MediaType.IMAGE_PNG_VALUE,
            "webp" to "image/webp",
            "gif" to MediaType.IMAGE_GIF_VALUE,
        )
    }

    private val okHttpClient get() = sessionManager.okHttpClient

    /** Per-gallery concurrency guard so a single reader cannot saturate EH (contract 429). */
    private val galleryFetchers = ConcurrentHashMap<Long, Semaphore>()

    // ── legacy endpoints (kept intact) ───────────────────────────

    @GetMapping("/proxy")
    fun proxyImage(@RequestParam url: String): ResponseEntity<ByteArray> {
        val cached = imageCacheService.getCachedImage(url)
        if (cached != null) {
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE)
                .body(cached)
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
    }

    @GetMapping("/cache/status")
    fun cacheStatus(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "cacheSize" to imageCacheService.getCacheSize()
        ))
    }

    @PostMapping("/cache/clear")
    fun clearCache(): ResponseEntity<Map<String, Boolean>> {
        imageCacheService.clearCache()
        return ResponseEntity.ok(mapOf("success" to true))
    }

    // ── streamGalleryImage ───────────────────────────────────────

    @GetMapping("/{galleryId}/{page}")
    fun streamGalleryImage(
        @PathVariable galleryId: Long,
        @PathVariable page: Int,
        @RequestParam(required = false) w: Int?,
        @RequestParam(name = "enhanced", required = false, defaultValue = "false") enhanced: Boolean,
        @RequestHeader(name = HttpHeaders.RANGE, required = false) range: String?
    ): ResponseEntity<*> {
        // Page numbers are 0-based per contract; reject negatives explicitly.
        if (page < 0) return notFound(galleryId, page)

        // Surface an expired E-Hentai login as 401 (see AuthExceptionHandler).
        sessionManager.requireValidSession()

        // Enhanced variant: serve from the processing output dir when present.
        if (enhanced) {
            val file = imageCacheService.getEnhancedImage(galleryId, page)
                ?: return notFound(galleryId, page)
            return serveFile(file, range)
        }

        // 1. Serve from cache (memory/disk) when available.
        val cachedFile = imageCacheService.findCachedPageFile(galleryId, page)
        if (cachedFile != null) {
            return serveFile(cachedFile, range)
        }
        val cached = imageCacheService.getCachedImageByKey(galleryId, page)
        if (cached != null) {
            return serveBytes(cached, MediaType.IMAGE_JPEG_VALUE, range)
        }

        // 2. Cache miss — fetch from E-Hentai via the shared session client.
        val fetcher = galleryFetchers.computeIfAbsent(galleryId) { Semaphore(MAX_CONCURRENT_PAGE_FETCHES) }
        if (!fetcher.tryAcquire()) {
            return rateLimited(galleryId, page)
        }
        try {
            return fetchAndServe(galleryId, page, range)
        } finally {
            fetcher.release()
            galleryFetchers.remove(galleryId, fetcher)
        }
    }

    // ── fetch / serve helpers ────────────────────────────────────

    private fun fetchAndServe(galleryId: Long, page: Int, range: String?): ResponseEntity<*> {
        val token = galleryLookupService.findToken(galleryId)
            ?: return notFound(galleryId, page)
        val imageUrl = try {
            // EH page numbers are 1-based; the endpoint is 0-based.
            galleryLookupService.fetchImageUrl(galleryId, token, page + 1)
        } catch (e: StatusCodeException) {
            if (e.code == 509) return rateLimited(galleryId, page)
            logger.warn("Failed to resolve page URL for gid={} page={}: {}", galleryId, page, e.message)
            return notFound(galleryId, page)
        } catch (e: Exception) {
            logger.warn("Failed to resolve page URL for gid={} page={}", galleryId, page, e)
            return notFound(galleryId, page)
        }

        return try {
            val builder = EhRequestBuilder(imageUrl, EhUrl.getReferer())
            if (range != null) {
                builder.header(HttpHeaders.RANGE, range)
            }
            val response = okHttpClient.newCall(builder.build()).execute()
            if (response.code() == 509) {
                response.close()
                return rateLimited(galleryId, page)
            }
            if (!response.isSuccessful) {
                response.close()
                return notFound(galleryId, page)
            }

            val contentType = response.header(HttpHeaders.CONTENT_TYPE) ?: MediaType.IMAGE_JPEG_VALUE

            // A Range request is streamed straight through (never buffered,
            // never cached) so the client receives a true 206 when EH honors it.
            if (range != null) {
                val body = response.body()
                if (body == null) {
                    response.close()
                    return notFound(galleryId, page)
                }
                val streaming = StreamingResponseBody { output ->
                    body.byteStream().use { input -> input.copyTo(output) }
                }
                return ResponseEntity
                    .status(response.code())
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CACHE_CONTROL, CACHE_MAX_AGE)
                    .apply {
                        response.header(HttpHeaders.CONTENT_RANGE)?.let { header(HttpHeaders.CONTENT_RANGE, it) }
                        response.header(HttpHeaders.CONTENT_LENGTH)?.let { header(HttpHeaders.CONTENT_LENGTH, it) }
                    }
                    .body(streaming)
            }

            // Full fetch: materialise once so the page is cached for later
            // (and future Range requests are served from the cache).
            val bytes = response.body()?.bytes()
            response.close()
            if (bytes == null || bytes.isEmpty()) {
                return notFound(galleryId, page)
            }
            val ext = extensionFromMime(contentType)
            imageCacheService.cacheImageByKey(galleryId, page, bytes, ext)
            MetricsController.imagesServed.incrementAndGet()
            serveBytes(bytes, contentType, null)
        } catch (e: Exception) {
            logger.warn("Failed to download page image for gid={} page={}", galleryId, page, e)
            notFound(galleryId, page)
        }
    }

    private fun serveFile(file: File, range: String?): ResponseEntity<*> {
        return try {
            val mime = IMAGE_MIME_BY_EXT[file.extension.lowercase()] ?: MediaType.IMAGE_JPEG_VALUE
            serveBytes(file.readBytes(), mime, range)
        } catch (e: Exception) {
            logger.warn("Failed to read cached image {}", file, e)
            ResponseEntity.status(HttpStatus.NOT_FOUND).build<Any>()
        }
    }

    private fun serveBytes(data: ByteArray, contentType: String, range: String?): ResponseEntity<*> {
        MetricsController.imagesServed.incrementAndGet()
        if (range != null) {
            val parsed = parseRange(range, data.size)
            if (parsed == null) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */${data.size}")
                    .build<Any>()
            }
            val (start, end) = parsed
            val length = end - start + 1
            val body = if (start == 0 && end == data.size - 1) data else data.copyOfRange(start, end + 1)
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_RANGE, "bytes $start-$end/${data.size}")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, CACHE_MAX_AGE)
                .header(HttpHeaders.CONTENT_LENGTH, length.toString())
                .body(body)
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, contentType)
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .header(HttpHeaders.CACHE_CONTROL, CACHE_MAX_AGE)
            .body(data)
    }

    /**
     * Parse a single `bytes=start-end` (or `bytes=start-` / `bytes=-suffix`)
     * range. Returns null when malformed or unsatisfiable.
     */
    private fun parseRange(header: String, size: Int): Pair<Int, Int>? {
        if (size <= 0) return null
        if (!header.startsWith("bytes=")) return null
        val spec = header.substring(6).trim().split(",").firstOrNull() ?: return null
        val dash = spec.indexOf('-')
        if (dash < 0) return null
        val startStr = spec.substring(0, dash).trim()
        val endStr = spec.substring(dash + 1).trim()
        val start: Int
        val end: Int
        if (startStr.isEmpty()) {
            // Suffix range: last N bytes.
            val suffix = endStr.toIntOrNull() ?: return null
            if (suffix <= 0) return null
            start = (size - suffix).coerceAtLeast(0)
            end = size - 1
        } else {
            start = startStr.toIntOrNull() ?: return null
            if (start >= size) return null
            end = if (endStr.isEmpty()) size - 1 else (endStr.toIntOrNull() ?: return null).coerceAtMost(size - 1)
        }
        if (start > end) return null
        return start to end
    }

    private fun extensionFromMime(contentType: String): String {
        val mime = contentType.substringBefore(';').trim().lowercase()
        return when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
    }

    private fun notFound(galleryId: Long, page: Int): ResponseEntity<*> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf(
                "error" to "gallery or page not found",
                "galleryId" to galleryId,
                "page" to page
            ))
    }

    private fun rateLimited(galleryId: Long, page: Int): ResponseEntity<*> {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf(
                "error" to "rate limited: too many concurrent fetches for this gallery",
                "galleryId" to galleryId,
                "page" to page
            ))
    }
}
