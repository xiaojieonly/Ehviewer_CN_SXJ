package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.DeviceInfoDto
import com.hippo.ehviewer.web.dto.SyncPullResponse
import com.hippo.ehviewer.web.dto.SyncPushRequest
import com.hippo.ehviewer.web.dto.SyncPushResponse
import com.hippo.ehviewer.web.dto.SyncStatusResponse
import com.hippo.ehviewer.web.service.DeviceService
import com.hippo.ehviewer.web.service.EhAuthService
import com.hippo.ehviewer.web.service.SyncService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/sync")
class SyncController(
    private val syncService: SyncService,
    private val deviceService: DeviceService,
    private val authService: EhAuthService,
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
    fun status(): ResponseEntity<SyncStatusResponse> {
        return ResponseEntity.ok(syncService.status())
    }

    @GetMapping("/devices")
    fun devices(): ResponseEntity<List<DeviceInfoDto>> {
        return ResponseEntity.ok(deviceService.list())
    }

    @DeleteMapping("/devices/{deviceId}")
    fun revokeDevice(@PathVariable deviceId: String): ResponseEntity<Map<String, Boolean>> {
        authService.revokeDevice(deviceId)
        return ResponseEntity.ok(mapOf("success" to true))
    }
}
