package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.client.SiteRequestBuilder
import com.hippo.anotherviewer.client.SiteUrl
import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.entity.Tier2PageEntity
import com.hippo.anotherviewer.web.repository.Tier2PageRepository
import com.hippo.anotherviewer.web.util.ResponseTooLargeException
import com.hippo.anotherviewer.web.util.bytesBounded
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Persistent EH metadata page cache for Tier-2 browsing（App Tier-2 浏览的
 * 列表页/详情页缓存）：所有成功抓取的 Gallery Site 页面按规范化 URL 落库
 * （[Tier2PageRepository] / SQLite `tier2_page`），上游不可达时回源缓存
 * （stale），并由后台任务周期性刷新过期行。
 *
 * 语义（GET /api/v1/tier2/page 契约，见 [com.hippo.anotherviewer.web.api.Tier2PageController]）：
 *  - 源站可达且返回 2xx → 持久化 body + 时间戳，返回 fresh；
 *  - 源站不可达（[EhAvailabilityService] DOWN、网络 IOException、上游 5xx、
 *    响应超限）→ 有缓存行（无论多旧）即回 stale，无缓存行即 Unavailable（404）；
 *  - 源站可达但返回其他状态（如 404/403）→ 原样透传（E2E-6：错误不被掩盖，
 *    也不拿旧内容顶替错误）。
 *
 * 抓取复用共享会话 client（[SiteSessionManager.okHttpClient]，cookie/代理/
 * 浏览器指纹与站点其余流量一致，经 [SiteRequestBuilder]）。与
 * [GalleryLookupService] 的 10min Caffeine detail 缓存互不相干：那是
 * gid→解析结果的结构化缓存，本服务缓存的是原始页面字节。
 *
 * 后台刷新：`fixedDelay` 任务（默认 30min 一轮）挑 `fetched_at` 早于刷新
 * TTL（默认 6h）的最旧一批（默认 10 条/轮）重新抓取；availability DOWN 时
 * 整轮跳过；单条失败保持原 fetched_at（下轮最先重试）。任务运行在调度线程，
 * 绝不在请求线程上。容量上限（默认 2000 行，<=0 不裁剪）：每轮刷新后裁掉
 * 超限的最旧行——最近刷新过的行 fetched_at 最新，天然最不易被裁。
 */
