package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.client.SiteRequestBuilder
import com.hippo.anotherviewer.client.SiteUrl
import com.hippo.anotherviewer.web.service.SiteSessionManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
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
    fun proxy(@RequestParam url: String): ResponseEntity<*> = proxyRequest(url, null, null)

    /**
     * POST form of the transparent proxy: the App's EH API calls (api.php,
     * `{"method":"showpage",...}` JSON) arrive here. The body and content-type
     * are forwarded verbatim; the site Referer/Origin headers are rebuilt like
     * the GET path so api.php accepts the request.
     */
    @PostMapping("/proxy")
    fun proxyPost(
        @RequestParam url: String,
        @RequestBody(required = false) body: ByteArray?,
        @org.springframework.web.bind.annotation.RequestHeader(HttpHeaders.CONTENT_TYPE) contentType: String?,
    ): ResponseEntity<*> = proxyRequest(url, body, contentType)

    private fun proxyRequest(url: String, body: ByteArray?, contentType: String?): ResponseEntity<*> {
        val target = url.toHttpUrlOrNull()
        if (target == null || !isGallerySiteHost(target.host)) {
            return errorEnvelope(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "url must point at a Gallery Site host (e-hentai.org, exhentai.org, ehgt.org, lofi.e-hentai.org or their subdomains)"
            )
        }

        return try {
            // Referer must be slash-terminated: EH strictly validates the
            // origin shape on thumbnail-origin hosts (s.exhentai.org rejects
            // the bare SiteUrl.REFERER_* form with 403; see ImageProxyController).
            val builder = SiteRequestBuilder(target.toString(), SiteUrl.getReferer() + "/", SiteUrl.getOrigin())
            if (body != null && body.isNotEmpty()) {
                builder.post(
                    body.toRequestBody(
                        (contentType ?: "application/json; charset=utf-8").toMediaTypeOrNull()
                    )
                )
            }
            okHttpClient.newCall(builder.build()).execute().use { response ->
                val respContentType = response.header(HttpHeaders.CONTENT_TYPE) ?: DEFAULT_CONTENT_TYPE
                val bytes = response.body?.bytes() ?: ByteArray(0)
                ResponseEntity.status(response.code)
                    .header(HttpHeaders.CONTENT_TYPE, respContentType)
                    .body(bytes)
            }
        } catch (e: Exception) {
            logger.warn("Site proxy fetch failed for url={}", url, e)
            errorEnvelope(HttpStatus.BAD_GATEWAY, "BAD_GATEWAY", "gallery site unreachable")
        }
    }

    companion object {
        private const val DEFAULT_CONTENT_TYPE = "application/octet-stream"

        /** Same host predicate as the App's Tier-2 interceptor. */
        fun isGallerySiteHost(host: String): Boolean =
            host == "e-hentai.org" || host == "exhentai.org" ||
            host == "lofi.e-hentai.org" || host == "ehgt.org" ||
            host.endsWith(".e-hentai.org") || host.endsWith(".exhentai.org") ||
            host.endsWith(".ehgt.org")
    }
}
