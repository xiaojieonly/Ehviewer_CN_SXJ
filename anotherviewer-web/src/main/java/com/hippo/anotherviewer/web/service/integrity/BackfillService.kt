package com.hippo.anotherviewer.web.service.integrity

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.entity.PageFileHashEntity
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import com.hippo.anotherviewer.web.repository.PageFileHashRepository
import com.hippo.anotherviewer.web.service.DownloadDirIndex
import com.hippo.anotherviewer.web.service.DownloadDirs
import com.hippo.anotherviewer.web.service.ServerConfigService
import com.hippo.anotherviewer.web.service.SyncClock
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileInputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.TreeMap

/**
 * TOFU 回填（文件完整性 Wave 2 / S4）：对**既有**下载目录补建 SHA-256 基线。
 *
 * 语义（TOFU = Trust On First Use，只补缺）：
 * - 目录解析一律走 [DownloadDirIndex]（磁盘目录是 `{gid}-{title}` 形态而行内
 *   download_dir 多为 `{gid}` 形态，裸 `File(root, gid)` 找不到目录）；
 * - 逐页文件过 V 门（[VGate.check] 流式版，O(1) 内存）：过 → SHA-256 →
 *   `page_file_hash` 补行（origin=import，**已存在的 (gid,page) 行绝不覆盖**）；
 *   不过 → 落 verdict=struct_bad 行（hash 为空串 = 无基线），同样不覆盖已有行；
 * - 单遍读盘：DigestInputStream 与 V 门共用同一条读路径，读一遍同时算哈希；
 * - 限速：累计字节 + 睡眠节流（参数留给 Wave 3 StorageTuning 参数化）；
 * - 断点：进度写 [ServerConfigService] KV（低频，每 [CHECKPOINT_EVERY_FILES] 个
 *   文件一次，写即失效其 60s 缓存——高频写会反复打缓存，故刻意稀疏）；中断后
 *   续跑只处理剩余；全部完成清 KV。断点重启窗口内丢进度是安全的：重复扫描的
 *   文件按「已有行不覆盖」幂等跳过。
 * - dry-run：只统计，不写库、不写/清断点、不忽略（也不消费）断点。
 *
 * 纯显式维护动作：本服务不做任何调度/自动触发，仅由 Wave 3 管理端点调用。
 */
