package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.any
import okhttp3.Call
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pins [EhAvailabilityService] state machine semantics
 * (docs/plan-2026-08-30-eh-circuit-breaker.md §3.1): UNKNOWN initial state,
 * recordFailure → DOWN + downAt/lastReason, recordSuccess → UP, probeNow
 * outcomes, and the single-flight guard.
 */
class EhAvailabilityServiceTest {

    private fun service(probe: () -> Boolean = { true }): EhAvailabilityService =
        EhAvailabilityService(mock(SiteSessionManager::class.java), "https://e-hentai.org", 5000, probe = probe)

    /** probeHttp 真实路径的替身客户端：返回固定 HTTP 状态码或抛异常。 */
    private data class ProbeHarness(val service: EhAvailabilityService, val call: Call)

    private fun httpHarness(code: Int): ProbeHarness {
        val sessionManager = mock(SiteSessionManager::class.java)
        val client = mock(okhttp3.OkHttpClient::class.java)
        `when`(sessionManager.okHttpClient).thenReturn(client)
        val call = mock(Call::class.java)
        `when`(client.newCall(any(Request::class.java))).thenReturn(call)
        // Call.timeout() returns okio.Timeout; the service only holds it, never calls it.
        `when`(call.timeout()).thenReturn(mock(okio.Timeout::class.java))
        val request = Request.Builder().url("https://e-hentai.org").method("HEAD", null).build()
        `when`(call.execute()).thenReturn(
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("OK")
                .body(ByteArray(0).toResponseBody("text/plain".toMediaType()))
                .build()
        )
        return ProbeHarness(
            EhAvailabilityService(sessionManager, "https://e-hentai.org", 5000),
            call
        )
    }

    // ── state machine ──────────────────────────────────────────

    @Test
    fun `initial state is UNKNOWN and not blocked`() {
        val s = service()
        assertFalse(s.isBlocked())
        val status = s.status()
        assertEquals("UNKNOWN", status.state)
        assertNull(status.downAt)
        assertNull(status.lastReason)
        assertNull(status.lastProbeAt)
    }

    @Test
    fun `recordFailure sets DOWN with downAt and reason until recordSuccess`() {
        val s = service()
        s.recordFailure("connect timed out")

        assertTrue(s.isBlocked())
        val status = s.status()
        assertEquals("DOWN", status.state)
        assertNotNull(status.downAt)
        assertEquals("connect timed out", status.lastReason)

        s.recordSuccess()
        assertFalse(s.isBlocked())
        assertEquals("UP", s.status().state)
        assertNull(s.status().downAt)
        assertNull(s.status().lastReason)
    }

    @Test
    fun `probeNow success transitions to UP and records the probe time`() {
        val s = service { true }
        assertTrue(s.probeNow())
        assertEquals("UP", s.status().state)
        assertNotNull(s.status().lastProbeAt)
        assertFalse(s.isBlocked())
    }

    @Test
    fun `probeNow failure transitions to DOWN and returns false`() {
        val s = service { false }
        assertFalse(s.probeNow())
        assertTrue(s.isBlocked())
        val status = s.status()
        assertEquals("DOWN", status.state)
        assertNotNull(status.downAt)
        assertNotNull(status.lastProbeAt)
    }

    @Test
    fun `probeNow exception transitions to DOWN and records the message`() {
        val s = service { throw IOException("connection reset") }
        assertFalse(s.probeNow())
        assertTrue(s.isBlocked())
        assertEquals("connection reset", s.status().lastReason)
    }

    // ── single flight ──────────────────────────────────────────

    @Test
    fun `concurrent probeNow callers share one probe and return the current result`() {
        val invocations = AtomicInteger(0)
        val probeStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val s = service {
            invocations.incrementAndGet()
            probeStarted.countDown()
            assertTrue(release.await(5, TimeUnit.SECONDS), "probe release timed out")
            true
        }

        val first = Thread { s.probeNow() }
        first.start()
        assertTrue(probeStarted.await(5, TimeUnit.SECONDS), "first probe never started")

        // Second concurrent caller must not issue a second HEAD: it reports the
        // current result (not blocked → true) without touching the probe.
        assertTrue(s.probeNow())
        assertEquals(1, invocations.get(), "only one real probe may run")

        release.countDown()
        first.join(5000)
        assertEquals(1, invocations.get())
        assertEquals("UP", s.status().state)
    }

    // ── probeHttp (real HTTP path) ─────────────────────────────

    @Test
    fun `probeHttp treats 2xx and 3xx as reachable`() {
        // probeHttp 是原始 HTTP 探测（仅更新 lastReason）；状态转换由 probeNow 完成。
        val h = httpHarness(200)
        assertTrue(h.service.probeHttp())

        val redirect = httpHarness(302)
        assertTrue(redirect.service.probeHttp())
    }

    @Test
    fun `probeHttp treats 404 as a DOWN failure with the status reason`() {
        val h = httpHarness(404)
        assertFalse(h.service.probeHttp())
        assertEquals("probe HTTP 404", h.service.status().lastReason)
        // 经 probeNow 落库为 DOWN（模拟一个失败探测完整链路）。
        assertFalse(h.service.probeNow())
        assertEquals("DOWN", h.service.status().state)
    }

    @Test
    fun `probeHttp failure reason comes from the exception message`() {
        val sessionManager = mock(SiteSessionManager::class.java)
        val client = mock(okhttp3.OkHttpClient::class.java)
        `when`(sessionManager.okHttpClient).thenReturn(client)
        val call = mock(Call::class.java)
        `when`(client.newCall(any(Request::class.java))).thenReturn(call)
        `when`(call.timeout()).thenReturn(mock(okio.Timeout::class.java))
        `when`(call.execute()).thenThrow(IOException("UnknownHostException: e-hentai.org"))

        val s = EhAvailabilityService(sessionManager, "https://e-hentai.org", 5000)
        assertFalse(s.probeHttp())
        assertEquals("UnknownHostException: e-hentai.org", s.status().lastReason)
    }
}
