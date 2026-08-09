package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.config.ApiError
import com.hippo.anotherviewer.web.config.ApiErrorEnvelope
import com.hippo.anotherviewer.web.dto.UploadCompleteRequest
import com.hippo.anotherviewer.web.dto.UploadInitRequest
import com.hippo.anotherviewer.web.service.DownloadUploadService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * App 推送本地下载端点（push-of-local-downloads，契约 /api/v1/download/upload 段）。
 * 鉴权与其余 /api/v1/download 端点一致（Bearer token，SecurityConfig 已覆盖）。
 *
 * - PUT {gid} — 注册/更新下载行；非 force 冲突 → 400 + InitResponse(success=false)
 *   （契约如此，App 据此跳过该本）。
 * - POST {gid}/page/{page} — multipart 单页上传（幂等覆盖）；page 为 path 参数。
 * - POST {gid}/complete — 标记 state=FINISHED；无行 → 404 envelope。
 */
@RestController
@RequestMapping("/api/v1/download/upload")
class DownloadUploadController(private val uploadService: DownloadUploadService) {

    private val logger = LoggerFactory.getLogger(DownloadUploadController::class.java)

    @PutMapping("/{gid}")
    fun initUpload(
        @PathVariable gid: Long,
        @Valid @RequestBody request: UploadInitRequest,
        authentication: Authentication,
    ): ResponseEntity<*> {
        val response = uploadService.initUpload(gid, request, authentication.name)
        // 契约：gid 冲突（非 force）→ 400 + InitResponse 本体（success=false）。
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
        }
    }

    @PostMapping("/{gid}/page/{page}")
    fun uploadPage(
        @PathVariable gid: Long,
        @PathVariable page: Int,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<*> {
        return try {
            // MultipartFile 必须在 HTTP 线程读完（EhImportService.prepareImport 同款约束）。
            uploadService.storePage(gid, page, file.originalFilename, file.bytes)
            ResponseEntity.ok(true)
        } catch (e: IllegalArgumentException) {
            logger.warn("Page upload rejected for gid={} page={}: {}", gid, page, e.message)
            errorEnvelope(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", e.message ?: "Invalid page upload")
        }
    }

    @PostMapping("/{gid}/complete")
    fun complete(
        @PathVariable gid: Long,
        @Valid @RequestBody request: UploadCompleteRequest,
    ): ResponseEntity<*> {
        return if (uploadService.completeUpload(gid, request)) {
            // 契约 200 响应 schema 为 ApiErrorEnvelope：以 code=OK 的 envelope 形状回执。
            ResponseEntity.ok(ApiErrorEnvelope(ApiError("OK", "download finalized", "", HttpStatus.OK.value())))
        } else {
            errorEnvelope(HttpStatus.NOT_FOUND, "NOT_FOUND", "No upload row for gid=$gid")
        }
    }
}
