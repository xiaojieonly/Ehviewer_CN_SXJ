package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.service.EhUnavailableException
import com.hippo.anotherviewer.web.service.Tier2PageService
import com.hippo.anotherviewer.web.service.Tier2PageService.Tier2PageResult
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Tier-2 page API for the App: `GET /api/v1/tier2/page?url=<urlencoded EH page
 * URL>` with `Authorization: Bearer <device token>` (same filter chain as the
 * sync endpoints — the /api route tree is authenticated() in
 * [com.hippo.anotherviewer.web.config.SecurityConfig]).
 *
 * Response contract ([Tier2PageService] owns the fetch/stale decision):
 *  - origin fetched now → 200, raw page body, `X-Cache: fresh`;
 *  - origin unreachable, cached copy exists (any age) → 200, cached body, `X-Cache: stale`;
 *  - origin unreachable, no cached copy → 404, uniform error envelope (EH_UNAVAILABLE);
 *  - origin reachable but answered a non-2xx/5xx status (e.g. 404) → status and
 *    body passed through verbatim, no X-Cache (E2E-6: errors are surfaced, never
 *    papered over — mirrors SiteProxyController).
 *
 * The `url` host is whitelisted with the same predicate as the App's Tier-2
 * interceptor ([SiteProxyController.isGallerySiteHost]) so the endpoint can
 * never act as an open proxy; anything else is a 400 VALIDATION_ERROR.
 */
@RestController
@RequestMapping("/api/v1/tier2")
class Tier2PageController(
    private val tier2PageService: Tier2PageService,
) {

    @GetMapping("/page")
    fun page(@RequestParam url: String): ResponseEntity<*> {
        val target = url.toHttpUrlOrNull()
        if (target == null || !SiteProxyController.isGallerySiteHost(target.host)) {
            return errorEnvelope(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "url must point at a Gallery Site host (e-hentai.org, exhentai.org, ehgt.org, lofi.e-hentai.org or their subdomains)"
            )
        }

        return when (val result = tier2PageService.fetchPage(url)) {
            is Tier2PageResult.Served -> ResponseEntity.ok()
                .header("X-Cache", if (result.stale) "stale" else "fresh")
                .header(HttpHeaders.CONTENT_TYPE, result.contentType)
                .body(result.body.toByteArray(Charsets.ISO_8859_1))

            is Tier2PageResult.UpstreamError -> ResponseEntity.status(result.status)
                .header(HttpHeaders.CONTENT_TYPE, result.contentType)
                .body(result.body.toByteArray(Charsets.ISO_8859_1))

            Tier2PageResult.Unavailable -> errorEnvelope(
                HttpStatus.NOT_FOUND,
                EhUnavailableException.CODE,
                EhUnavailableException.USER_MESSAGE
            )
        }
    }
}