@Service
class BackfillService(
    private val config: SiteCoreConfigProperties,
    private val dirIndex: DownloadDirIndex,
    private val hashRepository: PageFileHashRepository,
    private val downloadRepository: DownloadInfoRepository,
    private val serverConfigService: ServerConfigService,
    // v4（WP2）：pages 修正/完成化的水位由服务器统一打戳（业务时间字段不动）。
    // 可空缺省仅为既有直构测试的源兼容保留——null 时回退墙钟。
    private val syncClock: SyncClock? = null,
) {
    private val logger = LoggerFactory.getLogger(BackfillService::class.java)

    /** v4 打戳助手：有 SyncClock 走服务器单调时钟，直构测试场景回落墙钟。 */
    private fun stamp(): Long = syncClock?.next() ?: System.currentTimeMillis()

    // ── 公共结果类型（Wave 3 端点 / F1 面板直接序列化）──────────────────────

    /** V 门拒绝的一个页文件（不建基线，已落 verdict=struct_bad 行）。 */
    data class StructBadPage(
        val gid: Long,
        /** 0-based 页号（与 page_file_hash.page 同口径）。 */
        val page: Int,
        /** 磁盘文件名（4/8 位数字 + 扩展名，定位用）。 */
        val fileName: String,
        /** 拒因：V1_MAGIC | V2_TAIL。 */
        val reason: String,
    )

    /**
     * @param gids 本轮实际访问到目录的画廊数。
     * @param scanned 本轮真正读了盘并过 V 门的页文件数（断点跳过的不计）。
     * @param accepted V 门通过（含其中因已有基线被跳过的）。
     * @param rejected V 门拒绝（struct_bad）。
     * @param skipped V 门通过但 (gid,page) 已有基线 → TOFU 只补缺，未覆盖。
     * @param ioErrors 读盘 IO 异常的文件数（不计入 rejected：V 门未给出判定）。
     * @param rejectedPages 拒绝明细（按扫描顺序）。
     * @param resumedFromCheckpoint 本轮是否从 KV 断点续跑。
     * @param dryRun 与入参一致。
     */
    data class BackfillStats(
        val gids: Int,
        val scanned: Int,
        val accepted: Int,
        val rejected: Int,
        val skipped: Int,
        val ioErrors: Int,
        val rejectedPages: List<StructBadPage>,
        val resumedFromCheckpoint: Boolean,
        val dryRun: Boolean,
    )

    /**
     * @param gids 检查过的 download_info 行涉及的画廊数。
     * @param rowsExamined 检查的 download_info 行数（仅存活行）。
     * @param rowsPagesUpdated pages 字段与磁盘页数不符而被回填的行数。
     * @param rowsCompleted state=1(WAIT) 且 total=0 的遗留行被按磁盘页数完成化
     *   （state=3、total=done=磁盘页数）的行数。
     * @param dryRun 与入参一致；dry-run 时两个计数表示「将要」改的行数。
     */
    data class ApplyPageCountStats(
        val gids: Int,
        val rowsExamined: Int,
        val rowsPagesUpdated: Int,
        val rowsCompleted: Int,
        val dryRun: Boolean,
    )

    /**
     * 测试缝：限制单轮处理的文件数（到达即按断点语义落盘并提前返回），
     * 模拟进程中断；null = 不限。生产路径恒为 null。
     */
    @Volatile
    internal var testStopAfterFiles: Int? = null

    // ── 主入口：TOFU 哈希回填 ────────────────────────────────────────────────

    /**
     * 扫描 [gid]（或全部，null）画廊的下载目录，逐文件 V 门 + SHA-256 补基线。
     *
     * @param gid 指定画廊 gid；null = 全库（downloads 根下全部本应用布局目录）。
     * @param dryRun true = 只统计不写任何库表/KV。
     * @param rateLimitMbPerSec 读盘限速 MB/s；≤0 = 不限速。
     */
    fun backfillHashes(
        gid: Long? = null,
        dryRun: Boolean = false,
        rateLimitMbPerSec: Int = 400,
    ): BackfillStats = synchronized(this) {
        val startNanos = System.nanoTime()
        val targetGids = if (gid != null) listOf(gid) else enumerateRootGids()

        // 断点只服务于全库扫描（长任务）；显式单画廊重扫只在断点恰好指向同一
        // 画廊时续跑，否则视为全新一轮（已有行不覆盖使重扫天然幂等且便宜）。
        val checkpoint = if (!dryRun) readCheckpoint()?.takeIf { gid == null || it.gid == gid } else null
        val resumed = checkpoint != null

        var scanned = 0
        var accepted = 0
        var rejected = 0
        var skipped = 0
        var ioErrors = 0
        val rejectedPages = mutableListOf<StructBadPage>()
        var gidsVisited = 0
        var bytesRead = 0L
        var sinceCheckpoint = 0
        var lastPosition: Checkpoint? = null
        var stopped = false

        loop@ for (target in targetGids) {
            // 断点之前的画廊整本跳过；断点画廊内按文件位置跳过。
            if (checkpoint != null && target < checkpoint.gid) continue
            val dir = dirIndex.dirFor(target) ?: continue
            gidsVisited++
            for (pf in listPageFiles(dir)) {
                if (checkpoint != null && target == checkpoint.gid &&
                    (pf.page < checkpoint.page || (pf.page == checkpoint.page && pf.fileName <= checkpoint.fileName))
                ) continue

                val page = pf.page - 1 // 文件名 1-based → 基线页号 0-based
                val file = File(dir, pf.fileName)
                val result = runCatching { vgateAndHash(file) }
                bytesRead += file.length()
                scanned++
                sinceCheckpoint++
                lastPosition = Checkpoint(target, pf.page, pf.fileName)
                result.fold(
                    onSuccess = { (verdict, hash) ->
                        val existing = hashRepository.findByGidAndPage(target, page)
                        when (verdict) {
                            is VGateResult.Accept -> {
                                accepted++
                                if (existing != null) {
                                    skipped++ // TOFU 只补缺：既有行一律不覆盖
                                } else if (!dryRun) {
                                    hashRepository.save(
                                        newRow(target, page, pf.ext, file.length(), hash)
                                    )
                                }
                            }
                            is VGateResult.Reject -> {
                                rejected++
                                rejectedPages += StructBadPage(target, page, pf.fileName, verdict.reason.name)
                                if (existing == null && !dryRun) {
                                    // 结构坏：落 verdict=struct_bad 行（hash 空 = 无基线），
                                    // 让巡检/修复归因看得到这页的坏状态。
                                    hashRepository.save(
                                        newRow(target, page, pf.ext, file.length(), hash = "")
                                            .apply { this.verdict = VERDICT_STRUCT_BAD }
                                    )
                                }
                            }
                        }
                    },
                    onFailure = { e ->
                        ioErrors++
                        logger.warn("Backfill: failed to read {}: {}", file.absolutePath, e.toString())
                    },
                )

                if (sinceCheckpoint >= CHECKPOINT_EVERY_FILES) {
                    lastPosition?.let { if (!dryRun) writeCheckpoint(it) }
                    sinceCheckpoint = 0
                }
                throttle(bytesRead, rateLimitMbPerSec, startNanos)

                val limit = testStopAfterFiles
                if (limit != null && scanned >= limit) {
                    stopped = true
                    break@loop
                }
            }
        }

        // 收尾：中断 → 落断点（当前已处理位置）；跑完 → 清断点。
        // 断点只由全库扫描写/清；显式单画廊扫描不产生断点（除非它在续跑断点画廊）。
        if (!dryRun && (gid == null || resumed)) {
            if (stopped) lastPosition?.let { writeCheckpoint(it) } else clearCheckpoint()
        }

        val stats = BackfillStats(
            gids = gidsVisited,
            scanned = scanned,
            accepted = accepted,
            rejected = rejected,
            skipped = skipped,
            ioErrors = ioErrors,
            rejectedPages = rejectedPages,
            resumedFromCheckpoint = resumed,
            dryRun = dryRun,
        )
        logger.info(
            "Backfill hashes done: gid={} dryRun={} resumed={} gids={} scanned={} accepted={} rejected={} skipped={} ioErrors={} stopped={} {}ms",
            gid, dryRun, resumed, gidsVisited, scanned, accepted, rejected, skipped, ioErrors, stopped,
            (System.nanoTime() - startNanos) / 1_000_000,
        )
        stats
    }

    // ── 磁盘页数（Wave 3 管理端点的显式维护动作）────────────────────────────

    /**
     * 数 [gid] 画廊磁盘上的页文件数（按页号去重；4/8 位文件名都认）。
     * 目录缺失/不可读返回 0。目录经 [DownloadDirIndex] 解析。
     */
    fun pageCountFromDisk(gid: Long): Int {
        val dir = dirIndex.dirFor(gid) ?: return 0
        val pages = HashSet<Int>()
        dir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val match = PAGE_FILE_PATTERN.matchEntire(file.name) ?: return@forEach
            val pageNo = match.groupValues[1].toIntOrNull() ?: return@forEach
            pages += pageNo
        }
        return pages.size
    }

    /**
     * 把磁盘页数回填进 download_info 行：pages 字段校正 + state=1(WAIT) 且
     * total=0 的遗留行按磁盘页数完成化（state=3、total=done=磁盘页数）。
     *
     * 保守面：磁盘目录缺失或 0 页的画廊一律不动行（无从考证，绝不把 pages
     * 归零）；既有 pages 已正确的行不动；实际写入 bump lastModified（增量
     * pull 把修正传播到设备）。dry-run 只统计将要改的行。
     */
    fun applyDiskPageCounts(gid: Long? = null, dryRun: Boolean = false): ApplyPageCountStats = synchronized(this) {
        val rows = if (gid != null) {
            downloadRepository.findAllByGid(gid).filter { !it.deleted }
        } else {
            downloadRepository.findAllByDeletedFalseOrderById()
        }
        val diskCounts = HashMap<Long, Int>()
        var rowsPagesUpdated = 0
        var rowsCompleted = 0
        for (row in rows) {
            val count = diskCounts.getOrPut(row.gid) { pageCountFromDisk(row.gid) }
            if (count <= 0) continue
            var changed = false
            if (row.pages != count) {
                changed = true
                rowsPagesUpdated++
            }
            val willComplete = row.state == STATE_WAIT && row.total == 0
            if (willComplete) {
                changed = true
                rowsCompleted++
            }
            // 只在真实写入分支触碰实体：dry-run 不改托管实体（事务内测试 /
            // 外层事务会把内存脏字段冲进库），保证零写入语义。
            if (changed && !dryRun) {
                if (row.pages != count) row.pages = count
                if (willComplete) {
                    row.total = count
                    row.done = count
                    row.state = STATE_FINISHED
                }
                row.lastModified = stamp()
                downloadRepository.save(row)
            }
        }
        val stats = ApplyPageCountStats(
            gids = rows.map { it.gid }.distinct().size,
            rowsExamined = rows.size,
            rowsPagesUpdated = rowsPagesUpdated,
            rowsCompleted = rowsCompleted,
            dryRun = dryRun,
        )
        logger.info(
            "Apply disk page counts: gid={} dryRun={} rows={} pagesUpdated={} completed={}",
            gid, dryRun, rows.size, rowsPagesUpdated, rowsCompleted,
        )
        stats
    }

    // ── 内部实现 ─────────────────────────────────────────────────────────────

    /** 磁盘上一个页文件（文件名页号 1-based）。 */
    private data class DiskPage(val page: Int, val fileName: String, val ext: String)

    /** 断点位置：画廊 + 文件名页号 + 文件名（页号同页多扩展时按文件名稳定排序）。 */
    private data class Checkpoint(val gid: Long, val page: Int, val fileName: String)

    /** 单遍读盘：V 门与 SHA-256 共用一条读路径；返回 (判定, 哈希 hex)。 */
    private fun vgateAndHash(file: File): Pair<VGateResult, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val verdict = VGate.check(DigestInputStream(input, digest))
            val hex = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
            return verdict to hex
        }
    }

    private fun newRow(gid: Long, page: Int, ext: String, size: Long, hash: String) =
        PageFileHashEntity().apply {
            this.gid = gid
            this.page = page
            this.ext = ext
            this.size = size
            this.hash = hash
            algo = ALGO_SHA256
            origin = ORIGIN_IMPORT
            createdAt = System.currentTimeMillis()
        }

    /** downloads 根下全部本应用布局目录的 gid（去重升序）；根缺失返回空。 */
    private fun enumerateRootGids(): List<Long> {
        val root = File(config.download.path)
        if (!root.isDirectory) return emptyList()
        return root.listFiles()
            ?.filter { it.isDirectory && DownloadDirs.isOursDir(it.name) }
            ?.mapNotNull { DownloadDirs.parseGid(it.name) }
            ?.distinct()
            ?.sorted()
            .orEmpty()
    }

    /**
     * 目录内页文件清单（每页唯一、页号升序）。P2-5：同 (gid,page) 存在 4 位与
     * 8 位两个文件时，只处理 [DownloadDirIndex.selectPageFile] 选中的那个——
     * 与下载目录索引读取、复验比对、修复覆写三处恒一致，绝不给未选中的副本
     * 建/跳过基线（否则基线可能描述的是修复管线不会覆写的那个文件）；同页多
     * 文件一律日志告警。
     */
    private fun listPageFiles(dir: File): List<DiskPage> {
        val listing = dir.listFiles()?.filter { it.isFile } ?: return emptyList()
        val byPage = TreeMap<Int, MutableList<File>>()
        for (file in listing) {
            val match = PAGE_FILE_PATTERN.matchEntire(file.name) ?: continue
            val pageNo = match.groupValues[1].toIntOrNull() ?: continue
            byPage.getOrPut(pageNo) { mutableListOf() }.add(file)
        }
        val pages = mutableListOf<DiskPage>()
        for ((pageNo, candidates) in byPage) {
            val chosen = DownloadDirIndex.selectPageFile(candidates)
            if (candidates.size > 1) {
                logger.warn(
                    "Backfill: duplicate page files for page {} in {}: {} (using {})",
                    pageNo, dir.absolutePath, candidates.map { it.name }, chosen?.name,
                )
            }
            if (chosen != null) pages.add(DiskPage(pageNo, chosen.name, chosen.extension.lowercase()))
        }
        return pages
    }

    // ── 断点 KV（紧凑管道格式：v1|gid|page|fileName）─────────────────────────

    private fun readCheckpoint(): Checkpoint? {
        val raw = serverConfigService.get(KEY_CHECKPOINT, "")
        if (raw.isBlank()) return null
        val parts = raw.split("|", limit = 4)
        if (parts.size != 4 || parts[0] != CHECKPOINT_FORMAT) {
            logger.warn("Backfill: ignoring malformed checkpoint [{}]", raw)
            return null
        }
        val gid = parts[1].toLongOrNull() ?: return null
        val page = parts[2].toIntOrNull() ?: return null
        return Checkpoint(gid, page, parts[3])
    }

    private fun writeCheckpoint(position: Checkpoint) {
        serverConfigService.set(
            KEY_CHECKPOINT,
            "$CHECKPOINT_FORMAT|${position.gid}|${position.page}|${position.fileName}",
        )
    }

    private fun clearCheckpoint() {
        serverConfigService.findByKeyStartingWith(KEY_CHECKPOINT).forEach { serverConfigService.delete(it) }
    }

    // ── 限速：累计字节 + 睡眠补齐预算（Wave 3 StorageTuning 参数化接手）──────

    private fun throttle(bytesRead: Long, rateLimitMbPerSec: Int, startNanos: Long) {
        if (rateLimitMbPerSec <= 0 || bytesRead <= 0) return
        val budgetNanos = bytesRead * 1_000_000_000L / (rateLimitMbPerSec.toLong() * 1024 * 1024)
        val sleepNanos = budgetNanos - (System.nanoTime() - startNanos)
        if (sleepNanos > 0) {
            runCatching { Thread.sleep(sleepNanos / 1_000_000, (sleepNanos % 1_000_000).toInt()) }
        }
    }

    companion object {
        /** 断点 KV 键（ServerConfigService；v1|gid|page|fileName 管道格式）。 */
        const val KEY_CHECKPOINT = "integrity.backfill.checkpoint"

        private const val CHECKPOINT_FORMAT = "v1"

        /** 断点 KV 写入节奏（文件数）：写即失效 ServerConfigService 60s 缓存，刻意低频。 */
        private const val CHECKPOINT_EVERY_FILES = 50

        private const val ORIGIN_IMPORT = "import"
        private const val ALGO_SHA256 = "sha256"
        private const val VERDICT_STRUCT_BAD = "struct_bad"

        /** Android `DownloadInfo.STATE_*`：1=WAIT，3=FINISHED。 */
        private const val STATE_WAIT = 1
        private const val STATE_FINISHED = 3

        /** 页文件名：`{4,}位数字.{扩展}`，与 DownloadDirIndex 索引口径一致（4/8 位混存都认）。 */
        private val PAGE_FILE_PATTERN = Regex("^(\\d{4,})\\.(jpg|jpeg|png|gif|webp)$", RegexOption.IGNORE_CASE)
    }
}
