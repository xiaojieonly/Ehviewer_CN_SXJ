package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.dto.SyncPolicyDto
import com.hippo.anotherviewer.web.service.SyncService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Sync policy endpoint (contract v2 §8, ADR-0003; OpenAPI: /api/v1/sync/policy).
 *
 * GET 返回当前 SyncPolicy；PUT 持久化（任一已认证客户端，跨重启生效）。
 * conflictStrategy/clientTier/autoSyncIntervalSec 非法值 → 400（统一错误信封）。
 *
 * D2 语义：android push 携带 policy 时为权威覆盖（见 [SyncController.push]），
 * WebUI 经 PUT 的修改可能被下一次 android push 覆盖，高级面板须明示。
 */
@RestController
@RequestMapping("/api/v1/sync/policy")
class PolicyController(private val syncService: SyncService) {

    @GetMapping
    fun getPolicy(): ResponseEntity<SyncPolicyDto> {
        return ResponseEntity.ok(syncService.currentPolicy())
    }

    @PutMapping
    fun updatePolicy(@RequestBody policy: SyncPolicyDto): ResponseEntity<*> {
        // conflictStrategy 由 Jackson 约束为枚举（未知值 → HttpMessageNotReadable → 400）；
        // clientTier / autoSyncIntervalSec 值域在此显式校验（契约 §8：非法值 400）。
        syncService.validatePolicy(policy)?.let { message ->
            return errorEnvelope(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message)
        }
        return ResponseEntity.ok(syncService.updatePolicy(policy))
    }
}
