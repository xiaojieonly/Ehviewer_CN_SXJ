package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.dto.FavoriteAddRequest
import com.hippo.anotherviewer.web.dto.FavoriteListResponse
import com.hippo.anotherviewer.web.dto.FavoriteRemoveRequest
import com.hippo.anotherviewer.web.service.FavoriteService
import jakarta.validation.Valid
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
    fun addFavorite(@Valid @RequestBody request: FavoriteAddRequest): ResponseEntity<Map<String, Boolean>> {
        val result = favoriteService.addFavorite(request.gid, request.token, null, request.category, request.slot)
        return ResponseEntity.ok(mapOf("success" to result))
    }

    @DeleteMapping("/remove")
    fun removeFavorite(@Valid @RequestBody request: FavoriteRemoveRequest): ResponseEntity<Map<String, Boolean>> {
        val result = favoriteService.removeFavorite(request.gid)
        return ResponseEntity.ok(mapOf("success" to result))
    }
}
