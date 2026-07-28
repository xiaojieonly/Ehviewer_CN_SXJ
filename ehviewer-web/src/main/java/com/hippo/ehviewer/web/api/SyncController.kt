package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.SyncPullResponse
import com.hippo.ehviewer.web.dto.SyncPushRequest
import com.hippo.ehviewer.web.dto.SyncPushResponse
import com.hippo.ehviewer.web.dto.SyncStatusResponse
import com.hippo.ehviewer.web.service.SyncService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/sync")
class SyncController(private val syncService: SyncService) {

    @PostMapping("/push")
    fun push(@RequestBody request: SyncPushRequest): ResponseEntity<SyncPushResponse> {
        return ResponseEntity.ok(syncService.push(request))
    }

    @GetMapping("/pull")
    fun pull(@RequestParam since: Long): ResponseEntity<SyncPullResponse> {
        return ResponseEntity.ok(syncService.pull(since))
    }

    @GetMapping("/status")
    fun status(): ResponseEntity<SyncStatusResponse> {
        return ResponseEntity.ok(syncService.status())
    }
}