@Service
class Tier2PageService(
    private val repository: Tier2PageRepository,
    private val sessionManager: SiteSessionManager,
    private val availability: EhAvailabilityService,
    private val config: SiteCoreConfigProperties,
    // ── 后台刷新配置（application.yml anotherviewer.tier2.refresh.*）──
    @Value("\${anotherviewer.tier2.refresh.enabled:true}")
    private val refreshEnabled: Boolean,
    @Value("\${anotherviewer.tier2.refresh.max-age-ms:21600000}")
    private val refreshMaxAgeMs: Long,
    @Value("\${anotherviewer.tier2.refresh.batch-size:10}")
    private val refreshBatchSize: Int,
    // ── 容量裁剪（anotherviewer.tier2.cache.max-entries；<=0 = 不裁剪）──
    @Value("\${anotherviewer.tier2.cache.max-entries:2000}")
    private val maxEntries: Int,
) {

    private val logger = LoggerFactory.getLogger(Tier2PageService::class.java)

    private val client get() = sessionManager.okHttpClient

    /** [Tier2PageService.fetchPage] 的结果：控制器据此映射 HTTP 响应。 */
    sealed class Tier2PageResult {
        /** 200（fresh：刚从源站抓到并已落库；stale：源站不可达时的缓存回源）。 */
        data class Served(val body: String, val contentType: String, val stale: Boolean) : Tier2PageResult()

        /** 源站可达但回了非 2xx/5xx 状态（4xx/3xx）——状态与 body 原样透传。 */
        data class UpstreamError(val status: Int, val body: String, val contentType: String) : Tier2PageResult()

        /** 源站不可达且无任何缓存行 → 控制器回 404 EH_UNAVAILABLE 信封。 */
        object Unavailable : Tier2PageResult()
    }

    /**
     * Tier-2 页面入口：fresh 优先、不可达回 stale。调用方（控制器）负责
     * host 白名单校验；此处只做规范化并落库/回源。
     */
    fun fetchPage(rawUrl: String): Tier2PageResult {
        val target = normalize(rawUrl)
            ?: return Tier2PageResult.Unavailable
        // 熔断 DOWN：不发起任何上游请求，直接走缓存回源（含无缓存的 Unavailable）。
        if (availability.isBlocked()) {
            logger.debug("Tier2 page fetch skipped for url={}: EH unavailable", rawUrl)
            return cachedOrUnavailable(target)
        }
        return when (val fetched = fetchFromOrigin(target)) {
            is OriginResult.Success -> Tier2PageResult.Served(fetched.body, fetched.contentType, stale = false)
            is OriginResult.UpstreamError -> Tier2PageResult.UpstreamError(fetched.status, fetched.body, fetched.contentType)
            OriginResult.Failed -> cachedOrUnavailable(target)
        }
    }

    /**
     * Periodic refresh of cached pages older than [refreshMaxAgeMs]（默认 6h）：
     * oldest-first、单轮最多 [refreshBatchSize] 条（限速），DOWN 整轮跳过。
     * 任何单条失败只记 debug——刷新是尽力而为，绝不允许把调度线程打挂。
     */
    @Scheduled(fixedDelayString = "\${anotherviewer.tier2.refresh.interval-ms:1800000}")
    fun refreshExpiredEntries() {
        if (!refreshEnabled) return
        if (availability.isBlocked()) {
            logger.debug("Tier2 page refresh skipped: EH unavailable")
            return
        }
        val cutoff = System.currentTimeMillis() - refreshMaxAgeMs
        val candidates = repository.findByFetchedAtLessThanOrderByFetchedAtAsc(
            cutoff,
            PageRequest.of(0, refreshBatchSize.coerceAtLeast(1), Sort.by(Sort.Direction.ASC, "fetchedAt")),
        )
        var refreshed = 0
        for (row in candidates) {
            val target = normalize(row.url)
            if (target == null) {
                // 行内的 URL 已不可解析（理论不可达：只存规范化 URL）——删掉避免永久重试。
                repository.delete(row)
                continue
            }
            when (fetchFromOrigin(target)) {
                is OriginResult.Success -> refreshed++
                else -> logger.debug("Tier2 page refresh failed for url={}; keeping cached row", row.url)
            }
        }
        if (candidates.isNotEmpty()) {
            logger.info("Tier2 page refresh pass: {} candidates, {} refreshed", candidates.size, refreshed)
        }
        trimIfNeeded()
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private sealed class OriginResult {
        data class Success(val body: String, val contentType: String) : OriginResult()
        data class UpstreamError(val status: Int, val body: String, val contentType: String) : OriginResult()
        object Failed : OriginResult()
    }

    /** Normalize to the cache key form: lowercase host, default port dropped, no fragment. */
    private fun normalize(rawUrl: String): HttpUrl? =
        rawUrl.toHttpUrlOrNull()?.newBuilder()?.fragment(null)?.build()

    /** 源站不可达路径的兜底：有任何缓存行（无论多旧）即 stale 回源。 */
    private fun cachedOrUnavailable(target: HttpUrl): Tier2PageResult {
        val cached = repository.findById(target.toString()).orElse(null) ?: return Tier2PageResult.Unavailable
        return Tier2PageResult.Served(cached.body, cached.contentType, stale = true)
    }

    /**
     * One upstream fetch through the shared session client (same call pattern
     * as SiteProxyController: browser fingerprint + slash-terminated referer).
     * Network failures / 5xx / oversize bodies map to [OriginResult.Failed]
     *（触发 stale 回源）；其他非 2xx 原样透传，2xx 由调用方落库。
     */
    private fun fetchFromOrigin(target: HttpUrl): OriginResult {
        val request = SiteRequestBuilder(target.toString(), SiteUrl.getReferer() + "/", SiteUrl.getOrigin())
        return try {
            client.newCall(request.build()).execute().use { response ->
                if (response.code in 500..599) {
                    logger.info("Tier2 page fetch got HTTP {} for url={}", response.code, target)
                    return OriginResult.Failed
                }
                val contentType = response.header("Content-Type") ?: DEFAULT_CONTENT_TYPE
                // 有界读取（MASTER-2026-08-22 S2）：超限视为抓取失败 → stale 回源，
                // 绝不缓存半截/超大 body 打爆堆。
                val bytes = try {
                    response.body?.bytesBounded(config.proxy.maxResponseBytes) ?: ByteArray(0)
                } catch (e: ResponseTooLargeException) {
                    logger.warn("Tier2 page fetch aborted: upstream body exceeds {} bytes for url={}", e.maxBytes, target)
                    return OriginResult.Failed
                }
                // Latin-1 解码（每字节 1:1 映射码点）保证 TEXT 落库/还原逐字节无损；
                // 字符集语义由原样回传的 Content-Type 承载，客户端自行按其解码。
                val body = String(bytes, Charsets.ISO_8859_1)
                if (response.isSuccessful) {
                    upsert(target.toString(), body, contentType)
                    OriginResult.Success(body, contentType)
                } else {
                    OriginResult.UpstreamError(response.code, body, contentType)
                }
            }
        } catch (e: Exception) {
            logger.info("Tier2 page fetch failed for url={}: {}", target, e.message)
            OriginResult.Failed
        }
    }

    /** Upsert by normalized URL（PK）：刷新 fetched_at，body/content_type 原地覆盖。 */
    private fun upsert(url: String, body: String, contentType: String) {
        val entity = repository.findById(url).orElseGet { Tier2PageEntity() }
        entity.url = url
        entity.body = body
        entity.contentType = contentType
        entity.fetchedAt = System.currentTimeMillis()
        repository.save(entity)
    }

    /** 容量裁剪：超出 [maxEntries] 的最旧行按 fetched_at 升序删除（<=0 = 不裁剪）。 */
    private fun trimIfNeeded() {
        if (maxEntries <= 0) return
        val total = repository.count()
        val excess = total - maxEntries
        if (excess <= 0) return
        val oldest = repository.findAll(
            PageRequest.of(0, excess.toInt(), Sort.by(Sort.Direction.ASC, "fetchedAt"))
        ).content
        if (oldest.isNotEmpty()) {
            repository.deleteAllInBatch(oldest)
            logger.info("Tier2 page cache trimmed: removed {} oldest rows (total was {})", oldest.size, total)
        }
    }

    companion object {
        private const val DEFAULT_CONTENT_TYPE = "text/html; charset=UTF-8"
    }
}
