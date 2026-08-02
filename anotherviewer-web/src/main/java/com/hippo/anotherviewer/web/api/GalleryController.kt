package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.dto.*
import com.hippo.anotherviewer.web.service.GalleryService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
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
        @Valid @RequestBody body: AddHistoryRequest
    ): ResponseEntity<Map<String, Any>> {
        if (gid <= 0) {
            return ResponseEntity.badRequest().body(
                mapOf("success" to false, "message" to "gid must be a positive number")
            )
        }
        galleryService.addToHistory(gid, body.token, body.title)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleBodyValidationErrors(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, Any>> {
        val message = ex.bindingResult.fieldErrors.joinToString("; ") {
            it.defaultMessage ?: "invalid value for ${it.field}"
        }
        return ResponseEntity.badRequest().body(mapOf("success" to false, "message" to message))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(ex: HttpMessageNotReadableException): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.badRequest().body(
            mapOf("success" to false, "message" to "Invalid request body")
        )
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
