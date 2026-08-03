package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.client.SiteRequestBuilder
import com.hippo.anotherviewer.client.SiteUrl
import com.hippo.anotherviewer.web.service.SiteSessionManager
import okhttp3.HttpUrl
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * W3 (R4-13): transparent Gallery Site proxy for App Tier-2 browsing.
 *
 * The App's Tier-2 interceptor rewrites site-family requests to
 * `{server}/api/v1/site/proxy?url=<encoded site URL>`; this endpoint fetches
 * the site URL through the shared session client (cookies/proxy inherited
 * from [SiteSessionManager.okHttpClient], same browser-fingerprint headers as
 * every other site call via [SiteRequestBuilder]) and passes status,
 * content-type and body straight back through (E2E-6: errors are surfaced,
 * never papered over).
 *
 * The `url` host is whitelisted to the Gallery Site family — anything else is
 * a 400 VALIDATION_ERROR, so the endpoint can never be used as an open proxy.
 */
@RestController
@RequestMapping("/api/v1/site")
class SiteProxyController(private val sessionManager: SiteSessionManager) {

    private val logger = LoggerFactory.getLogger(SiteProxyController::class.java)
    private val okHttpClient get() = sessionManager.okHttpClient

    @GetMapping("/proxy")
    fun proxy(@RequestParam url: String): ResponseEntity<*> {
        val target = HttpUrl.parse(url)
        if (target == null || !isGallerySiteHost(target.host())) {
            return errorEnvelope(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "url must point at a Gallery Site host (gallery.test or *.gallery.test)"
            )
        }

        return try {
            val request = SiteRequestBuilder(target.toString(), SiteUrl.getReferer()).build()
            okHttpClient.newCall(request).execute().use { response ->
                val contentType = response.header(HttpHeaders.CONTENT_TYPE) ?: DEFAULT_CONTENT_TYPE
                val bytes = response.body()?.bytes() ?: ByteArray(0)
                ResponseEntity.status(response.code())
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .body(bytes)
            }
        } catch (e: Exception) {
            logger.warn("Site proxy fetch failed for url={}", url, e)
            errorEnvelope(HttpStatus.BAD_GATEWAY, "BAD_GATEWAY", "gallery site unreachable")
        }
    }

    companion object {
        private const val DEFAULT_CONTENT_TYPE = "application/octet-stream"

        /** Same host predicate as the App's Tier-2 interceptor and the mock-site interceptor. */
        fun isGallerySiteHost(host: String): Boolean =
            host == "gallery.test" || host.endsWith(".gallery.test")
    }
}
