package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.SyncPullResponse
import com.hippo.ehviewer.web.dto.SyncPushRequest
import com.hippo.ehviewer.web.dto.SyncPushResponse
import com.hippo.ehviewer.web.dto.SyncStatusResponse
import com.hippo.ehviewer.web.service.SyncService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/sync")
class SyncController(private val syncService: SyncService) {

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
        authentication: Authentication,
    ): ResponseEntity<SyncPullResponse> {
        return ResponseEntity.ok(syncService.pull(since, authentication.name))
    }

    @GetMapping("/status")
    fun status(): ResponseEntity<SyncStatusResponse> {
        return ResponseEntity.ok(syncService.status())
    }
}
