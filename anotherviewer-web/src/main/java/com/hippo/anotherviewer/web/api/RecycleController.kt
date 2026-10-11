package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.dto.RecyclePurgeRequest
import com.hippo.anotherviewer.web.dto.RecyclePurgeResponse
import com.hippo.anotherviewer.web.dto.RecycleRestoreRequest
import com.hippo.anotherviewer.web.dto.RecycleRestoreResponse
import com.hippo.anotherviewer.web.service.RecycleService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

/**
 * v4 回收站端点（WP3）：墓碑行的列表 / 恢复 / 物理清除。
 * 错误信封照 [ApiErrors] 惯例（非法 entity → 400 VALIDATION_ERROR）。
 */
@RestController
@RequestMapping("/api/v1/recycle")
class RecycleController(
    private val recycleService: RecycleService,
) {

    @GetMapping("/list")
    fun list(
        @RequestParam entity: String?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) pageSize: Int?,
        authentication: Authentication,
    ): ResponseEntity<*> = try {
        ResponseEntity.ok(
            recycleService.list(
                username = authentication.name,
                entity = entity,
                page = page ?: 0,
                pageSize = pageSize ?: DEFAULT_PAGE_SIZE,
            )
        )
    } catch (e: IllegalArgumentException) {
        errorEnvelope(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", e.message ?: "invalid recycle entity")
    }

    @PostMapping("/restore")
    fun restore(
        @Valid @RequestBody request: RecycleRestoreRequest,
        authentication: Authentication,
    ): ResponseEntity<RecycleRestoreResponse> =
        ResponseEntity.ok(recycleService.restore(authentication.name, request))

    /** body 可缺省：缺省/无 items = 清除全部到期项（与定时任务同一路径）。 */
    @PostMapping("/purge")
    fun purge(
        @RequestBody(required = false) request: RecyclePurgeRequest?,
        authentication: Authentication,
    ): ResponseEntity<RecyclePurgeResponse> =
        ResponseEntity.ok(recycleService.purge(authentication.name, request))

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
