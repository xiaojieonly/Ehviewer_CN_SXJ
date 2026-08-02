package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.dto.CacheStatsResponse
import com.hippo.anotherviewer.web.dto.SuccessResponse
import com.hippo.anotherviewer.web.service.ImageCacheService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/cache")
class CacheController(private val imageCacheService: ImageCacheService) {

    @GetMapping("/stats")
    fun getCacheStats(): ResponseEntity<CacheStatsResponse> {
        return ResponseEntity.ok(imageCacheService.getCacheStats())
    }

    @DeleteMapping("/gallery/{id}")
    fun clearGalleryCache(@PathVariable id: Long): ResponseEntity<SuccessResponse> {
        val removed = imageCacheService.clearGalleryCache(id)
        return if (removed) {
            ResponseEntity.ok(SuccessResponse(success = true))
        } else {
            ResponseEntity.notFound().build()
        }
    }
}
