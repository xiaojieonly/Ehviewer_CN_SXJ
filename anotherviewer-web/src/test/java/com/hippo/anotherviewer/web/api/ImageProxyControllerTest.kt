package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.argThatK
import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.eq
import com.hippo.anotherviewer.web.service.DownloadDirIndex
import com.hippo.anotherviewer.web.service.DownloadService
import com.hippo.anotherviewer.web.service.EhAvailabilityService
import com.hippo.anotherviewer.web.service.GalleryLookupService
import com.hippo.anotherviewer.web.service.ImageCacheService
import com.hippo.anotherviewer.web.service.InMemoryJobStore
import com.hippo.anotherviewer.web.service.JobService
import com.hippo.anotherviewer.web.service.PrefetchService
import com.hippo.anotherviewer.web.service.SiteSessionManager
import org.springframework.context.ApplicationEventPublisher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

class ImageProxyControllerTest {

    /** Records the upstream request; answers with a canned response or throws. */
    private class FakeSite(private val responder: (Request) -> Response) : Interceptor {
        val lastRequest = AtomicReference<Request?>(null)
        var callCount = 0
            private set

        override fun intercept(chain: Interceptor.Chain): Response {
            callCount++
            lastRequest.set(chain.request())
            return responder(chain.request())
        }
    }

    private fun canned(request: Request, code: Int, contentType: String, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .header("Content-Type", contentType)
            .body(body.toResponseBody(contentType.toMediaType()))
            .build()

    private lateinit var imageCacheService: ImageCacheService
    private lateinit var sessionManager: SiteSessionManager
    private lateinit var mockMvc: MockMvc
    private lateinit var site: FakeSite
    private val config = SiteCoreConfigProperties()
    private lateinit var availability: EhAvailabilityService

    private fun setUpClient(interceptor: FakeSite) {
        `when`(sessionManager.okHttpClient)
            .thenReturn(OkHttpClient.Builder().addInterceptor(interceptor).build())
    }

    private fun probeFalseService(): EhAvailabilityService =
        EhAvailabilityService(mock(com.hippo.anotherviewer.web.service.WebProxyManager::class.java), "https://e-hentai.org", 5000, probe = { false })

    @BeforeEach
    fun setUp() {
        imageCacheService = mock(ImageCacheService::class.java)
        val galleryLookupService = mock(GalleryLookupService::class.java)
        sessionManager = mock(SiteSessionManager::class.java)
        val prefetchService = mock(PrefetchService::class.java)
        availability = probeFalseService().apply { recordSuccess() }
        // P10 required 注入：测试显式提供真实 config 与轻量 JobService 替身。
        mockMvc = MockMvcBuilders.standaloneSetup(
            ImageProxyController(
                imageCacheService, galleryLookupService, sessionManager, prefetchService,
                mock(DownloadService::class.java),
                config,
                JobService(InMemoryJobStore(), ApplicationEventPublisher {}),
                availability,
                mock(DownloadDirIndex::class.java),
            )
        )
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    // ------------------------------------------------------------------
    // Cache-hit path (legacy behavior kept intact)
    // ------------------------------------------------------------------

    @Test
    fun `proxyImage serves a cache hit without fetching`() {
        val url = "https://e-hentai.org/t/1001/cover.jpg"
        `when`(imageCacheService.getCachedImage(url)).thenReturn(byteArrayOf(1, 2, 3))
        site = FakeSite { canned(it, 200, "image/jpeg", "") }
        setUpClient(site)

        mockMvc.perform(get("/api/v1/image/proxy").param("url", url))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "image/jpeg"))

        verify(imageCacheService, never()).cacheImage(any(), any())
        org.junit.jupiter.api.Assertions.assertEquals(0, site.callCount)
    }

    // ------------------------------------------------------------------
    // W3 R4-13 fetch-on-miss
    // ------------------------------------------------------------------

    @Test
    fun `proxyImage fetch-on-miss backfills the cache and passes the content-type through`() {
        val url = "https://e-hentai.org/t/1001/cover.jpg"
        `when`(imageCacheService.getCachedImage(url)).thenReturn(null)
        site = FakeSite { canned(it, 200, "image/png", "PNGBYTES") }
        setUpClient(site)

        mockMvc.perform(get("/api/v1/image/proxy").param("url", url))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(content().string("PNGBYTES"))

        org.junit.jupiter.api.Assertions.assertEquals(url, site.lastRequest.get()!!.url.toString())
        verify(imageCacheService).cacheImage(
            eq(url),
            argThatK<ByteArray> { it.contentEquals("PNGBYTES".toByteArray()) }
        )
    }

