package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.hippo.anotherviewer.web.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import com.hippo.anotherviewer.web.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deterministic (no network) tests for [PrefetchService]: cached pages are
 * skipped, error chains stop early, the executor/semaphore bounds hold, and
 * prefetching never goes past the known page count.
 */
class PrefetchServiceTest {

    private lateinit var galleryLookup: GalleryLookupService
    private lateinit var imageCache: ImageCacheService
    private lateinit var sessionManager: SiteSessionManager
    private lateinit var client: OkHttpClient
    private lateinit var call: Call
    private lateinit var response: Response
    private lateinit var config: SiteCoreConfigProperties
    private lateinit var service: PrefetchService
    private lateinit var availability: EhAvailabilityService

    private val gid = 12345L
    private val token = "0123456789abcdef"
    private val pageCount = 10

    @BeforeEach
    fun setUp() {
        galleryLookup = mock(GalleryLookupService::class.java)
        imageCache = mock(ImageCacheService::class.java)
        sessionManager = mock(SiteSessionManager::class.java)
        client = mock(OkHttpClient::class.java)
        call = mock(Call::class.java)
        response = mock(Response::class.java)

        `when`(sessionManager.okHttpClient).thenReturn(client)
        `when`(client.newCall(any(Request::class.java))).thenReturn(call)
        `when`(call.execute()).thenReturn(response)
        `when`(response.code).thenReturn(200)
        `when`(response.isSuccessful).thenReturn(true)
        // OkHttp 4.x: Response.header(name) is a Kotlin default-arg method that
        // compiles to header(name, null); a single anyString() matcher no longer
        // matches the 2-arg JVM signature, so stub the concrete header instead.
        `when`(response.header("Content-Type")).thenReturn("image/jpeg")
        `when`(response.body).thenAnswer { ByteArray(64).toResponseBody("image/jpeg".toMediaType()) }

        `when`(galleryLookup.findToken(gid)).thenReturn(token)
        `when`(galleryLookup.fetchPageCount(gid, token)).thenReturn(pageCount)
        `when`(galleryLookup.fetchImageUrl(anyLong(), anyString(), anyInt()))
            .thenReturn("https://e-hentai.org/example/image.jpg")

        config = SiteCoreConfigProperties()
        config.reader.prefetchPages = 3
        availability = EhAvailabilityService(sessionManager, "https://e-hentai.org", 5000)
        service = PrefetchService(galleryLookup, imageCache, sessionManager, config, availability)
    }

    // ── happy path / cache skipping ─────────────────────────────

    @Test
    fun `prefetches the next pages and writes them into the cache`() {
        service.prefetchAround(gid, 2) // 0-based page 2 → targets 3, 4, 5

        verify(galleryLookup, timeout(5000)).fetchImageUrl(eq(gid), eq(token), eq(4))
        verify(galleryLookup, timeout(5000)).fetchImageUrl(eq(gid), eq(token), eq(5))
        verify(galleryLookup, timeout(5000)).fetchImageUrl(eq(gid), eq(token), eq(6))
        verify(imageCache, timeout(5000)).cacheImageByKey(eq(gid), eq(3), any(ByteArray::class.java), eq("jpg"))
        verify(imageCache, timeout(5000)).cacheImageByKey(eq(gid), eq(4), any(ByteArray::class.java), eq("jpg"))
        verify(imageCache, timeout(5000)).cacheImageByKey(eq(gid), eq(5), any(ByteArray::class.java), eq("jpg"))
    }

    @Test
    fun `skips pages already cached`() {
        `when`(imageCache.getCachedImageByKey(gid, 4)).thenReturn(ByteArray(16))

        service.prefetchAround(gid, 2) // targets 3, 4, 5 — page 4 is cached

        verify(galleryLookup, timeout(5000)).fetchImageUrl(eq(gid), eq(token), eq(4))
        verify(galleryLookup, timeout(5000)).fetchImageUrl(eq(gid), eq(token), eq(6))
        verify(galleryLookup, never()).fetchImageUrl(eq(gid), eq(token), eq(5))
        verify(imageCache, timeout(5000)).cacheImageByKey(eq(gid), eq(3), any(ByteArray::class.java), eq("jpg"))
        verify(imageCache, timeout(5000)).cacheImageByKey(eq(gid), eq(5), any(ByteArray::class.java), eq("jpg"))
        verify(imageCache, never()).cacheImageByKey(eq(gid), eq(4), any(ByteArray::class.java), eq("jpg"))
    }

