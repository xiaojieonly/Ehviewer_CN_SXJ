package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.dto.*
import com.hippo.anotherviewer.web.service.SmbBackupService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/smb")
class SmbController(private val smbBackupService: SmbBackupService) {

    @GetMapping("/config")
    fun getConfig(): ResponseEntity<SmbConfigResponse?> {
        return ResponseEntity.ok(smbBackupService.getConfig())
    }

    @PutMapping("/config")
    fun updateConfig(@Valid @RequestBody request: SmbConfigUpdateRequest): ResponseEntity<Boolean> {
        return ResponseEntity.ok(smbBackupService.updateConfig(request))
    }

    @PostMapping("/test-connection")
    fun testConnection(@Valid @RequestBody request: SmbTestConnectionRequest): ResponseEntity<SmbTestConnectionResponse> {
        return ResponseEntity.ok(smbBackupService.testConnection(request))
    }

    @PostMapping("/sync")
    fun startSync(@Valid @RequestBody request: SmbSyncRequest = SmbSyncRequest()): ResponseEntity<Boolean> {
        return ResponseEntity.ok(smbBackupService.startSync(request.aggressive))
    }

    @PostMapping("/cancel")
    fun cancelSync(): ResponseEntity<Boolean> {
        return ResponseEntity.ok(smbBackupService.cancelSync())
    }

    @GetMapping("/progress")
    fun getProgress(): ResponseEntity<SmbSyncProgress> {
        return ResponseEntity.ok(smbBackupService.getProgress())
    }
}
