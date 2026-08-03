package com.hippo.anotherviewer.web.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.hippo.anotherviewer.web.dto.BackupManifest
import com.hippo.anotherviewer.web.dto.BackupResult
import com.hippo.anotherviewer.web.service.BackupService
import com.hippo.anotherviewer.web.service.BackupStateHolder
import com.hippo.anotherviewer.web.service.isSafeArchiveEntryName
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupRestoreResponse(
    val success: Boolean,
    val message: String? = null,
)

/** R4-2: restore 运行态感知响应（GET /api/v1/backup/state）。 */
data class BackupStateResponse(
    val restorePending: Boolean,
)

/**
 * 备份/还原接口，鉴权与其余 /api 一致（Bearer token，见 SecurityConfig）。
 * 导出产物同时落盘 `<dataDir>/backups/`（分片可独立拷走异地备份），
 * 浏览器下载时再套一层 zip 包装。
 *
 * 还原失败（空文件/缺 manifest/解包还原异常）返回统一错误 envelope（M-6），
 * 成功仍为 `{success,message}`。
 */
@RestController
@RequestMapping("/api/v1/backup")
class BackupController(
    private val backupService: BackupService,
    private val backupState: BackupStateHolder,
) {
    private val logger = LoggerFactory.getLogger(BackupController::class.java)
    private val mapper = jacksonObjectMapper()
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /**
     * R4-2: 运行态感知。restorePending=true 表示"已成功还原但尚未重启"，
     * 前端据此显示持久警示横幅。health 语义不受影响（不改动 /health）。
     */
    @GetMapping("/state")
    fun state(): ResponseEntity<BackupStateResponse> =
        ResponseEntity.ok(BackupStateResponse(restorePending = backupState.restorePending))

    /** 生成备份并流式返回 `anotherviewer-backup-<yyyyMMdd-HHmmss>.zip`（分片 + manifest）。 */
    @GetMapping("/export")
    fun export(
        @RequestParam(name = "includeDownloads", defaultValue = "false") includeDownloads: Boolean
    ): ResponseEntity<StreamingResponseBody> {
        val result = backupService.export(includeDownloads)
        val filename = "anotherviewer-backup-${LocalDateTime.now().format(stamp)}.zip"
        return ResponseEntity.ok()
            .contentType(MediaType("application", "zip"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .body(StreamingResponseBody { out -> packZip(result, out) })
    }

    /** 上传备份 zip → 解包 → 逐片 SHA-256 校验（失败拒绝）→ 还原。成功后需重启生效。 */
    @PostMapping("/restore")
    fun restore(@RequestParam("file") file: MultipartFile): ResponseEntity<*> {
        if (file.isEmpty) {
            return errorEnvelope(HttpStatus.BAD_REQUEST, "RESTORE_FAILED", "上传的备份文件为空")
        }
        val tmp = Files.createTempDirectory("backup-upload-")
        try {
            unzip(file.inputStream, tmp)
            val manifestFile = tmp.resolve("manifest.json")
            if (!Files.isRegularFile(manifestFile)) {
                return errorEnvelope(HttpStatus.BAD_REQUEST, "RESTORE_FAILED", "备份包缺少 manifest.json")
            }
            val manifest: BackupManifest = mapper.readValue<BackupManifest>(manifestFile.toFile())
            val slices = manifest.slices.associate { it.name to tmp.resolve(it.name) }
            backupService.restore(manifest, slices)
            // R4-2: 还原成功后置运行态标志，前端横幅提示"重启后生效"；重启后 bean 复位为 false。
            backupState.restorePending = true
            return ResponseEntity.ok(BackupRestoreResponse(true, "还原成功，重启后生效"))
        } catch (e: Exception) {
            logger.warn("备份还原失败: {}", e.message)
            return errorEnvelope(HttpStatus.BAD_REQUEST, "RESTORE_FAILED", e.message ?: "还原失败")
        } finally {
            runCatching { tmp.toFile().deleteRecursively() }
        }
    }

    private fun packZip(result: BackupResult, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            writeZipEntry(zip, "manifest.json", result.directory.resolve("manifest.json"))
            result.slices.forEach { writeZipEntry(zip, it.fileName.toString(), it) }
        }
    }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, file: Path) {
        zip.putNextEntry(ZipEntry(name))
        Files.newInputStream(file).use { it.transferTo(zip) }
        zip.closeEntry()
    }

    private fun unzip(input: InputStream, targetDir: Path) {
        ZipInputStream(input).use { zip ->
            val buffer = ByteArray(64 * 1024)
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name
                    require(isSafeArchiveEntryName(name)) { "备份包含非法路径: $name" }
                    val dest = targetDir.resolve(name).normalize()
                    require(dest.startsWith(targetDir.normalize())) { "备份包路径越界: $name" }
                    Files.createDirectories(dest.parent)
                    Files.newOutputStream(dest).use { out ->
                        while (true) {
                            val n = zip.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
