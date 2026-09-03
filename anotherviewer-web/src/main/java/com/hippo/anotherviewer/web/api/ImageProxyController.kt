package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.client.SiteRequestBuilder
import com.hippo.anotherviewer.client.SiteUrl
import com.hippo.anotherviewer.client.exception.SiteException
import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.JobSubmitResponse
import com.hippo.anotherviewer.web.dto.JobType
import com.hippo.anotherviewer.web.util.ResponseTooLargeException
import com.hippo.anotherviewer.web.util.bytesBounded
import com.hippo.anotherviewer.web.service.DownloadDirIndex
import com.hippo.anotherviewer.web.service.DownloadService
import com.hippo.anotherviewer.web.service.EhAvailabilityService
import com.hippo.anotherviewer.web.service.EhUnavailableException
import com.hippo.anotherviewer.web.service.Job
import com.hippo.anotherviewer.web.service.JobService
import com.hippo.anotherviewer.web.service.SiteSessionManager
import com.hippo.anotherviewer.web.service.GalleryLookupService
import com.hippo.anotherviewer.web.service.ImageCacheService
import com.hippo.anotherviewer.web.service.PrefetchService
import com.hippo.network.StatusCodeException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/**
 * Image delivery endpoints.
 *
 * - `GET /api/v1/image/{galleryId}/{page}` — stream a gallery page image
 *   (0-based page, on-demand fetch + cache, Range/206, optional enhanced
 *   variant). See contracts/openapi.yaml `streamGalleryImage`.
 * - `GET /api/v1/image/proxy` — URL-keyed cache lookup; W3 R4-13 added
 *   fetch-on-miss for whitelisted Gallery Site URLs (backfill + content-type
 *   pass-through, unreachable site keeps the 404 envelope).
 * - `GET /api/v1/image/cache/status`, `POST /api/v1/image/cache/clear`.
 */
