package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.UploadCompleteRequest
import com.hippo.anotherviewer.web.dto.UploadInitRequest
import com.hippo.anotherviewer.web.dto.UploadInitResponse
import com.hippo.anotherviewer.web.entity.DownloadInfoEntity
import com.hippo.anotherviewer.web.entity.PageFileHashEntity
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import com.hippo.anotherviewer.web.repository.PageFileHashRepository
import com.hippo.anotherviewer.web.service.integrity.VGate
import com.hippo.anotherviewer.web.service.integrity.VGateResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.security.MessageDigest
import java.util.regex.Pattern

/** MASTER-2026-08-22 S3：upload_enabled 关闭时由 [DownloadUploadService] 抛出。 */
class UploadDisabledException(message: String) : IllegalArgumentException(message)

/**
 * App 推送本地下载（push-of-local-downloads）：App 把已下载漫画的元数据与逐页
 * 图片推送到服务器，落盘布局与服务器下载器一致——
 * `downloads/<gid>/%08d.<ext>`（1-based 8 位，Android/DownloadService 同款命名；
 * 读取端兼容历史 `%04d` 旧文件），阅读代理（ImageProxyController）cache miss
 * 时优先回退该目录。
 *
 * - [initUpload]：findAllByGid 判重，非 force 冲突 → success=false（App 跳过该本）；
 *   force 覆盖（含墓碑行，复位 deleted=false）；upsert 行 state=2（DOWNLOADING），
 *   downloadDir 固定派生自 config.download.path。
 * - [storePage]：V 门校验后覆盖写单页，扩展名白名单 jpg/jpeg/png/gif/webp
 *   （保留原扩展名），并建/刷新 SHA-256 基线（origin=upload）。
 * - [completeUpload]：存在即更新，state=3（FINISHED）+ total/done；终态写前在
 *   flush 锁内丢弃进度批量器的待写帧（旧 done 帧不得覆盖 state=3 行，二期 Wave 2
 *   E2E 缝隙修复）。
 */