    // ── error handling ──────────────────────────────────────────

    @Test
    fun `stops prefetch chain when page count lookup fails`() {
        `when`(galleryLookup.fetchPageCount(eq(gid), eq(token))).thenThrow(RuntimeException("detail parse failed"))

        service.prefetchAround(gid, 0)

        verify(galleryLookup, timeout(5000)).fetchPageCount(eq(gid), eq(token))
        verify(galleryLookup, never()).fetchImageUrl(anyLong(), anyString(), anyInt())
        verify(imageCache, never()).cacheImageByKey(anyLong(), anyInt(), any(ByteArray::class.java), anyString())
    }

    @Test
    fun `stops prefetch chain when image url lookup fails`() {
        `when`(galleryLookup.fetchImageUrl(eq(gid), eq(token), eq(4)))
            .thenThrow(RuntimeException("upstream failed"))

        service.prefetchAround(gid, 0) // targets 1, 2, 3 → third URL lookup fails

        verify(galleryLookup, timeout(5000)).fetchImageUrl(eq(gid), eq(token), eq(2))
        verify(galleryLookup, timeout(5000)).fetchImageUrl(eq(gid), eq(token), eq(3))
        // page 4 lookup throws inside the call (invocation recorded); the chain
        // must stop there and never touch page 5.
        verify(galleryLookup, timeout(5000)).fetchImageUrl(eq(gid), eq(token), eq(4))
        verify(galleryLookup, never()).fetchImageUrl(eq(gid), eq(token), eq(5))
        verify(imageCache, timeout(5000)).cacheImageByKey(eq(gid), eq(1), any(ByteArray::class.java), eq("jpg"))
        verify(imageCache, timeout(5000)).cacheImageByKey(eq(gid), eq(2), any(ByteArray::class.java), eq("jpg"))
        verify(imageCache, never()).cacheImageByKey(eq(gid), eq(3), any(ByteArray::class.java), eq("jpg"))
    }

    @Test
    fun `stops prefetch chain on network error`() {
        `when`(call.execute()).thenThrow(RuntimeException("connection reset"))

        service.prefetchAround(gid, 0) // first download fails → no caching, no further pages

        verify(galleryLookup, timeout(5000)).fetchImageUrl(eq(gid), eq(token), eq(2))
        verify(galleryLookup, never()).fetchImageUrl(eq(gid), eq(token), eq(3))
        verify(imageCache, never()).cacheImageByKey(anyLong(), anyInt(), any(ByteArray::class.java), anyString())
    }

    @Test
    fun `stops prefetch chain on 509 response`() {
        `when`(response.code).thenReturn(509)

        service.prefetchAround(gid, 0)

        verify(galleryLookup, timeout(5000)).fetchImageUrl(eq(gid), eq(token), eq(2))
        verify(galleryLookup, never()).fetchImageUrl(eq(gid), eq(token), eq(3))
        verify(imageCache, never()).cacheImageByKey(anyLong(), anyInt(), any(ByteArray::class.java), anyString())
    }

    @Test
    fun `stops prefetch chain on unsuccessful response`() {
        `when`(response.isSuccessful).thenReturn(false)

        service.prefetchAround(gid, 0)

        verify(galleryLookup, timeout(5000)).fetchImageUrl(eq(gid), eq(token), eq(2))
        verify(galleryLookup, never()).fetchImageUrl(eq(gid), eq(token), eq(3))
        verify(imageCache, never()).cacheImageByKey(anyLong(), anyInt(), any(ByteArray::class.java), anyString())
    }

    // ── page count boundary ─────────────────────────────────────

