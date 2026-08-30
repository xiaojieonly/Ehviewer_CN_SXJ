package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.UploadCompleteRequest
import com.hippo.anotherviewer.web.dto.UploadInitRequest
import com.hippo.anotherviewer.web.dto.UploadInitResponse
import com.hippo.anotherviewer.web.entity.DownloadInfoEntity
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.util.regex.Pattern

/** MASTER-2026-08-22 S3：upload_enabled 关闭时由 [DownloadUploadService] 抛出。 */
class UploadDisabledException(message: String) : IllegalArgumentException(message)

/**
 * App 推送本地下载（push-of-local-downloads）：App 把已下载漫画的元数据与逐页
 * 图片推送到服务器，落盘布局与服务器下载器一致——
 * `downloads/<gid>/%04d.<ext>`（1-based 4 位，DownloadService 同款命名），阅读
 * 代理（ImageProxyController）cache miss 时优先回退该目录。
 *
 * - [initUpload]：findByGid 判重，非 force 冲突 → success=false（App 跳过该本）；
 *   upsert 行 state=2（DOWNLOADING），downloadDir 固定派生自 config.download.path。
 * - [storePage]：覆盖写单页，扩展名白名单 jpg/jpeg/png/gif/webp（保留原扩展名）。
 * - [completeUpload]：存在即更新，state=3（FINISHED）+ total/done。
 */
@Service
class DownloadUploadService(
    private val downloadRepository: DownloadInfoRepository,
    private val config: SiteCoreConfigProperties,
    private val serverConfig: ServerConfigService,
) {
    private val logger = LoggerFactory.getLogger(DownloadUploadService::class.java)

    /** 页面文件名：`%04d.<ext>`（1-based）。 */
    private val pageFilePattern = Pattern.compile("^(\\d{4})\\.(?:jpg|jpeg|png|gif|webp)$", Pattern.CASE_INSENSITIVE)

    /**
     * 注册/更新下载行。gid 行已存在且 !force → success=false（返回既有页清单，
     * 不写库）；否则 upsert（行字段来自请求，state=2，downloadDir = downloads/<gid>）。
     */
    fun initUpload(gid: Long, request: UploadInitRequest, username: String): UploadInitResponse {
        if (!isUploadEnabled()) {
            return UploadInitResponse(success = false, message = "Upload disabled")
        }
        val existing = downloadRepository.findByGid(gid)
        if (existing != null && !request.force) {
            return UploadInitResponse(
                success = false,
                message = "gid=$gid 已存在下载行；如需覆盖请用 force=true",
                existingPages = existingPages(gid),
            )
        }

        // 2026-08-30：目录命名对齐 Android——`{gid}-{title}`（人读可辨）。
        val downloadDir = File(
            config.download.path,
            DownloadDirs.dirName(gid, request.title)
        )
        downloadDir.mkdirs()

        val now = System.currentTimeMillis()
        val entity = existing ?: DownloadInfoEntity()
        entity.apply {
            this.gid = gid
            token = request.token
            title = request.title
            titleJpn = request.titleJpn
            thumb = request.thumb
            category = request.category
            uploader = request.uploader
            rating = request.rating
            simpleTags = request.simpleTags
            pages = request.pages
            state = 2
            total = 0
            done = 0
            label = request.label
            this.username = username
            this.downloadDir = downloadDir.absolutePath
            time = now
            lastModified = now
        }
        downloadRepository.save(entity)
        return UploadInitResponse(success = true, message = "ok", existingPages = existingPages(gid))
    }

    /**
     * 扫描 `downloads/<gid>(-{title})` 下已按 `%04d.<ext>` 命名存在的页序号
     * （1-based，断点续传用）。目录按行内 downloadDir 定位，行缺省回落
     * `<root>/<gid>`。
     */
    fun existingPages(gid: Long): List<Int> {
        val dir = rowDirOrRoot(gid)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.mapNotNull { file ->
                val m = pageFilePattern.matcher(file.name)
                if (m.matches() && file.isFile && file.length() > 0) m.group(1).toInt() else null
            }?.sorted() ?: emptyList()
    }

    /**
     * 落单页：`downloads/<gid>/%04d.<原扩展名>`（覆盖写）。扩展名取自上传文件名
     * 白名单校验（大小写不敏感）；page 必须 >= 1。
     * @throws IllegalArgumentException page < 1 或扩展名不在白名单（控制器转 400）
     */
    fun storePage(gid: Long, page: Int, filename: String?, bytes: ByteArray) {
        if (!isUploadEnabled()) {
            throw IllegalArgumentException("upload disabled")
        }
        require(page >= 1) { "page must be >= 1" }
        val ext = extensionOf(filename)
            ?: throw IllegalArgumentException("unsupported image extension: ${filename ?: "(none)"}")
        val dir = rowDirOrRoot(gid)
        dir.mkdirs()
        val target = File(dir, "%04d.$ext".format(page))
        target.writeBytes(bytes)
    }

    /**
     * 页落盘目录：优先下载行内 downloadDir（绝对路径，含 `{gid}-{title}` 命名），
     * 行缺失时回落 `<root>/<gid>`（旧布局兼容）。
     */
    private fun rowDirOrRoot(gid: Long): File {
        val row = downloadRepository.findByGid(gid)
        if (row?.downloadDir?.isNotBlank() == true) {
            val stored = File(row.downloadDir)
            if (stored.isAbsolute) return stored
        }
        return File(config.download.path, gid.toString())
    }

    /** 收尾：行存在即更新 state=3 + total/done；不存在返回 false（控制器转 404）。
     *  MASTER-2026-08-22 S3：受 download.upload_enabled 门控——关闭时抛
     *  [UploadDisabledException]（控制器转 403），与 storePage 同语义，
     *  不允许绕过开关 finalize 存量行。 */
    fun completeUpload(gid: Long, request: UploadCompleteRequest): Boolean {
        if (!isUploadEnabled()) {
            throw UploadDisabledException("upload disabled")
        }
        val entity = downloadRepository.findByGid(gid) ?: return false
        entity.state = 3
        entity.total = request.total
        entity.done = request.done
        entity.lastModified = System.currentTimeMillis()
        downloadRepository.save(entity)
        return true
    }

    /** 从上传文件名取小写扩展名；白名单外返回 null。 */
    private fun extensionOf(filename: String?): String? {
        if (filename == null) return null
        val dot = filename.lastIndexOf('.')
        if (dot < 0 || dot == filename.length - 1) return null
        val ext = filename.substring(dot + 1).lowercase()
        return if (ext in SUPPORTED_EXTENSIONS) ext else null
    }

    /** 上传开关（openapi.yaml「upload disabled → 400」）；默认开启。 */
    private fun isUploadEnabled(): Boolean =
        serverConfig.getBoolean(ServerConfigService.KEY_UPLOAD_ENABLED, true)

    private companion object {
        val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp")
    }
}
