package com.hippo.anotherviewer.web.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Snapshot of the EH availability state machine, served by
 * GET/POST /api/v1/site/availability. `state` is UP | DOWN | UNKNOWN;
 * `downAt` / `lastReason` / `lastProbeAt` are null (or 0-mapped null) until the
 * first probe fails / the first probe ran.
 */
data class AvailabilityStatus(
    val state: String,
    val downAt: Long?,
    val lastReason: String?,
    val lastProbeAt: Long?,
)

/**
 * Process-level EH reachability state machine (UNKNOWN / UP / DOWN) with
 * manual-probe semantics (docs/plan-2026-08-30-eh-circuit-breaker.md §3.1):
 *
 * - `state == DOWN` ([isBlocked]) short-circuits every automatic upstream
 *   EH request at the caller (search/feed/detail-enrich/image fetch/proxy/
 *   prefetch/download start) — see the task table in §3.2 of the plan.
 * - Probes are only issued by MANUAL user actions (POST
 *   `/api/v1/site/availability`, health-check galleryApi probe): there is no
 *   TTL-based auto-recovery and no background sweeping. On success the state
 *   returns to UP; on failure it stays DOWN (the state never transitions
 *   UNKNOWN → DOWN except through a probe that actually fails).
 * - The probe is a single flight: concurrent [probeNow] callers never issue
 *   more than one real HEAD/HTTP request; losers return the current result.
 *
 * Testability: a [probe] lambda may be injected at construction time to fake
 * network results deterministically (state transitions/blocked short-circuits
 * are then fully testable without a JVM client or the system curl executor).
 */
@Service
class EhAvailabilityService(
    @Value("\${anotherviewer.availability.probe-url:https://e-hentai.org}")
    private val probeUrl: String,
    @Value("\${anotherviewer.availability.probe-timeout-ms:5000}")
    private val probeTimeoutMs: Long,
    private val probe: (() -> Boolean)? = null,
) {
    private val logger = LoggerFactory.getLogger(EhAvailabilityService::class.java)

    /**
     * 独立 JDK HttpClient（非共享 session client）：
     * 共享 client 挂 CurlSiteExecutor（系统 curl，--max-time 60）与 30s
     * connect 超时，探测会被卡 60s（部署机实测）。探测只是 Reachability
     * 判定，不需要 cookie/UA 指纹，JDK client 的 connect+request 超时
     * 精确等于 probe-timeout-ms（默认 5s），下游短路语义不受影响。
     */
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(probeTimeoutMs))
        // NEVER：3xx 原样返回（本服务以 200..399 计可达），跟随重定向
        // 反而引入额外往返与无 Location 响应的异常路径。
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    enum class State { UNKNOWN, UP, DOWN }

    /** Only while DOWN — the whole circuit-breaker contract. */
    @Volatile
    private var state: State = State.UNKNOWN

    @Volatile
    private var downAt: Long? = null

    @Volatile
    private var lastReason: String? = null

    @Volatile
    private var lastProbeAt: Long = 0L

    /** Single-flight guard for [probeNow]. */
    private val probeInFlight = AtomicBoolean(false)

    fun isBlocked(): Boolean = state == State.DOWN

    /** Marks the site DOWN. Called by probe failures and future external observers. */
    fun recordFailure(reason: String) {
        state = State.DOWN
        downAt = System.currentTimeMillis()
        lastReason = reason
        logger.warn("EH availability changed to DOWN: {}", reason)
    }

    /** Marks the site UP; clears downAt/lastReason (still recorded at the last probe time). */
    fun recordSuccess() {
        state = State.UP
        downAt = null
        lastReason = null
        logger.info("EH availability changed to UP")
    }

    /**
     * Manual probe: one real upstream request, single-flighted.
     *
     * Returns `true` when the site is reachable (UP) and `false` when it is
     * DOWN after this probe. Concurrent callers while a probe is in flight do
     * NOT issue a second request: they return `!isBlocked()` — the current
     * result — and leave `lastProbeAt` untouched (no new probe happened).
     */
    fun probeNow(): Boolean {
        if (!probeInFlight.compareAndSet(false, true)) {
            // Another probe is already in flight; never stack HEAD requests.
            // Return the current state (the result of the in-flight probe is
            // not awaited — callers can retry after it lands).
            logger.debug("EH availability probe already in flight; returning current result")
            return !isBlocked()
        }
        return try {
            val ok = probe?.invoke() ?: probeHttp()
            if (ok) {
                recordSuccess()
            } else {
                recordFailure(lastReason ?: "probe failed")
            }
            lastProbeAt = System.currentTimeMillis()
            ok
        } catch (e: Exception) {
            recordFailure(e.message ?: "probe failed")
            lastProbeAt = System.currentTimeMillis()
            false
        } finally {
            probeInFlight.set(false)
        }
    }

    fun status(): AvailabilityStatus = AvailabilityStatus(
        state = state.name,
        downAt = downAt,
        lastReason = lastReason,
        lastProbeAt = lastProbeAt.takeIf { it > 0 },
    )

    /**
     * The real probe: HEAD [probeUrl] (default https://e-hentai.org) through a
     * dedicated JDK [HttpClient], bounded by
     * anotherviewer.availability.probe-timeout-ms (default 5000). Any 2xx/3xx
     * counts as reachable; 4xx/5xx/exceptions count as DOWN. On failure
     * [lastReason] is set so [probeNow] records the actual cause.
     *
     * Never routes through the shared OkHttp session client: it carries the
     * CurlSiteExecutor (system curl, --max-time 60) and 30s connect timeouts —
     * a dead network would pin a manual probe for up to 60s (observed on the
     * deployed box 2026-08-30). A reachability probe needs no cookies nor
     * fingerprint, so the JDK client's exact `probe-timeout-ms` bounds it.
     */
    internal fun probeHttp(): Boolean {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(probeUrl))
                .timeout(Duration.ofMillis(probeTimeoutMs))
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36"
                )
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
            if (response.statusCode() in 200..399) {
                logger.debug("EH availability probe OK (HTTP {})", response.statusCode())
                true
            } else {
                lastReason = "probe HTTP ${response.statusCode()}"
                false
            }
        } catch (e: Exception) {
            lastReason = e.message?.takeIf { it.isNotBlank() } ?: "probe failed"
            false
        }
    }
}
