package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.dto.*
import com.hippo.anotherviewer.web.service.UserPreferenceService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/preferences")
class PreferenceController(private val preferenceService: UserPreferenceService) {

    @GetMapping
    fun get(authentication: Authentication): ResponseEntity<PreferenceResponse> {
        val username = authentication.name
        return ResponseEntity.ok(preferenceService.get(username))
    }

    @PutMapping
    fun update(
        @Valid @RequestBody request: PreferenceUpdateRequest,
        authentication: Authentication,
    ): ResponseEntity<PreferenceResponse> {
        val username = authentication.name
        val merged = preferenceService.update(username, request, "webui")
        return ResponseEntity.ok(merged)
    }
}