@Service
class DownloadUploadService(
    private val downloadRepository: DownloadInfoRepository,
    // 文件完整性 Wave 2（S3）：上传页落盘时建/刷新 SHA-256 基线（origin=upload）。
    private val pageFileHashRepository: PageFileHashRepository,
    private val config: SiteCoreConfigProperties,
    private val serverConfig: ServerConfigService,
    // 二期 Wave 2 E2E 缝隙：completeUpload 终态写前丢弃进度批量器的待写帧。
    // 默认直构（绑定本仓库）仅为既有直构测试的源兼容保留；Spring 装配按类型
    // 注入容器级单例（与 DownloadService 同款）。
    private val progressPersister: DownloadProgressPersister = DownloadProgressPersister(downloadRepository),
    // v4（WP2）：上传写入路径与 sync 合并共用服务器打戳时钟。可空缺省仅为既有直构
    // 测试的源兼容保留——null 时回退墙钟。
    private val syncClock: SyncClock? = null,
) {
    private val logger = LoggerFactory.getLogger(DownloadUploadService::class.java)

    /** v4 打戳助手：有 SyncClock 走服务器单调时钟，直构测试场景回落墙钟。 */
    private fun stamp(): Long = syncClock?.next() ?: System.currentTimeMillis()

    /**
     * 页面文件名：`%04d` 历史布局与 `%08d` 现行布局都认（U2：写入端 2026-08-30
     * 起统一 `%08d` 对齐 Android，本扫描此前只认 4 位 → 断点续传 existingPages
     * 恒空集、每次全量重传）。位数下限与 DownloadDirIndex FILE_NAME_PATTERN
     * 的 `\d{4,}` 同口径；1-based。
     */
    private val pageFilePattern = Pattern.compile("^(\\d{4,})\\.(?:jpg|jpeg|png|gif|webp)$", Pattern.CASE_INSENSITIVE)

    /**
     * 注册/更新下载行。gid 行已存在且 !force → success=false（返回既有页清单，
     * 不写库）；否则 upsert（行字段来自请求，state=2，downloadDir = downloads/<gid>）。
     * force 覆盖墓碑行时复位 deleted=false（U4：否则重传成功后行仍是墓碑，
     * Web 列表永不可见）。
     */
    fun initUpload(gid: Long, request: UploadInitRequest, username: String): UploadInitResponse {
        if (!isUploadEnabled()) {
            return UploadInitResponse(success = false, message = "Upload disabled")
        }
        // U5（A7-3 同款）：List 化查找 + firstOrNull，同 gid 多行脏数据不再
        // 抛 IncorrectResultSizeDataAccessException 毒化上传通道。
        val existing = downloadRepository.findAllByGid(gid).firstOrNull()
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

        // time 是业务时间字段（上传时刻），保留墙钟；lastModified 是同步水位，
        // v4 起由服务器打戳。
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
            // U4：本路径只会在「新建行」（默认 false）或 force 覆盖时走到——
            // 覆盖墓碑行必须复位 deleted，让重传的行回到 Web 列表（与
            // addDownload 的复活语义对齐）。
            deleted = false
            lastModified = stamp()
        }
        downloadRepository.save(entity)
        return UploadInitResponse(success = true, message = "ok", existingPages = existingPages(gid))
    }

    /**
     * 扫描 `downloads/<gid>(-{title})` 下已按 `%04d`/`%08d`.<ext> 命名存在的页序号
     * （1-based，断点续传用；U2：两种位数都认）。目录按行内 downloadDir 定位，
     * 行缺省回落 `<root>/<gid>`。
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
     * 落单页：`downloads/<gid>/%08d.<原扩展名>`（覆盖写）。扩展名取自上传文件名
     * 白名单校验（大小写不敏感）；page 必须 >= 1。
     * S3 写入钩子（文件完整性 Wave 2）：落盘前字节过 V 门——拒收则该页不落盘、
     * 不建基线，抛 [IllegalArgumentException]（按本服务既有失败语义 → 控制器
     * 400）；通过则写文件 + SHA-256 基线 upsert（origin=upload；覆盖已有基线 =
     * 刷新并把 verdict/last_verified_at 复位为未巡检）。
     * @throws IllegalArgumentException page < 1、扩展名不在白名单或 V 门拒收
     *   （控制器转 400）
     */
    fun storePage(gid: Long, page: Int, filename: String?, bytes: ByteArray) {
        if (!isUploadEnabled()) {
            throw IllegalArgumentException("upload disabled")
        }
        require(page >= 1) { "page must be >= 1" }
        val ext = extensionOf(filename)
            ?: throw IllegalArgumentException("unsupported image extension: ${filename ?: "(none)"}")
        // S3：V 门按内容嗅探（与扩展名无关）。拒收 = 拒收该页，先于任何落盘动作。
        val gate = VGate.check(bytes)
        if (gate is VGateResult.Reject) {
            throw IllegalArgumentException(
                "page $page failed integrity gate: ${gate.reason}" + (gate.format?.let { " ($it)" } ?: "")
            )
        }
        val dir = rowDirOrRoot(gid)
        dir.mkdirs()
        val target = File(dir, "%08d.$ext".format(page))
        target.writeBytes(bytes)
        upsertPageHash(gid, page - 1, ext, bytes, ORIGIN_UPLOAD)
    }

    /**
     * SHA-256 基线 upsert（Wave 1 契约：先 [PageFileHashRepository.findByGidAndPage]
     * 取旧行改字段再 save——复合主键覆盖语义）。@param page0 0-based 页号。
     * 哈希落库失败只记日志不上抛：页已落盘，基线 best-effort（可由巡检/TOFU 回填）。
     */
    private fun upsertPageHash(gid: Long, page0: Int, ext: String, bytes: ByteArray, writeOrigin: String) {
        try {
            val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            val now = System.currentTimeMillis()
            val row = pageFileHashRepository.findByGidAndPage(gid, page0) ?: PageFileHashEntity().also {
                it.gid = gid
                it.page = page0
            }
            row.ext = ext
            row.size = bytes.size.toLong()
            row.hash = sha256
            row.algo = "sha256"
            row.origin = writeOrigin
            row.createdAt = now
            row.lastVerifiedAt = null
            row.verdict = null
            pageFileHashRepository.save(row)
        } catch (e: Exception) {
            logger.warn("Failed to persist page hash baseline for gid={} page={}: {}", gid, page0, e.message)
        }
    }

    /**
     * 页落盘目录：优先下载行内 downloadDir（绝对路径，含 `{gid}-{title}` 命名），
     * 行缺失时回落 `<root>/<gid>`（旧布局兼容）。
     */
    private fun rowDirOrRoot(gid: Long): File {
        // U5：List 化查找 + firstOrNull（多行脏数据容错，同 initUpload）。
        val row = downloadRepository.findAllByGid(gid).firstOrNull()
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
        // U5：List 化查找 + firstOrNull（多行脏数据容错，同 initUpload）。
        val entity = downloadRepository.findAllByGid(gid).firstOrNull() ?: return false
        // 二期 Wave 2 E2E 缝隙：同 taskId 在进度批量器 pending 里的旧 done 帧会在
        // 下一 tick 覆盖刚写的 state=3 行（drain 守卫只跳 0/4，**不跳 3**）。
        // 与 deleteDownload 墓碑化同款 discard（终态 done 以请求为准，旧帧无意义）；
        // 包在 flush 锁内是与在途 tick 串行化：先让在途 drain 完成、再丢弃、再写
        // 终态，关闭「tick 摘帧 → 终态写 → tick 旧帧落库」的交错窗口。
        progressPersister.locked {
            progressPersister.discard(entity.id)
            entity.state = 3
            entity.total = request.total
            entity.done = request.done
            entity.lastModified = stamp()
            downloadRepository.save(entity)
        }
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

        /** 页文件基线 origin：App 推上传来的页（文件完整性 Wave 2 S3）。 */
        const val ORIGIN_UPLOAD = "upload"
    }
}
