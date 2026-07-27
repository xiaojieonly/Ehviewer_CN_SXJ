package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.service.SettingsService
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
    fun updateSettings(@RequestBody request: SettingsUpdateRequest): ResponseEntity<Boolean> {
        return ResponseEntity.ok(settingsService.updateSettings(request))
    }
}
