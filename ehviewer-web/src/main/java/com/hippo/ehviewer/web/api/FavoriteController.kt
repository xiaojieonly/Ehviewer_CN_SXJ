package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.FavoriteAddRequest
import com.hippo.ehviewer.web.dto.FavoriteListResponse
import com.hippo.ehviewer.web.dto.FavoriteRemoveRequest
import com.hippo.ehviewer.web.service.FavoriteService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/favorite")
class FavoriteController(private val favoriteService: FavoriteService) {

    @GetMapping("/list")
    fun listFavorites(
        @RequestParam(defaultValue = "0") slot: Int,
        @RequestParam(defaultValue = "1") page: Int
    ): ResponseEntity<FavoriteListResponse> {
        return ResponseEntity.ok(favoriteService.listFavorites(slot, page))
    }

    @PostMapping("/add")
    fun addFavorite(@RequestBody request: FavoriteAddRequest): ResponseEntity<Map<String, Boolean>> {
        val result = favoriteService.addFavorite(request.gid, request.token, null, request.category)
        return ResponseEntity.ok(mapOf("success" to result))
    }

    @DeleteMapping("/remove")
    fun removeFavorite(@RequestBody request: FavoriteRemoveRequest): ResponseEntity<Map<String, Boolean>> {
        val result = favoriteService.removeFavorite(request.gid)
        return ResponseEntity.ok(mapOf("success" to result))
    }
}
