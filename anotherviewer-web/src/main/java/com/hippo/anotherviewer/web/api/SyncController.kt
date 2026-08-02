package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.dto.DeviceInfoDto
import com.hippo.anotherviewer.web.dto.SyncPullResponse
import com.hippo.anotherviewer.web.dto.SyncPushRequest
import com.hippo.anotherviewer.web.dto.SyncPushResponse
import com.hippo.anotherviewer.web.dto.SyncStatusResponse
import com.hippo.anotherviewer.web.service.SiteAuthService
import com.hippo.anotherviewer.web.service.SyncService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/sync")
class SyncController(
    private val syncService: SyncService,
    private val authService: SiteAuthService,
) {

    @PostMapping("/push")
    fun push(
        @RequestBody request: SyncPushRequest,
        authentication: Authentication,
    ): ResponseEntity<SyncPushResponse> {
        return ResponseEntity.ok(syncService.push(request, authentication.name))
    }

    @GetMapping("/pull")
    fun pull(
        @RequestParam since: Long,
        @RequestParam(required = false) deviceId: String = "",
        authentication: Authentication,
    ): ResponseEntity<SyncPullResponse> {
        return ResponseEntity.ok(syncService.pull(since, authentication.name, deviceId))
    }

    @GetMapping("/status")
    fun status(authentication: Authentication): ResponseEntity<SyncStatusResponse> {
        return ResponseEntity.ok(syncService.status(authentication.name))
    }

    @GetMapping("/devices")
    fun devices(authentication: Authentication): ResponseEntity<List<DeviceInfoDto>> {
        return ResponseEntity.ok(syncService.listDevices(authentication.name))
    }

    @DeleteMapping("/devices/{deviceId}")
    fun revokeDevice(
        @PathVariable deviceId: String,
        authentication: Authentication,
    ): ResponseEntity<Map<String, Any>> {
        val revoked = authService.revokeDevice(deviceId, authentication.name)
        return if (revoked) {
            ResponseEntity.ok(mapOf("success" to true))
        } else {
            ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("success" to false, "message" to "Device not found or not owned by this user"))
        }
    }
}
