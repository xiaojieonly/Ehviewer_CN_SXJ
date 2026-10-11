package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.entity.Tier2PageEntity
import com.hippo.anotherviewer.web.repository.Tier2PageRepository
import com.hippo.anotherviewer.web.service.Tier2PageService.Tier2PageResult
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.hippo.anotherviewer.web.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.invocation.InvocationOnMock
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.io.IOException
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Pins [Tier2PageService] (Tier-2 EH page cache): fresh-on-success + persist,
 * stale fallback on any origin failure (circuit-breaker DOWN / IOException /
 * upstream 5xx / oversize body) regardless of row age, 404 sentinel when
 * nothing is cached, byte-exact body round trip, normalized cache keys, and
 * the background refresh pass (bounded batch, cutoff + DOWN skip, row kept on
 * failure, capacity trim).
 */
class Tier2PageServiceTest {

    private val URL = "https://e-hentai.org/g/1234/abcdef/"
    private val HTML = "<table class=\"itg\"><tr>gallery row</tr></table>"

    /** Builds a canned upstream response (helper shared by all fake sites). */
    private fun canned(request: Request, code: Int, contentType: String, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .header("Content-Type", contentType)
            .body(body.toByteArray(Charsets.ISO_8859_1).toResponseBody(contentType.toMediaType()))
            .build()

    /** Records the upstream request and answers with a canned response or exception. */
    private class FakeSite(
        private val responder: (Request) -> Response
    ) : Interceptor {
        val lastRequest = AtomicReference<Request?>(null)
        var callCount = 0
            private set

        override fun intercept(chain: Interceptor.Chain): Response {
            callCount++
            lastRequest.set(chain.request())
            return responder(chain.request())
        }
    }

    private lateinit var repository: Tier2PageRepository
    private lateinit var availability: EhAvailabilityService
    private lateinit var site: FakeSite
    private lateinit var service: Tier2PageService

    /** Rows "persisted" by the mocked repository, keyed by URL. */
    private val rows = ConcurrentHashMap<String, Tier2PageEntity>()

    @BeforeEach
    fun setUp() {
        rows.clear()
        repository = mock(Tier2PageRepository::class.java)
        // In-memory PK map so save/findById/delete round-trip like the real JPA repo.
        doAnswer { inv: InvocationOnMock ->
            val entity = inv.getArgument<Tier2PageEntity>(0)
            rows[entity.url] = entity
            entity
        }.`when`(repository).save(any(Tier2PageEntity::class.java))
        doAnswer { inv: InvocationOnMock ->
            Optional.ofNullable(rows[inv.getArgument<String>(0)])
        }.`when`(repository).findById(anyString())
        doAnswer { inv: InvocationOnMock -> rows.remove(inv.getArgument<Tier2PageEntity>(0).url); null }
            .`when`(repository).delete(any(Tier2PageEntity::class.java))

        availability = EhAvailabilityService(
            mock(com.hippo.anotherviewer.web.service.WebProxyManager::class.java),
            "https://e-hentai.org", 5000,
            probe = { true }
        )
        installUpstream { canned(it, 200, "text/html; charset=UTF-8", HTML) }
    }

    /** Installs a fresh fake site + a service wired to a client behind it. */
    private fun installUpstream(
        config: SiteCoreConfigProperties = SiteCoreConfigProperties(),
        responder: (Request) -> Response,
    ) {
        site = FakeSite(responder)
        val sessionManager = mock(SiteSessionManager::class.java)
        `when`(sessionManager.okHttpClient).thenReturn(
            OkHttpClient.Builder().addInterceptor(site).build()
        )
        service = Tier2PageService(
            repository = repository,
            sessionManager = sessionManager,
            availability = availability,
            config = config,
            refreshEnabled = true,
            refreshMaxAgeMs = 6 * 3600_000L,
            refreshBatchSize = 10,
            maxEntries = 2000,
        )
    }

    private fun serviceWith(
        refreshEnabled: Boolean = true,
        refreshMaxAgeMs: Long = 6 * 3600_000L,
        refreshBatchSize: Int = 10,
        maxEntries: Int = 2000,
    ): Tier2PageService =
        Tier2PageService(
            repository = repository,
            sessionManager = mock(SiteSessionManager::class.java).also {
                `when`(it.okHttpClient).thenReturn(OkHttpClient.Builder().addInterceptor(site).build())
            },
            availability = availability,
            config = SiteCoreConfigProperties(),
            refreshEnabled = refreshEnabled,
            refreshMaxAgeMs = refreshMaxAgeMs,
            refreshBatchSize = refreshBatchSize,
            maxEntries = maxEntries,
        )

    /** Seeds a cached row as if a previous fetch had succeeded. */
    private fun seedRow(
        url: String = URL,
        body: String = HTML,
        contentType: String = "text/html; charset=UTF-8",
        fetchedAt: Long = 1_000L,
    ) {
        rows[url] = Tier2PageEntity().apply {
            this.url = url
            this.body = body
            this.contentType = contentType
            this.fetchedAt = fetchedAt
        }
    }

    // ------------------------------------------------------------------
    // fresh on success (+ persist)
    // ------------------------------------------------------------------

    @Test
    fun `success returns fresh body and persists the row`() {
        val result = service.fetchPage(URL)

        assertTrue(result is Tier2PageResult.Served)
        result as Tier2PageResult.Served
        assertEquals(HTML, result.body)
        assertEquals("text/html; charset=UTF-8", result.contentType)
        assertEquals(false, result.stale)

        val row = rows[normalized(URL)]
        assertEquals(HTML, row?.body)
        assertEquals("text/html; charset=UTF-8", row?.contentType)
        assertTrue(row!!.fetchedAt > 0)

        assertEquals(normalized(URL), site.lastRequest.get()!!.url.toString())
    }

    @Test
    fun `body round trips byte-exact through the latin1 cache encoding`() {
        val utf8Html = "<html><title>画廊 &amp; 取阅</title><body>ünïcödé</body></html>"
        installUpstream { req ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(utf8Html.toByteArray(Charsets.UTF_8).toResponseBody("text/html; charset=UTF-8".toMediaType()))
                .build()
        }

        val result = service.fetchPage(URL)

        result as Tier2PageResult.Served
        assertTrue(
            result.body.toByteArray(Charsets.ISO_8859_1).contentEquals(utf8Html.toByteArray(Charsets.UTF_8)),
            "cache encoding must be byte-exact on serve"
        )
        assertTrue(
            rows[normalized(URL)]!!.body.toByteArray(Charsets.ISO_8859_1).contentEquals(utf8Html.toByteArray(Charsets.UTF_8)),
            "cache encoding must be byte-exact at rest"
        )
    }

    @Test
    fun `cache key is normalized - host lowercased and fragment stripped`() {
        service.fetchPage("https://E-Hentai.org/g/1234/abcdef/?p=2#comments")

        assertTrue(rows.containsKey("https://e-hentai.org/g/1234/abcdef/?p=2"), "got rows: ${rows.keys}")
    }

    // ------------------------------------------------------------------
    // stale fallback on origin failure (any age)
    // ------------------------------------------------------------------

    @Test
    fun `origin IOException falls back to the cached row as stale no matter the age`() {
        seedRow(fetchedAt = System.currentTimeMillis() - 100 * 3600_000L) // ancient
        installUpstream { throw IOException("connect timed out") }

        val result = service.fetchPage(URL)

        result as Tier2PageResult.Served
        assertEquals(true, result.stale)
        assertEquals(HTML, result.body)
    }

    @Test
    fun `origin IOException without a cached row is Unavailable`() {
        installUpstream { throw IOException("connect timed out") }

        val result = service.fetchPage(URL)

        assertEquals(Tier2PageResult.Unavailable, result)
    }

    @Test
    fun `oversize origin body counts as origin failure and falls back to stale`() {
        seedRow()
        val tinyCap = SiteCoreConfigProperties().apply { proxy.maxResponseBytes = 16 }
        installUpstream(
            config = tinyCap,
            responder = { canned(it, 200, "text/html; charset=UTF-8", HTML) }, // HTML is > 16 bytes
        )

        val result = service.fetchPage(URL)

        result as Tier2PageResult.Served
        assertEquals(true, result.stale)
        assertEquals(HTML, result.body, "oversize bodies must not replace the cached row")
        assertTrue(site.callCount >= 1)
    }

    @Test
    fun `availability DOWN short-circuits without any upstream call and serves stale`() {
        seedRow()
        availability.recordFailure("probe failed")

        val result = service.fetchPage(URL)

        result as Tier2PageResult.Served
        assertEquals(true, result.stale)
        assertEquals(HTML, result.body)
        assertEquals(0, site.callCount, "DOWN must never issue upstream requests")
    }

    @Test
    fun `availability DOWN without a cached row is Unavailable`() {
        availability.recordFailure("probe failed")

        val result = service.fetchPage(URL)

        assertEquals(Tier2PageResult.Unavailable, result)
        assertEquals(0, site.callCount)
    }

    @Test
    fun `upstream 5xx counts as origin failure and falls back to stale`() {
        seedRow()
        installUpstream { canned(it, 503, "text/html", "service unavailable") }

        val result = service.fetchPage(URL)

        result as Tier2PageResult.Served
        assertEquals(true, result.stale)
        assertEquals(HTML, result.body, "the 5xx body must not replace the cached row")
        assertEquals(HTML, rows[normalized(URL)]!!.body)
    }

    @Test
    fun `upstream 5xx without a cached row is Unavailable`() {
        installUpstream { canned(it, 500, "text/html", "boom") }

        assertEquals(Tier2PageResult.Unavailable, service.fetchPage(URL))
    }

    // ------------------------------------------------------------------
    // non-2xx non-5xx passes through (no cache write)
    // ------------------------------------------------------------------

    @Test
    fun `upstream 404 passes through and does not clobber the cache`() {
        seedRow(body = "old-but-good")
        installUpstream { canned(it, 404, "text/html", "Gallery not found") }

        val result = service.fetchPage(URL)

        assertTrue(result is Tier2PageResult.UpstreamError)
        result as Tier2PageResult.UpstreamError
        assertEquals(404, result.status)
        assertEquals("Gallery not found", result.body)
        assertEquals("old-but-good", rows[normalized(URL)]!!.body)
    }

    // ------------------------------------------------------------------
    // background refresh (scheduled job, invoked directly in tests)
    // ------------------------------------------------------------------

    private fun stubRefreshCandidates(vararg entities: Tier2PageEntity) {
        `when`(
            repository.findByFetchedAtLessThanOrderByFetchedAtAsc(anyLong(), any(Pageable::class.java))
        ).thenAnswer { entities.toList() }
    }

    private fun row(url: String, fetchedAt: Long) = Tier2PageEntity().apply {
        this.url = url
        body = "cached $url"
        contentType = "text/html"
        this.fetchedAt = fetchedAt
    }

    @Test
    fun `refresh re-fetches expired rows and re-persists them`() {
        val old1 = row("https://e-hentai.org/?f_search=a&page=1", fetchedAt = 100L)
        val old2 = row("https://exhentai.org/g/9/xyz/", fetchedAt = 200L)
        stubRefreshCandidates(old1, old2)
        val before = System.currentTimeMillis()

        service.refreshExpiredEntries()

        assertEquals(2, site.callCount, "each candidate must be re-fetched once")
        assertEquals(HTML, rows["https://e-hentai.org/?f_search=a&page=1"]!!.body)
        assertEquals(HTML, rows["https://exhentai.org/g/9/xyz/"]!!.body)
        assertTrue(rows["https://e-hentai.org/?f_search=a&page=1"]!!.fetchedAt >= before)
    }

    @Test
    fun `refresh asks only for rows older than now minus max-age`() {
        var capturedCutoff = 0L
        `when`(
            repository.findByFetchedAtLessThanOrderByFetchedAtAsc(anyLong(), any(Pageable::class.java))
        ).thenAnswer { inv ->
            capturedCutoff = inv.getArgument(0)
            emptyList<Tier2PageEntity>()
        }
        val before = System.currentTimeMillis()

        service.refreshExpiredEntries()

        val maxAge = 6 * 3600_000L
        assertTrue(
            capturedCutoff in (before - maxAge - 5_000)..(System.currentTimeMillis() - maxAge + 5_000),
            "cutoff must be now - max-age, got $capturedCutoff",
        )
    }

    @Test
    fun `refresh batch size bounds the per-run work`() {
        var capturedPageable: Pageable? = null
        `when`(
            repository.findByFetchedAtLessThanOrderByFetchedAtAsc(anyLong(), any(Pageable::class.java))
        ).thenAnswer { inv ->
            capturedPageable = inv.getArgument(1)
            emptyList<Tier2PageEntity>()
        }

        service.refreshExpiredEntries()

        assertEquals(10, capturedPageable!!.pageSize)
    }

    @Test
    fun `refresh keeps the cached row when the origin still fails`() {
        seedRow(body = "cached $URL")
        stubRefreshCandidates(row(URL, fetchedAt = 100L))
        installUpstream { throw IOException("still down") }

        service.refreshExpiredEntries()

        assertEquals(1, site.callCount)
        assertEquals("cached $URL", rows[normalized(URL)]!!.body, "failed refresh must not clobber the cached body")
    }

    @Test
    fun `refresh skips entirely while availability is DOWN`() {
        seedRow(body = "cached $URL")
        stubRefreshCandidates(row(URL, fetchedAt = 100L))
        availability.recordFailure("probe failed")

        service.refreshExpiredEntries()

        assertEquals(0, site.callCount)
        assertEquals("cached $URL", rows[normalized(URL)]!!.body)
    }

    @Test
    fun `refresh disabled does nothing`() {
        stubRefreshCandidates(row(URL, fetchedAt = 100L))
        val disabled = serviceWith(refreshEnabled = false)

        disabled.refreshExpiredEntries()

        assertEquals(0, site.callCount)
    }

    @Test
    fun `refresh deletes rows whose stored url is no longer parseable`() {
        val dead = row("not a url", fetchedAt = 100L)
        stubRefreshCandidates(dead)

        service.refreshExpiredEntries()

        verify(repository).delete(dead)
        assertNull(rows["not a url"])
    }

    // ------------------------------------------------------------------
    // capacity trim
    // ------------------------------------------------------------------

    @Test
    fun `trim deletes the oldest rows beyond maxEntries after a refresh pass`() {
        val trimmed = serviceWith(maxEntries = 2)
        val a = row("https://e-hentai.org/a", fetchedAt = 1L)
        val b = row("https://e-hentai.org/b", fetchedAt = 2L)
        val c = row("https://e-hentai.org/c", fetchedAt = 3L)
        listOf(a, b, c).forEach { rows[it.url] = it }
        `when`(repository.count()).thenReturn(3L)
        // excess = total - maxEntries = 1 → the single oldest row is the trim candidate.
        `when`(repository.findAll(PageRequest.of(0, 1, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "fetchedAt"))))
            .thenReturn(PageImpl(listOf(a)))

        trimmed.refreshExpiredEntries()

        verify(repository).deleteAllInBatch(listOf(a))
    }

    @Test
    fun `trim is skipped when under the cap`() {
        `when`(repository.count()).thenReturn(2L)

        service.refreshExpiredEntries()

        verify(repository, never()).deleteAllInBatch(any())
    }

    /** Normalization is internal; mirror it here for row-key assertions. */
    private fun normalized(url: String): String = url.toHttpUrlOrNull()!!.newBuilder().fragment(null).build().toString()
}
