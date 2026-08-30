package com.hippo.anotherviewer.web.service

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.IOException
import java.net.InetSocketAddress
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
        EhAvailabilityService("https://e-hentai.org", 5000, probe = probe)

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

    // ── probeHttp (real HTTP path via JDK HttpServer) ─────────

    private fun echoServer(status: Int): Pair<HttpServer, String> {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange: HttpExchange ->
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
        }
        server.start()
        return server to "http://127.0.0.1:${server.address.port}/"
    }

    @AfterEach
    fun stopServers() {
        servers.forEach { it.stop(0) }
        servers.clear()
    }

    private val servers = mutableListOf<HttpServer>()

    @Test
    fun `probeHttp treats 2xx and 3xx as reachable`() {
        // probeHttp 是原始 HTTP 探测（仅更新 lastReason）；状态转换由 probeNow 完成。
        val (s2xx, u2) = echoServer(200)
        servers.add(s2xx)
        val ok = EhAvailabilityService(u2, 5000)
        assertTrue(ok.probeHttp())

        val (s3xx, u3) = echoServer(302)
        servers.add(s3xx)
        val redirect = EhAvailabilityService(u3, 5000)
        assertTrue(redirect.probeHttp())
    }

    @Test
    fun `probeHttp treats 404 as a DOWN failure with the status reason`() {
        val (server, url) = echoServer(404)
        servers.add(server)
        val h = EhAvailabilityService(url, 5000)
        assertFalse(h.probeHttp())
        assertEquals("probe HTTP 404", h.status().lastReason)
        // 经 probeNow 落库为 DOWN（模拟一个失败探测完整链路）。
        assertFalse(h.probeNow())
        assertEquals("DOWN", h.status().state)
    }

    @Test
    fun `probeHttp failure reason comes from the exception message`() {
        // 不可解析的 host（DNS 失败）→ 异常路径 → lastReason 来自 exception。
        val s = EhAvailabilityService("https://eh-unreachable-zzz.invalid", 2000)
        assertFalse(s.probeHttp())
        assertTrue(s.status().lastReason!!.isNotBlank())
    }
}
