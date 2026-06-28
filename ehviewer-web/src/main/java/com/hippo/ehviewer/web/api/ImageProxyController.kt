package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.service.ImageCacheService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/image")
class ImageProxyController(private val imageCacheService: ImageCacheService) {

    @GetMapping("/proxy")
    fun proxyImage(@RequestParam url: String): ResponseEntity<ByteArray> {
        val cached = imageCacheService.getCachedImage(url)
        if (cached != null) {
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE)
                .body(cached)
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
    }

    @GetMapping("/cache/status")
    fun cacheStatus(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "cacheSize" to imageCacheService.getCacheSize()
        ))
    }

    @PostMapping("/cache/clear")
    fun clearCache(): ResponseEntity<Map<String, Boolean>> {
        imageCacheService.clearCache()
        return ResponseEntity.ok(mapOf("success" to true))
    }
}
