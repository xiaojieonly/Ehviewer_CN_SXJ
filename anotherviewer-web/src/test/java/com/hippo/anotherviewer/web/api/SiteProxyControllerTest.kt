package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.service.SiteSessionManager
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * Pins the W3 R4-13 site transparent proxy: host whitelist (400
 * VALIDATION_ERROR for everything outside gallery.test / *.gallery.test),
 * status/content-type/body pass-through for whitelisted fetches, and the
 * E2E-6 failure path (unreachable site -> 502 error envelope, never
 * fabricated content).
 */
class SiteProxyControllerTest {

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

        fun canned(request: Request, code: Int, contentType: String, body: String): Response =
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("OK")
                .header("Content-Type", contentType)
                .body(ResponseBody.create(MediaType.parse(contentType), body))
                .build()
    }

    private lateinit var sessionManager: SiteSessionManager
    private lateinit var mockMvc: MockMvc
    private lateinit var site: FakeSite

    private fun setUpClient(interceptor: FakeSite) {
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        `when`(sessionManager.okHttpClient).thenReturn(client)
    }

    @BeforeEach
    fun setUp() {
        sessionManager = mock(SiteSessionManager::class.java)
        mockMvc = MockMvcBuilders.standaloneSetup(SiteProxyController(sessionManager))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    // ------------------------------------------------------------------
    // Whitelist
    // ------------------------------------------------------------------

    @Test
    fun `non-site host is rejected with 400 VALIDATION_ERROR and never fetched`() {
        site = FakeSite { canned(it, 200, "text/html", "should never be served") }
        setUpClient(site)

        mockMvc.perform(get("/api/v1/site/proxy").param("url", "https://evil.example.com/steal"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.traceId").exists())

        assertEquals(0, site.callCount, "rejected urls must not reach the client")
    }

    @Test
    fun `malformed url is rejected with 400 VALIDATION_ERROR`() {
        site = FakeSite { canned(it, 200, "text/html", "") }
        setUpClient(site)

        mockMvc.perform(get("/api/v1/site/proxy").param("url", "not a url"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))

        assertEquals(0, site.callCount)
    }

    @Test
    fun `missing url param yields the uniform 400 envelope`() {
        site = FakeSite { canned(it, 200, "text/html", "") }
        setUpClient(site)

        mockMvc.perform(get("/api/v1/site/proxy"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
    }

    @Test
    fun `lookalike hosts are not whitelisted`() {
        site = FakeSite { canned(it, 200, "text/html", "") }
        setUpClient(site)

        for (bad in listOf("https://gallery.test.evil.com/", "https://notgallery.test/")) {
            mockMvc.perform(get("/api/v1/site/proxy").param("url", bad))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        }
        assertEquals(0, site.callCount)
    }

    // ------------------------------------------------------------------
    // Pass-through
    // ------------------------------------------------------------------

    @Test
    fun `whitelisted fetch passes status content-type and body through`() {
        val html = "<table class=\"itg\"><tr>gallery row</tr></table>"
        site = FakeSite { canned(it, 200, "text/html; charset=UTF-8", html) }
        setUpClient(site)

        mockMvc.perform(
            get("/api/v1/site/proxy").param("url", "https://gallery.test/?f_search=alpha&page=2")
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "text/html; charset=UTF-8"))
            .andExpect(content().string(html))

        assertEquals(
            "https://gallery.test/?f_search=alpha&page=2",
            site.lastRequest.get()!!.url().toString(),
            "the proxy must fetch exactly the url it was given"
        )
    }

    @Test
    fun `site subdomains are whitelisted`() {
        site = FakeSite { canned(it, 200, "image/png", "pngbytes") }
        setUpClient(site)

        mockMvc.perform(get("/api/v1/site/proxy").param("url", "https://lofi.gallery.test/index.php"))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "image/png"))

        assertEquals("https://lofi.gallery.test/index.php", site.lastRequest.get()!!.url().toString())
    }

    @Test
    fun `binary body passes through byte-identical`() {
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x00, 0xFF.toByte())
        site = FakeSite {
            Response.Builder()
                .request(it)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Content-Type", "image/png")
                .body(ResponseBody.create(MediaType.parse("image/png"), bytes))
                .build()
        }
        setUpClient(site)

        val result = mockMvc.perform(
            get("/api/v1/site/proxy").param("url", "https://gallery.test/t/1001/cover.jpg")
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "image/png"))
            .andReturn()

        assertArrayEquals(bytes, result.response.contentAsByteArray)
    }

    @Test
    fun `site error statuses pass through with the body`() {
        site = FakeSite { canned(it, 404, "text/html", "<div class=\"d\">Gallery not found</div>") }
        setUpClient(site)

        mockMvc.perform(get("/api/v1/site/proxy").param("url", "https://gallery.test/g/9999/zzzz/"))
            .andExpect(status().isNotFound)
            .andExpect(content().string("<div class=\"d\">Gallery not found</div>"))
    }

    // ------------------------------------------------------------------
    // E2E-6: unreachable site -> 502 envelope, no fabricated content
    // ------------------------------------------------------------------

    @Test
    fun `unreachable site yields 502 error envelope`() {
        site = FakeSite { throw IOException("connect timed out") }
        setUpClient(site)

        mockMvc.perform(get("/api/v1/site/proxy").param("url", "https://gallery.test/?f_search=alpha"))
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.error.code").value("BAD_GATEWAY"))
            .andExpect(jsonPath("$.error.status").value(502))
            .andExpect(jsonPath("$.error.traceId").exists())
    }
}