    @Test
    fun `does not prefetch beyond the known page count`() {
        service.prefetchAround(gid, 8) // pageCount 10 → only target 9 remains

        verify(galleryLookup, timeout(5000)).fetchImageUrl(eq(gid), eq(token), eq(10))
        verify(galleryLookup, never()).fetchImageUrl(eq(gid), eq(token), eq(11))
        verify(imageCache, timeout(5000)).cacheImageByKey(eq(gid), eq(9), any(ByteArray::class.java), eq("jpg"))
    }

    @Test
    fun `does not prefetch when already on the last page`() {
        service.prefetchAround(gid, 9) // last page index (pageCount 10)

        Thread.sleep(300)
        verify(galleryLookup, never()).fetchImageUrl(anyLong(), anyString(), anyInt())
        verify(imageCache, never()).cacheImageByKey(anyLong(), anyInt(), any(ByteArray::class.java), anyString())
    }

    // ── guards / bounds ─────────────────────────────────────────

    @Test
    fun `does nothing when the gallery is unknown`() {
        `when`(galleryLookup.findToken(anyLong())).thenReturn(null)

        service.prefetchAround(gid, 0)

        Thread.sleep(300)
        verify(galleryLookup, never()).fetchPageCount(anyLong(), anyString())
        verify(galleryLookup, never()).fetchImageUrl(anyLong(), anyString(), anyInt())
        verify(imageCache, never()).cacheImageByKey(anyLong(), anyInt(), any(ByteArray::class.java), anyString())
    }

    @Test
    fun `prefetch is disabled when prefetch pages is zero`() {
        config.reader.prefetchPages = 0

        service.prefetchAround(gid, 0)

        verifyNoInteractions(galleryLookup, imageCache)
    }

    @Test
    fun `prefetch is a no-op while EH is DOWN`() {
        availability.recordFailure("connect timed out")

        service.prefetchAround(gid, 0)

        Thread.sleep(300)
        verify(galleryLookup, never()).findToken(anyLong())
        verify(galleryLookup, never()).fetchPageCount(anyLong(), anyString())
        verify(galleryLookup, never()).fetchImageUrl(anyLong(), anyString(), anyInt())
        verify(imageCache, never()).cacheImageByKey(anyLong(), anyInt(), any(ByteArray::class.java), anyString())
    }

    @Test
    fun `prefetch is bounded by the executor thread pool`() {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val release = CountDownLatch(1)
        `when`(galleryLookup.fetchImageUrl(anyLong(), anyString(), anyInt())).thenAnswer {
            val current = active.incrementAndGet()
            maxActive.accumulateAndGet(current) { a, b -> maxOf(a, b) }
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS))
            } finally {
                active.decrementAndGet()
            }
            "https://e-hentai.org/example/image.jpg"
        }

        service.prefetchAround(gid, 0)
        service.prefetchAround(gid, 0) // 2 chains × 3 pages = 6 fetches

        verify(galleryLookup, timeout(5000).times(2)).fetchImageUrl(anyLong(), anyString(), anyInt())
        assertEquals(2, maxActive.get())

        release.countDown()
        verify(galleryLookup, timeout(10000).times(6)).fetchImageUrl(anyLong(), anyString(), anyInt())
    }

    @Test
    fun `drops prefetch submissions beyond the in-flight limit`() {
        val release = CountDownLatch(1)
        `when`(galleryLookup.fetchImageUrl(anyLong(), anyString(), anyInt())).thenAnswer {
            assertTrue(release.await(5, TimeUnit.SECONDS))
            "https://e-hentai.org/example/image.jpg"
        }

        repeat(9) { service.prefetchAround(gid, 0) } // 8 permits → 9th submission dropped

        verify(galleryLookup, timeout(5000).times(2)).fetchImageUrl(anyLong(), anyString(), anyInt())
        release.countDown()
        verify(galleryLookup, timeout(10000).times(24)).fetchImageUrl(anyLong(), anyString(), anyInt())
        verify(imageCache, timeout(10000).times(24)).cacheImageByKey(anyLong(), anyInt(), any(ByteArray::class.java), anyString())
    }
}
