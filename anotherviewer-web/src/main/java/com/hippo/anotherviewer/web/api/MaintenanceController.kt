package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.dto.MaintenanceCleanRequest
import com.hippo.anotherviewer.web.dto.MaintenancePreviewResponse
import com.hippo.anotherviewer.web.service.DownloadMaintenanceService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 下载维护端点（W2-DL F2）：dry-run 预览 + 执行清理两段式。
 * 同步小任务实现，取舍理由见 [DownloadMaintenanceService] 类注释。
 */
@RestController
@RequestMapping("/api/v1/download/maintenance")
class MaintenanceController(private val maintenanceService: DownloadMaintenanceService) {

    /** 第一段：只读扫描，返回将删清单（冗余文件 + 无效下载）。 */
    @GetMapping("/preview")
    fun preview(): ResponseEntity<MaintenancePreviewResponse> =
        ResponseEntity.ok(maintenanceService.preview())

    /** 第二段：按类别执行清理；执行前重新扫描，只删当前仍命中的条目。 */
    @PostMapping("/clean")
    fun clean(@Valid @RequestBody request: MaintenanceCleanRequest): ResponseEntity<*> =
        ResponseEntity.ok(maintenanceService.clean(request.kind!!))
}