    @Test
    // MASTER-2026-08-22 S2：上游响应超限 → 502 UPSTREAM_TOO_LARGE，不落缓存。
    fun `proxyImage fetch-on-miss aborts with 502 envelope when upstream exceeds size cap`() {
        val url = "https://e-hentai.org/t/1002/huge.jpg"
        config.proxy.maxResponseBytes = 4
        `when`(imageCacheService.getCachedImage(url)).thenReturn(null)
        site = FakeSite { canned(it, 200, "image/jpeg", "TOO-LONG-FOR-CAP") }
        setUpClient(site)

        mockMvc.perform(get("/api/v1/image/proxy").param("url", url))
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.error.code").value("UPSTREAM_TOO_LARGE"))
            .andExpect(jsonPath("$.error.traceId").exists())
        verify(imageCacheService, never()).cacheImage(eq(url), argThatK<ByteArray> { true })
    }

    @Test
    fun `proxyImage never fetches non-whitelisted urls and keeps the legacy 404`() {        val url = "https://evil.example.com/img.jpg"
        `when`(imageCacheService.getCachedImage(url)).thenReturn(null)
        site = FakeSite { canned(it, 200, "image/jpeg", "stolen") }
        setUpClient(site)

        mockMvc.perform(get("/api/v1/image/proxy").param("url", url))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.error.traceId").exists())

        org.junit.jupiter.api.Assertions.assertEquals(0, site.callCount, "non-whitelisted urls must never be fetched")
        verify(imageCacheService, never()).cacheImage(any(), any())
    }

    @Test
    fun `proxyImage returns the 404 envelope when the site is unreachable`() {
        val url = "https://e-hentai.org/t/1001/cover.jpg"
        `when`(imageCacheService.getCachedImage(url)).thenReturn(null)
        site = FakeSite { throw IOException("connect timed out") }
        setUpClient(site)

        mockMvc.perform(get("/api/v1/image/proxy").param("url", url))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.status").value(404))
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.error.traceId").exists())

        verify(imageCacheService, never()).cacheImage(any(), any())
    }

    @Test
    fun `proxyImage returns the 404 envelope when the site answers with an error status`() {
        val url = "https://e-hentai.org/t/9999/cover.jpg"
        `when`(imageCacheService.getCachedImage(url)).thenReturn(null)
        site = FakeSite { canned(it, 404, "text/html", "not here") }
        setUpClient(site)

        mockMvc.perform(get("/api/v1/image/proxy").param("url", url))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))

        verify(imageCacheService, never()).cacheImage(any(), any())
    }

    // ------------------------------------------------------------------

    @Test
    fun `streamGalleryImage rejects a negative page with 404 uniform envelope`() {
        mockMvc.perform(get("/api/v1/image/123/-1"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.status").value(404))
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.error.traceId").exists())
    }

    // ------------------------------------------------------------------
    // EH DOWN 熔断：cache/pushed 命中之后、上游 fetch 之前秒回 EH_UNAVAILABLE
    // ------------------------------------------------------------------

    @Test
    fun `proxyImage miss returns 404 EH_UNAVAILABLE without any upstream request when blocked`() {
        val url = "https://e-hentai.org/t/1001/cover.jpg"
        `when`(imageCacheService.getCachedImage(url)).thenReturn(null)
        site = FakeSite { canned(it, 200, "image/jpeg", "") }
        setUpClient(site)
        availability.recordFailure("connect timed out")

        mockMvc.perform(get("/api/v1/image/proxy").param("url", url))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("EH_UNAVAILABLE"))
            .andExpect(jsonPath("$.error.message").value("EH 平台当前不可达，仅显示本地内容"))
            .andExpect(jsonPath("$.error.traceId").exists())

        org.junit.jupiter.api.Assertions.assertEquals(0, site.callCount, "no upstream fetch while DOWN")
        verify(imageCacheService, never()).cacheImage(any(), any())
    }

    @Test
    fun `proxyImage cache hit is still served while DOWN`() {
        val url = "https://e-hentai.org/t/1001/cover.jpg"
        `when`(imageCacheService.getCachedImage(url)).thenReturn(byteArrayOf(1, 2, 3))
        availability.recordFailure("connect timed out")

        mockMvc.perform(get("/api/v1/image/proxy").param("url", url))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "image/jpeg"))
    }

    @Test
    fun `streamGalleryImage returns 404 EH_UNAVAILABLE on cache miss when blocked`() {
        `when`(imageCacheService.findCachedPageFile(anyLong(), anyInt())).thenReturn(null)
        `when`(imageCacheService.getCachedImageByKey(anyLong(), anyInt())).thenReturn(null)
        availability.recordFailure("connect timed out")

        mockMvc.perform(get("/api/v1/image/555/0"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("EH_UNAVAILABLE"))
            .andExpect(jsonPath("$.error.message").value("EH 平台当前不可达，仅显示本地内容"))
    }
}
