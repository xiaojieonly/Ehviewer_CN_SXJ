package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.service.GalleryService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/gallery")
class GalleryController(private val galleryService: GalleryService) {

    @GetMapping("/search")
    fun search(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) category: Int?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ResponseEntity<GalleryListResponse> {
        return ResponseEntity.ok(galleryService.searchGallery(keyword, category, page, pageSize))
    }

    @GetMapping("/{gid}")
    fun getDetail(@PathVariable gid: Long): ResponseEntity<*> {
        val detail = galleryService.getGalleryDetail(gid)
            ?: return ResponseEntity.notFound().build<Any>()
        return ResponseEntity.ok(detail)
    }

    @GetMapping("/history")
    fun getHistory(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ResponseEntity<GalleryListResponse> {
        return ResponseEntity.ok(galleryService.getHistory(page, pageSize))
    }

    @PostMapping("/history/{gid}")
    fun addToHistory(
        @PathVariable gid: Long,
        @RequestBody body: Map<String, Any>
    ): ResponseEntity<Map<String, Boolean>> {
        val token = body["token"] as? String ?: ""
        val title = body["title"] as? String
        galleryService.addToHistory(gid, token, title)
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
    fun createQuickSearch(@RequestBody dto: QuickSearchDto): ResponseEntity<QuickSearchDto> {
        return ResponseEntity.ok(galleryService.createQuickSearch(dto))
    }

    @DeleteMapping("/quick-search/{id}")
    fun deleteQuickSearch(@PathVariable id: Long): ResponseEntity<Map<String, Boolean>> {
        galleryService.deleteQuickSearch(id)
        return ResponseEntity.ok(mapOf("success" to true))
    }
}
