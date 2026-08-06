package com.hippo.anotherviewer.web.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.hippo.anotherviewer.web.config.ApiError
import com.hippo.anotherviewer.web.config.ApiErrorEnvelope
import com.hippo.anotherviewer.web.dto.BackupManifest
import com.hippo.anotherviewer.web.dto.BackupResult
import com.hippo.anotherviewer.web.dto.JobSubmitResponse
import com.hippo.anotherviewer.web.dto.JobState
import com.hippo.anotherviewer.web.dto.JobType
import com.hippo.anotherviewer.web.service.BackupService
import com.hippo.anotherviewer.web.service.BackupStateHolder
import com.hippo.anotherviewer.web.service.Job
import com.hippo.anotherviewer.web.service.JobService
import com.hippo.anotherviewer.web.service.isSafeArchiveEntryName
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.io.File
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
 * 大数据量操作（导出/还原）经 JobService 异步执行：POST .../async 与
 * POST /restore 立即返回 202 {jobId, state}，进度经 WS 推送，终态 result
 * 按 plan-2026-08-06 附录 A1 约定。同步 GET /export 保留兼容。
 */
@RestController
@RequestMapping("/api/v1/backup")
class BackupController(
    private val backupService: BackupService,
    private val backupState: BackupStateHolder,
    private val jobService: JobService,
) {
    private val logger = LoggerFactory.getLogger(BackupController::class.java)
    private val mapper = jacksonObjectMapper()
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /** 导出 zip 的临时文件命名前缀（GET /export/{jobId} 按 jobId 反查路径）。 */
    private fun exportZipFile(jobId: String): File =
        File(System.getProperty("java.io.tmpdir"), "anotherviewer-export-job-$jobId.zip")

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

    /**
     * 异步导出：提交 EXPORT Job → 202。worker 跑 [BackupService.export]，随后把
     * 产物目录（manifest + 分片）打包成单个 zip 写到临时目录，终态 result =
     * `{downloadUrl, filename, sizeBytes}`（附录 A1）。
     */
    @PostMapping("/export/async")
    fun exportAsync(
        @RequestParam(name = "includeDownloads", defaultValue = "false") includeDownloads: Boolean
    ): ResponseEntity<*> {
        lateinit var job: Job
        try {
            job = jobService.submit(JobType.EXPORT, {}) { handle -> runExport(handle, includeDownloads, job) }
        } catch (e: IllegalStateException) {
            return errorEnvelope(HttpStatus.CONFLICT, "CONFLICT", e.message ?: "已有导出任务进行中")
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(JobSubmitResponse(job.jobId, job.state))
    }

    /**
     * 下载异步导出产物：job COMPLETED 才可下，流式下发（application/zip），
     * 流完删除临时文件（TTL 兜底见 [cleanupStaleExportFiles]）。
     * 错误响应（404/409）以 JSON 信封流返回——声明返回类型必须是
     * ResponseEntity<StreamingResponseBody>，否则容器按普通 body 处理流对象。
     */
    @GetMapping("/export/{jobId}")
    fun downloadExport(@PathVariable jobId: String): ResponseEntity<StreamingResponseBody> {
        val job = jobService.getJob(jobId)
            ?: return errorStream(HttpStatus.NOT_FOUND, "EXPORT_NOT_FOUND", "导出任务不存在: $jobId")
        if (job.state != JobState.COMPLETED) {
            return errorStream(HttpStatus.CONFLICT, "EXPORT_NOT_READY", "导出尚未完成")
        }
        val file = exportZipFile(jobId)
        if (!file.isFile) {
            return errorStream(HttpStatus.NOT_FOUND, "EXPORT_NOT_FOUND", "导出文件不存在或已过期")
        }
        val filename = (job.result as? Map<*, *>)?.get("filename") as? String ?: file.name
        return ResponseEntity.ok()
            .contentType(MediaType("application", "zip"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .body(StreamingResponseBody { out ->
                try {
                    Files.newInputStream(file.toPath()).use { it.transferTo(out) }
                } finally {
                    runCatching { Files.deleteIfExists(file.toPath()) }
                }
            })
    }

    /** M-6 错误信封以 JSON 流返回（与 [StreamingResponseBody] 返回类型兼容）。 */
    private fun errorStream(status: HttpStatus, code: String, message: String): ResponseEntity<StreamingResponseBody> {
        val envelope = ApiErrorEnvelope(ApiError(code, message, newTraceId(), status.value()))
        val json = mapper.writeValueAsBytes(envelope)
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(StreamingResponseBody { out -> out.write(json) })
    }

    /** 上传备份 zip → 解包 → 逐片 SHA-256 校验（失败拒绝）→ 还原。成功后需重启生效。 */
    @PostMapping("/restore")
    fun restore(@RequestParam("file") file: MultipartFile): ResponseEntity<*> {
        lateinit var job: Job
        try {
            job = jobService.submit(JobType.RESTORE, {
                if (file.isEmpty) throw IllegalArgumentException("上传的备份文件为空")
            }) { handle -> runRestore(handle, file, job) }
        } catch (e: IllegalStateException) {
            return errorEnvelope(HttpStatus.CONFLICT, "CONFLICT", e.message ?: "已有还原任务进行中")
        } catch (e: IllegalArgumentException) {
            return errorEnvelope(HttpStatus.BAD_REQUEST, "RESTORE_FAILED", e.message ?: "还原参数错误")
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(JobSubmitResponse(job.jobId, job.state))
    }

    // ── 异步 worker ─────────────────────────────────────────────

    /** EXPORT worker：导出 → 打包单 zip → 终态 result。异常上抛由 JobService 置 FAILED。 */
    private fun runExport(handle: JobService.JobHandle, includeDownloads: Boolean, job: Job) {
        cleanupStaleExportFiles()
        handle.stage("数据库快照")
        val result = backupService.export(includeDownloads)
        val zipFile = exportZipFile(job.jobId)
        Files.deleteIfExists(zipFile.toPath())
        val total = result.slices.size.toLong() + 1 // 分片数 + 打包阶段
        try {
            ZipOutputStream(Files.newOutputStream(zipFile.toPath())).use { zip ->
                writeZipEntry(zip, "manifest.json", result.directory.resolve("manifest.json"))
                result.slices.forEachIndexed { i, slice ->
                    handle.progress("压缩分片 ${i + 1}/${result.slices.size}", (i + 1).toLong(), total)
                    writeZipEntry(zip, slice.fileName.toString(), slice)
                }
            }
        } catch (e: Exception) {
            runCatching { Files.deleteIfExists(zipFile.toPath()) }
            throw e
        }
        handle.progress("打包 zip", total, total)
        job.result = mapOf(
            "downloadUrl" to "/api/v1/backup/export/${job.jobId}",
            "filename" to "anotherviewer-backup-${LocalDateTime.now().format(stamp)}.zip",
            "sizeBytes" to Files.size(zipFile.toPath()),
        )
        logger.info("导出任务完成: jobId={}, zip={}, sizeBytes={}", job.jobId, zipFile.name, Files.size(zipFile.toPath()))
    }

    /**
     * RESTORE worker：解包（含 isSafeArchiveEntryName 校验）→ manifest 缺失在
     * 此转 FAILED（而非 500）→ 逐片还原 → 成功后照旧置 restorePending。
     * 临时目录清理放 finally；异常上抛由 JobService 置 FAILED（error = 异常 message）。
     */
    private fun runRestore(handle: JobService.JobHandle, file: MultipartFile, job: Job) {
        val tmp = Files.createTempDirectory("backup-upload-")
        try {
            unzip(file.inputStream, tmp)
            val manifestFile = tmp.resolve("manifest.json")
            if (!Files.isRegularFile(manifestFile)) {
                throw IllegalArgumentException("备份包缺少 manifest.json")
            }
            val manifest: BackupManifest = mapper.readValue<BackupManifest>(manifestFile.toFile())
            handle.stage("解包校验")
            val slices = manifest.slices.associate { it.name to tmp.resolve(it.name) }
            backupService.restore(manifest, slices, handle)
            // R4-2: 还原成功后置运行态标志，前端横幅提示"重启后生效"；重启后 bean 复位为 false。
            backupState.restorePending = true
            handle.stage("完成")
            job.result = BackupRestoreResponse(true, "还原成功，重启后生效")
        } finally {
            runCatching { tmp.toFile().deleteRecursively() }
        }
    }

    /** 提交导出时顺带清理 1h 前遗留的导出 zip（流式删除之外的 TTL 兜底）。 */
    private fun cleanupStaleExportFiles(olderThanMs: Long = EXPORT_FILE_TTL_MS) {
        val dir = File(System.getProperty("java.io.tmpdir"))
        val cutoff = System.currentTimeMillis() - olderThanMs
        dir.listFiles { f -> f.isFile && f.name.startsWith("anotherviewer-export-job-") }?.forEach { f ->
            if (f.lastModified() < cutoff) {
                runCatching { Files.deleteIfExists(f.toPath()) }
            }
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

    private companion object {
        const val EXPORT_FILE_TTL_MS = 60 * 60 * 1000L
    }
}
