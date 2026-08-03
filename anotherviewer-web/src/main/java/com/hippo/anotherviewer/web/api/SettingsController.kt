package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.dto.*
import com.hippo.anotherviewer.web.service.SettingsService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/settings")
class SettingsController(private val settingsService: SettingsService) {

    @GetMapping
    fun getSettings(): ResponseEntity<SettingsResponse> {
        return ResponseEntity.ok(settingsService.getSettings())
    }

    @PutMapping
    fun updateSettings(@Valid @RequestBody request: SettingsUpdateRequest): ResponseEntity<Boolean> {
        return ResponseEntity.ok(settingsService.updateSettings(request))
    }
}
