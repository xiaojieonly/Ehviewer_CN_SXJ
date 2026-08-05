package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.service.EhImportService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * 原版 EhViewer 备份导入端点（B3）。鉴权与其余 /api 一致（Bearer token + 管理员，
 * 见 SecurityConfig）。失败返回统一错误 envelope（M-6），成功返回 B3 契约计数。
 */
@RestController
@RequestMapping("/api/v1/backup")
class ImportController(private val ehImportService: EhImportService) {

    private val logger = LoggerFactory.getLogger(ImportController::class.java)

    /** 上传 EhViewer 导出 .db（+ 可选 cookie db/JSON）→ 表驱动扫描导入。`force=true` 时 gid 冲突 upsert。 */
    @PostMapping("/import-ehviewer")
    fun importEhViewer(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(value = "cookies", required = false) cookies: MultipartFile?,
        @RequestParam(value = "force", defaultValue = "false") force: Boolean,
        authentication: Authentication,
    ): ResponseEntity<*> {
        if (file.isEmpty) {
            return errorEnvelope(HttpStatus.BAD_REQUEST, "IMPORT_FAILED", "上传的 EhViewer 备份文件为空")
        }
        return try {
            ResponseEntity.ok(ehImportService.importEhViewer(file, cookies, force, authentication.name))
        } catch (e: Exception) {
            logger.warn("EhViewer 备份导入失败: {}", e.message)
            errorEnvelope(HttpStatus.BAD_REQUEST, "IMPORT_FAILED", e.message ?: "导入失败")
        }
    }
}