@RestController
@RequestMapping("/api/v1/image")
class ImageProxyController(
    private val imageCacheService: ImageCacheService,
    private val galleryLookupService: GalleryLookupService,
    private val sessionManager: SiteSessionManager,
    private val prefetchService: PrefetchService,
    private val downloadService: DownloadService,
    // MASTER-2026-08-22 P10：required 注入——构造器默认参数会绕过 Spring 装配
    // 静默产生孤儿 JobStore/配置实例，测试需要替身时显式传入。
    private val config: SiteCoreConfigProperties,
    private val jobService: JobService,
    private val availability: EhAvailabilityService,
    private val downloadDirIndex: DownloadDirIndex,
) {
    private val logger = LoggerFactory.getLogger(ImageProxyController::class.java)

    companion object {
        private const val MAX_CONCURRENT_PAGE_FETCHES = 4
        private const val CACHE_MAX_AGE = "max-age=86400"
        /** P3: /proxy 全局并发 curl 上限（25 卡首开 → 6 并发批次排队）。 */
        private const val MAX_CONCURRENT_PROXY_FETCHES = 6
        /** P4: 缩略图 per-request curl --max-time（秒）；上游挂着时快速失败，不占满默认 60s。 */
        private const val PROXY_FETCH_TIMEOUT_SEC = 10
        private val IMAGE_MIME_BY_EXT = mapOf(
            "jpg" to MediaType.IMAGE_JPEG_VALUE,
            "jpeg" to MediaType.IMAGE_JPEG_VALUE,
            "png" to MediaType.IMAGE_PNG_VALUE,
            "webp" to "image/webp",
            "gif" to MediaType.IMAGE_GIF_VALUE,
        )
    }

    private val okHttpClient get() = sessionManager.okHttpClient

    /**
     * In-flight page fetches keyed by (galleryId, page): concurrent requests
     * for the SAME page share one upstream fetch instead of each tripping the
     * gallery concurrency limit (a reader's srcset 1x/2x + dual-page mode used
     * to race 4+ requests against a Semaphore(2) → spurious 429 storms).
     */
    private val pageFetchers = ConcurrentHashMap<String, CompletableFuture<ResponseEntity<*>>>()
    /** Per-gallery concurrency cap so one reader cannot saturate EH. */
    private val gallerySemaphores = ConcurrentHashMap<Long, java.util.concurrent.Semaphore>()

    /**
     * P3: in-flight thumbnail fetches keyed by URL — concurrent `/proxy` misses
     * for the SAME url share one upstream fetch (first page of 25 cards used to
     * trip 25 concurrent curl processes + 75 temp files). Distinct urls are
     * capped by the global [proxyFetchSemaphore]; excess requests queue on the
     * semaphore instead of erroring (thumbnails have no client retry logic).
     */
    private val proxyFetchers = ConcurrentHashMap<String, CompletableFuture<ResponseEntity<*>>>()
    private val proxyFetchSemaphore = Semaphore(MAX_CONCURRENT_PROXY_FETCHES)

    // ── legacy endpoints (kept intact) ───────────────────────────

    @GetMapping("/proxy")
    fun proxyImage(@RequestParam url: String): ResponseEntity<*> {
        val cached = imageCacheService.getCachedImage(url)
        if (cached != null) {
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE)
                .body(cached)
        }

        // W3 R4-13 fetch-on-miss (acceptance addition for the Tier-2 thumbnail
        // rewrite, merged in a4 #13): a cache miss for a Gallery Site URL is
        // fetched through the shared session client (cookies/proxy inherited),
        // backfilled into the cache and served with the site's content-type.
        // Non-whitelisted hosts are never fetched (legacy 404 stands), and an
        // unreachable/erroring site surfaces the legacy 404 envelope (E2E-6:
        // errors are passed through, never papered over with fake content).
        val target = url.toHttpUrlOrNull()
        if (target == null || !SiteProxyController.isGallerySiteHost(target.host)) {
            return errorEnvelope(HttpStatus.NOT_FOUND, "NOT_FOUND", "Image not found in cache")
        }

        // EH DOWN 熔断：cache miss 直接秒回 404 EH_UNAVAILABLE，不发起任何上游请求。
        if (availability.isBlocked()) {
            logger.debug("Image proxy miss skipped for url={}: EH unavailable", url)
            return errorEnvelope(HttpStatus.NOT_FOUND, EhUnavailableException.CODE, EhUnavailableException.USER_MESSAGE)
        }

        // P3: 同 URL 并发请求共享一次上游 fetch。已在途 → 直接等它的结果
        // （失败以 completeExceptionally 收场，等的人统一回落 404 envelope）。
        val existing = proxyFetchers[url]
        if (existing != null) {
            return try {
                existing.join()
            } catch (e: Exception) {
                logger.warn("In-flight image proxy fetch failed for url={}", url, e)
                proxyUnavailableEnvelope()
            }
        }

        // P3: 全局并发上限——acquire 阻塞在请求线程上（排队成 6 并发批次），
        // 成功注册的 future 独占本次 fetch；竞态败者释放多占的额度改等胜者。
        proxyFetchSemaphore.acquire()
        val future = CompletableFuture.supplyAsync { fetchProxyImage(target, url) }
        val raced = proxyFetchers.putIfAbsent(url, future)
        if (raced != null) {
            proxyFetchSemaphore.release()
            return try {
                raced.join()
            } catch (e: Exception) {
                logger.warn("In-flight image proxy fetch failed for url={}", url, e)
                proxyUnavailableEnvelope()
            }
        }
        try {
            return future.join()
        } catch (e: Exception) {
            logger.warn("Image proxy fetch failed for url={}", url, e)
            return proxyUnavailableEnvelope()
        } finally {
            proxyFetchers.remove(url, future)
            proxyFetchSemaphore.release()
        }
    }

    /** P3: /proxy 上游失败时等价于旧 catch 路径的 404 envelope。 */
    private fun proxyUnavailableEnvelope(): ResponseEntity<*> =
        errorEnvelope(HttpStatus.NOT_FOUND, "NOT_FOUND", "gallery site unreachable")

    /**
     * P3: /proxy 的单次上游抓取（自原 proxyImage miss 路径抽出，语义不变）。
     * 由 [proxyFetchers] 注册方经 CompletableFuture 执行；网络层异常向上抛出
     * （future completeExceptionally，等价旧 catch 路径，join 方统一记日志），
     * HTTP 层错误仍以 envelope 返回。
     */
    private fun fetchProxyImage(target: HttpUrl, url: String): ResponseEntity<*> {
        // EH validates the Referer of thumbnail-origin requests strictly:
        // an origin without the trailing slash (SiteUrl.REFERER_*) is
        // rejected with 403 on s.exhentai.org. Send the slash form.
        val request = SiteRequestBuilder(target.toString(), siteReferer()).build()
        // P4: 缩略图打 10s per-request 超时 tag（curl --max-time）；tag 不进
        // 线路（CurlSiteExecutor 只读不剥离），阅读器页图路径维持默认 60s。
        // tag 类型用 Integer（javaObjectType）——原始类型 int.class 会 CCE。
        val tagged = request.newBuilder().tag(Int::class.javaObjectType, PROXY_FETCH_TIMEOUT_SEC).build()
        okHttpClient.newCall(tagged).execute().use { response ->
            if (!response.isSuccessful) {
                return errorEnvelope(
                    HttpStatus.NOT_FOUND,
                    "NOT_FOUND",
                    "site did not serve the image (HTTP ${response.code})"
                )
            }
            val contentType = response.header(HttpHeaders.CONTENT_TYPE) ?: MediaType.IMAGE_JPEG_VALUE
            // MASTER-2026-08-22 S2：上游响应有界读取，超限 502（防恶意/异常大图打爆堆）。
            val bytes = try {
                response.body?.bytesBounded(config.proxy.maxResponseBytes)
            } catch (e: ResponseTooLargeException) {
                logger.warn("Image proxy fetch-on-miss aborted: upstream body exceeds {} bytes for url={}", e.maxBytes, url)
                return errorEnvelope(HttpStatus.BAD_GATEWAY, "UPSTREAM_TOO_LARGE", "Upstream image exceeds size limit")
            }
            if (bytes == null || bytes.isEmpty()) {
                return errorEnvelope(HttpStatus.NOT_FOUND, "NOT_FOUND", "site returned an empty image body")
            }
            imageCacheService.cacheImage(url, bytes)
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(bytes)
        }
    }

    @GetMapping("/cache/status")
    fun cacheStatus(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "cacheSize" to imageCacheService.getCacheSize()
        ))
    }

    @PostMapping("/cache/clear")
    fun clearCache(): ResponseEntity<*> {
        lateinit var job: Job
        try {
            job = jobService.submit(JobType.CACHE_CLEAR, {}) { handle ->
                val outcome = imageCacheService.clearCache(handle)
                job.result = mapOf("removed" to outcome.removed, "total" to outcome.total)
            }
        } catch (e: IllegalStateException) {
            return errorEnvelope(HttpStatus.CONFLICT, "CONFLICT", e.message ?: "已有清缓存任务进行中")
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(JobSubmitResponse(job.jobId, job.state))
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

        // Surface an expired Gallery Site login as 401 (see AuthExceptionHandler).
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

        // 1.5. App-pushed download fallback: downloads/<gid>/%04d.<ext> (1-based).
        //      Checked after the cache and before any upstream EH fetch, so
        //      pushed content is served locally even when EH is unreachable.
        val pushedFile = findPushedPageFile(galleryId, page)
        if (pushedFile != null) {
            // 需求 1（2026-08-30）：阅读命中存储池文件 → 若磁盘校验通过即置
            // 「已完成」——导入时所有行视为未下载完成，阅读匹配后升级。
            downloadService.completeIfVerified(galleryId)
            return serveFile(pushedFile, range)
        }

        // EH DOWN 熔断：cache miss + pushed miss 后、进入 fetch 前秒回
        // 404 EH_UNAVAILABLE（不占用 semaphore、不发起上游、不触发 prefetch）。
        if (availability.isBlocked()) {
            logger.debug("Page stream fetch skipped for gid={} page={}: EH unavailable", galleryId, page)
            return errorEnvelope(HttpStatus.NOT_FOUND, EhUnavailableException.CODE, EhUnavailableException.USER_MESSAGE)
        }

        // 2. Cache miss — fetch from Gallery Site via the shared session client.
        //    Concurrent requests for the same (galleryId, page) share ONE upstream
        //    fetch (srcset 1x/2x + dual-page mode issue several identical requests);
        //    distinct pages are additionally capped per-gallery so one reader
        //    cannot saturate EH. A busy page waits for the in-flight fetch instead
        //    of immediately 429ing (old behavior caused retry storms).
        val key = "$galleryId:$page"
        val existing = pageFetchers[key]
        if (existing != null) {
            return try {
                existing.join()
            } catch (e: Exception) {
                logger.warn("In-flight page fetch failed for gid={} page={}", galleryId, page, e)
                notFound(galleryId, page)
            }
        }
        val fetcher = gallerySemaphores.computeIfAbsent(galleryId) { java.util.concurrent.Semaphore(MAX_CONCURRENT_PAGE_FETCHES) }
        if (!fetcher.tryAcquire()) {
            // Distinct-page concurrency cap reached — degrade to 429; the client
            // retries with backoff (see PageMode). Never queues unboundedly.
            return rateLimited(galleryId, page)
        }
        val future = CompletableFuture.supplyAsync {
            try {
                fetchAndServe(galleryId, page, range)
            } finally {
                fetcher.release()
            }
        }
        val raced = pageFetchers.putIfAbsent(key, future)
        if (raced != null) {
            // Another thread registered the same page first — wait on theirs.
            fetcher.release()
            return raced.join()
        }
        try {
            val response = future.join()
            // Reader prefetch: warm the next pages in the background, only when
            // this page was actually served (fire-and-forget, never blocks).
            if (response.statusCode.is2xxSuccessful) {
                prefetchService.prefetchAround(galleryId, page)
            }
            return response
        } finally {
            pageFetchers.remove(key, future)
        }
    }

    // ── fetch / serve helpers ────────────────────────────────────

    /**
     * Probes the App-pushed download layout `downloads/<gid>/%04d.<ext>` for a
     * 0-based [page] (files are 1-based, hence page+1), via the in-memory
     * [DownloadDirIndex] — no disk scan per request. The indexed entry is
     * still validated (`isFile && length > 0`) against the actual file system
     * before it is served. Returns null so the caller falls through to the
     * upstream EH fetch (or, while DOWN, the EH_UNAVAILABLE 404).
     */
    private fun findPushedPageFile(galleryId: Long, page: Int): File? {
        val ref = downloadDirIndex.findPage(galleryId, page) ?: return null
        // 目录可为 `{gid}`（旧）或 `{gid}-{title}`（新/Android）；经索引的
        // PageRef.fileName 是磁盘真实文件名（4 位/8 位均可），子目录由索引
        // findDir 语义解析——此处借 downloadDirIndex 定位目录，避免按纯 gid 拼路径。
        val dir = downloadDirIndex.dirFor(galleryId)
        if (dir == null) return null
        val file = File(dir, ref.fileName)
        return if (file.isFile && file.length() > 0) file else null
    }

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
        } catch (e: SiteException) {
            // "Invalid page." is what EH serves when the (anonymous) session
            // cannot read the page — the gallery needs a signed-in EH session.
            logger.warn("Page URL rejected by site for gid={} page={} (likely session-gated): {}", galleryId, page, e.message)
            return sessionRequired(galleryId, page)
        } catch (e: Exception) {
            logger.warn("Failed to resolve page URL for gid={} page={}", galleryId, page, e)
            return notFound(galleryId, page)
        }

        return try {
            val builder = SiteRequestBuilder(imageUrl, siteReferer())
            if (range != null) {
                builder.header(HttpHeaders.RANGE, range)
            }
            val response = okHttpClient.newCall(builder.build()).execute()
            if (response.code == 509) {
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
                val body = response.body
                if (body == null) {
                    response.close()
                    return notFound(galleryId, page)
                }
                val streaming = StreamingResponseBody { output ->
                    body.byteStream().use { input -> input.copyTo(output) }
                }
                return ResponseEntity
                    .status(response.code)
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
            // MASTER-2026-08-22 S2：有界读取，超限 502。
            val bytes = try {
                response.body?.bytesBounded(config.proxy.maxResponseBytes)
            } catch (e: ResponseTooLargeException) {
                response.close()
                logger.warn("Page fetch aborted: upstream body exceeds {} bytes for gid={} page={}", e.maxBytes, galleryId, page)
                return errorEnvelope(HttpStatus.BAD_GATEWAY, "UPSTREAM_TOO_LARGE", "Upstream image exceeds size limit")
            }
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
            errorEnvelope(HttpStatus.NOT_FOUND, "NOT_FOUND", "Image not found")
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
        return errorEnvelope(HttpStatus.NOT_FOUND, "NOT_FOUND", "gallery or page not found")
    }

    private fun rateLimited(galleryId: Long, page: Int): ResponseEntity<*> {
        return errorEnvelope(
            HttpStatus.TOO_MANY_REQUESTS,
            "RATE_LIMITED",
            "rate limited: too many concurrent fetches for this gallery"
        )
    }

    /** EH gated the page behind a signed-in session ("Invalid page." / similar). */
    private fun sessionRequired(galleryId: Long, page: Int): ResponseEntity<*> {
        return errorEnvelope(
            HttpStatus.UNAUTHORIZED,
            "SESSION_REQUIRED",
            "该画廊需要登录 EH 会话才能阅读"
        )
    }

    /**
     * Gallery Site origin as an HTTP Referer. EH strictly validates Referer
     * shape on thumbnail-origin hosts (s.exhentai.org): the bare
     * `https://exhentai.org` from [SiteUrl.getReferer] is rejected with 403,
     * the slash-terminated origin form is accepted.
     */
    private fun siteReferer(): String = SiteUrl.getReferer() + "/"
}
