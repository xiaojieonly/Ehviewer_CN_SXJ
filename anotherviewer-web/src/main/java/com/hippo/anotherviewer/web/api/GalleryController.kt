package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.dto.*
import com.hippo.anotherviewer.web.service.GalleryService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/gallery")
class GalleryController(private val galleryService: GalleryService) {

    @GetMapping("/search")
    fun search(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) category: Int?,
        @RequestParam(required = false) sort: Int?,
        @RequestParam(required = false) pageMin: Int?,
        @RequestParam(required = false) pageMax: Int?,
        @RequestParam(required = false) minRating: Int?,
        @RequestParam(required = false) searchName: Boolean?,
        @RequestParam(required = false) searchTags: Boolean?,
        @RequestParam(required = false) searchDesc: Boolean?,
        @RequestParam(required = false) searchTorrents: Boolean?,
        // W3 R4-10: higher AdvanceSearchTable bits (backendized).
        @RequestParam(required = false) searchTorrentsOnly: Boolean?,
        @RequestParam(required = false) searchLowPowerTags: Boolean?,
        @RequestParam(required = false) searchDownvotedTags: Boolean?,
        @RequestParam(required = false) searchExpunged: Boolean?,
        @RequestParam(required = false) disableLanguageFilter: Boolean?,
        @RequestParam(required = false) disableUploaderFilter: Boolean?,
        @RequestParam(required = false) disableTagFilter: Boolean?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ResponseEntity<GalleryListResponse> {
        // M-5: pageSize is clamped (not rejected) so an oversized value can
        // never be forwarded to the upstream gallery search; `page` is
        // 0-based per contract, so only the lower bound applies. The extended
        // params follow the same clamp-don't-reject policy against their
        // contract ranges (openapi GET /api/v1/gallery/search).
        val clampedPage = page.coerceAtLeast(0)
        val clampedPageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        val clampedSort = sort?.takeIf { it in SORT_RANGE } ?: 0
        val clampedPageMin = pageMin?.coerceAtLeast(0)
        val clampedPageMax = pageMax?.coerceAtLeast(0)
        val clampedMinRating = (minRating ?: 0).coerceIn(0, 5)
        return ResponseEntity.ok(
            galleryService.searchGallery(
                keyword, category, clampedPage, clampedPageSize,
                clampedSort, clampedPageMin, clampedPageMax, clampedMinRating,
                searchName ?: false, searchTags ?: false,
                searchDesc ?: false, searchTorrents ?: false,
                searchTorrentsOnly ?: false, searchLowPowerTags ?: false,
                searchDownvotedTags ?: false, searchExpunged ?: false,
                disableLanguageFilter ?: false, disableUploaderFilter ?: false,
                disableTagFilter ?: false
            )
        )
    }

    /**
     * Feed endpoint (contracts/openapi.yaml GET /api/v1/gallery/feed):
     * subscription/popular return the search-shaped GalleryListResponse,
     * toplist returns TopListResponse. An invalid `mode` is a 400 with the
     * frozen `{success:false, message}` body; an unreachable site surfaces
     * `success=false` from the service with HTTP 200 (E2E-6, same as /search).
     */
    @GetMapping("/feed")
    fun feed(
        @RequestParam(required = false) mode: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ResponseEntity<*> {
        if (mode == null || mode !in FEED_MODES) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(FeedErrorResponse(success = false, message = "mode must be one of: ${FEED_MODES.joinToString(", ")}"))
        }
        // Same M-5 clamping as /search.
        val clampedPage = page.coerceAtLeast(0)
        val clampedPageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        return when (mode) {
            "toplist" -> ResponseEntity.ok(galleryService.topListFeed())
            else -> ResponseEntity.ok(galleryService.feedGallery(mode, clampedPage, clampedPageSize))
        }
    }

    @GetMapping("/{gid}")
    fun getDetail(
        @PathVariable gid: Long,
        @RequestParam(required = false) token: String?,
    ): ResponseEntity<*> {
        val detail = galleryService.getGalleryDetail(gid, token)
            ?: return errorEnvelope(HttpStatus.NOT_FOUND, "NOT_FOUND", "Gallery not found")
        return ResponseEntity.ok(detail)
    }

    @GetMapping("/history")
    fun getHistory(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ResponseEntity<GalleryListResponse> {
        // Same M-5 clamping as /search.
        val clampedPage = page.coerceAtLeast(0)
        val clampedPageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        return ResponseEntity.ok(galleryService.getHistory(clampedPage, clampedPageSize))
    }

    @PostMapping("/history/{gid}")
    fun addToHistory(
        @PathVariable gid: Long,
        @Valid @RequestBody body: AddHistoryRequest
    ): ResponseEntity<*> {
        if (gid <= 0) {
            return errorEnvelope(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "gid must be a positive number")
        }
        // S6: page 透传（可空）——缺省（null）不改写已存进度，显式 0 重读写 0。
        galleryService.addToHistory(gid, body.token, body.title, body.mode, body.page)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @GetMapping("/favorites")
    fun getFavorites(): ResponseEntity<GalleryListResponse> {
        return ResponseEntity.ok(galleryService.getLocalFavorites())
    }

    @GetMapping("/quick-search")
    fun getQuickSearches(): ResponseEntity<QuickSearchListResponse> {
        return ResponseEntity.ok(galleryService.getQuickSearches())
    }

    @PostMapping("/quick-search")
    fun createQuickSearch(@Valid @RequestBody dto: QuickSearchDto): ResponseEntity<QuickSearchDto> {
        return ResponseEntity.ok(galleryService.createQuickSearch(dto))
    }

    @DeleteMapping("/quick-search/{id}")
    fun deleteQuickSearch(@PathVariable id: Long): ResponseEntity<Map<String, Boolean>> {
        galleryService.deleteQuickSearch(id)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    companion object {
        /** Upper bound for pageSize on paginated endpoints (M-5 clamp). */
        const val MAX_PAGE_SIZE = 200

        /** Contract values for the `sort` param (openapi enum 0..3). */
        val SORT_RANGE = 0..3

        /** Contract values for the feed `mode` param (openapi enum). */
        val FEED_MODES = setOf("subscription", "popular", "toplist")
    }
}
