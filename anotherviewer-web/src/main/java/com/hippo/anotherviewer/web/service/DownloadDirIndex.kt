package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * A single located pushed page file: 1-based [page], image [ext], stored
 * [size] and the ACTUAL [fileName] on disk (Android-aligned `%08d.<ext>` or
 * legacy `%04d.<ext>` — both accepted at scan time; [fileName] is the source
 * of truth so legacy files keep serving without rename).
 */
data class PageRef(
    val gid: Long,
    val page: Int,
    val ext: String,
    val size: Long,
    /** Actual file name on disk (either 4- or 8-digit layout). */
    val fileName: String,
)

/**
 * In-memory index of the App-pushed download layout `{downloads root}/<gid>/%04d.<ext>`
 * (files 1-based, standard image extensions in jpg/jpeg/png/gif/webp priority).
 *
 * P-S3（Wave 1，HDD 迁移硬前置）后的生命周期：
 *
 * - **启动**：快照开关（`anotherviewer.download.dir-index-snapshot`，默认开）
 *   开且 [DirIndexSnapshotStore] 里有校验通过的快照（格式版本 + CRC32 + 下载根
 *   匹配）→ 毫秒级直接载入（零 walk），随后由单条 daemon 后台线程做一次全量
 *   校验 walk（不信任快照 mtime，逐目录重扫 → diff → 交换 → 覆写快照）。快照
 *   缺失/损坏/根不符 → 同步全量 walk（原行为）并落一份新快照。
 * - **查询**：索引命中自验缓存目录（exists + mtime），零根遍历；miss/自验失败
 *   才按需重扫该 gid 目录（rename/delete 自愈，不等待 TTL）——语义与 P-S3 前
 *   一致。
 * - **自动感知（TTL）**：根列表指纹检查仍按
 *   `anotherviewer.download.dir-index-refresh-ms`（默认 30s）节流，但指纹变化
 *   **永远不在请求线程重建**——只调度后台重建，请求继续用旧索引（接受 ≤ 一轮
 *   后台重建的陈旧）。后台完成后 **volatile 交换整个索引引用**（copy-on-write，
 *   查询方持有旧引用恒自洽，绝无半新半旧）。间隔 ≤0 每请求都检（旧行为，测试
 *   用）。
 * - **增量**：TTL 触发的后台重建按目录级指纹（目录 mtime + 路径）复用未变化
 *   目录的条目，只重扫新增/变化的目录，根列表里消失的目录直接丢弃；快照载入
 *   后的首次校验 walk 是全量重扫。目录 mtime 粒度风险（个别 FS 不动 mtime）由
 *   既有 per-gid 自愈（查询时 mtime 自验 + 生命周期 invalidate）消化。
 * - **快照**：每次 rebuild 成功后原子覆写（temp+rename）；写失败静默降级，下
 *   次重建再写。显式 [refresh]（全部下载前重扫）保持同步全量，语义不变。
 *
 * Shared concurrently; safe for the WebUI single-user model.
 */
@Service
class DownloadDirIndex(
    private val config: SiteCoreConfigProperties,
    /**
     * 根指纹检查的节流间隔 ms；≤0 = 每次都检（旧行为，测试用）。节流只决定
     * 「何时调度后台重建」，不再触发请求线程内的同步重建（P-S3）。
     */
    @Value("\${anotherviewer.download.dir-index-refresh-ms:30000}")
    private val refreshIntervalMs: Long = 0,
    /**
     * P-S3：快照开关（默认开，零 yml 改动）。关 = 恒走同步全量 walk、不读不写
     * 快照文件（P-S3 前的完整行为）。
     */
    @Value("\${anotherviewer.download.dir-index-snapshot:true}")
    private val snapshotEnabled: Boolean = true,
) {
    private val logger = LoggerFactory.getLogger(DownloadDirIndex::class.java)

    /**
     * Extension priority; must mirror ImageProxyController.PUSHED_EXTENSIONS.
     */
    internal val extOrder: List<String> get() = EXT_ORDER

    private class DirEntry(
        val gid: Long,
        /** Cached directory handle: hit-path self-validation avoids findDir (a root listFiles). */
        val dir: File,
        val dirMtime: Long,
        /** 1-based page → located page ref (selection via [selectPageFile]). */
        val pageFiles: Map<Int, PageRef>,
    ) {
        val pageCount: Int get() = pageFiles.size
    }

    /**
     * P-S3：整表 copy-on-write。读方只做 volatile 读（持到的引用要么全旧要么
     * 全新，绝不半新半旧）；一切变更（后台 rebuild 整表交换、invalidate/自愈
     * 的单条增删）在 [indexLock] 内「复制 → 改 → 交换」，避免并发丢更新。
     */
    @Volatile
    private var indexRef: Map<Long, DirEntry> = emptyMap()

    private val indexLock = Any()

    private val root: File get() = File(config.download.path)

    /** 上次全量扫描的根列表指纹（目录名集合）；查询时轻量检测以触发后台重建。 */
    @Volatile
    private var lastDirFingerprint: Set<String>? = null

    /** 上次根目录检查（指纹或全量扫描）的时间戳；按 [refreshIntervalMs] 节流。 */
    @Volatile
    private var lastCheckAtMs: Long = 0L

    /** P-S3：快照落 <dataDir>/dir-index.snapshot；lazy 以尊重测试里的 dataDir 赋值。 */
    private val snapshotStore: DirIndexSnapshotStore by lazy { DirIndexSnapshotStore(config.dataDir) }

    /** P-S3：单条 daemon 后台重建线程；重建请求合并（在途再触发不叠任务）。 */
    private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "dir-index-background-rebuild").apply { isDaemon = true }
    }

    /** true = 已有重建在跑或排队；新触发合并掉。 */
    private val rebuildInFlight = AtomicBoolean(false)

    /**
     * true = 下一次后台 rebuild 必须全量重扫（快照载入后的校验 walk：不信任
     * 快照里的 mtime，逐目录重扫）；之后的 TTL 轮才做 mtime 增量。
     */
    private val verificationPending = AtomicBoolean(false)

    @Volatile
    private var destroyed = false

    // ---- P-S3 测试 seam（包内可测计数）：断言「快照启动零 walk」「增量只重扫变化目录」 ----

    private val walkCounter = AtomicInteger()

    private val dirScanCounter = AtomicInteger()

    /** 后台/前台 rebuild（根 walk）次数；快照载入路径不计。 */
    internal val walkCount: Int get() = walkCounter.get()

    /** 单目录重扫次数（rebuild 与按需自愈都计）；复用不计。 */
    internal val dirScanCount: Int get() = dirScanCounter.get()

    /** 测试开关：false = 快照载入后不自动调度校验 walk（供「零 walk」断言）。 */
    internal var startVerificationAfterSnapshotLoad = true

    /**
     * 测试 seam：提交空任务到同一单线程 executor 并等待——FIFO 保证在途/排队
     * 的后台重建全部完成后才返回（请求线程零 walk 断言的配对工具）。
     */
    internal fun awaitBackgroundIdleForTest(timeoutMs: Long = 30_000) {
        backgroundExecutor.submit<Unit> { }.get(timeoutMs, TimeUnit.MILLISECONDS)
    }

    @PostConstruct
    fun loadAll() {
        if (!snapshotEnabled || !tryLoadSnapshot()) {
            refresh()
        }
    }

    /**
     * P-S3：载入快照（毫秒级，零 walk）。成功 = 调度后台全量校验 walk 并返回
     * true；快照缺失/损坏/根不符 = 返回 false，调用方回退 [refresh] 全量 walk。
     */
    private fun tryLoadSnapshot(): Boolean {
        val startedAt = System.currentTimeMillis()
        val snapshot = snapshotStore.load(snapshotRootPath())
        if (snapshot == null) {
            logger.info(
                "DownloadDirIndex: no usable snapshot at {} — falling back to full walk",
                snapshotStore.file.absolutePath
            )
            return false
        }
        val next = HashMap<Long, DirEntry>(snapshot.entries.size)
        val names = HashSet<String>(snapshot.entries.size)
        var files = 0
        for (e in snapshot.entries) {
            val pageFiles = e.pages.associate {
                it.page to PageRef(gid = e.gid, page = it.page, ext = it.ext, size = it.size, fileName = it.fileName)
            }
            next[e.gid] = DirEntry(gid = e.gid, dir = File(e.dirPath), dirMtime = e.dirMtime, pageFiles = pageFiles)
            files += pageFiles.size
            names.add(File(e.dirPath).name)
        }
        synchronized(indexLock) { indexRef = next }
        // 指纹先取快照自述：TTL 到点时只有真的发生变化才再调度重建（校验 walk
        // 随后会写入真实指纹）。校验 pending 置位：下一次后台 rebuild 全量重扫
        // （快照 mtime 不可信）；调度被测试开关拦下时由下一轮 TTL 兜底。
        lastDirFingerprint = names
        verificationPending.set(true)
        logger.info(
            "DownloadDirIndex: snapshot loaded: {} gid directories, {} pushed page files from {} ({} ms) — background verification walk scheduled",
            next.size, files, snapshotStore.file.absolutePath, System.currentTimeMillis() - startedAt
        )
        if (startVerificationAfterSnapshotLoad) scheduleRebuild()
        return true
    }

    /**
     * 同步全量重建索引：清表后重扫 root 下全部 `{gid}`/`{gid}-{title}` 目录。
     * 显式语义保留（启动路径/测试）；**请求线程不得调用**——9327 目录/64 万
     * 文件的全量 walk 实测 9.5s（冷盘更久），会把「全部下载」这类 HTTP 请求
     * 阻塞成前端观感上的「卡死」。P-S3 后 TTL 指纹变化**不走这里**，走
     * [scheduleRebuild] 后台重建。
     */
    fun refresh() {
        rebuildRoot(incremental = false, reason = "full rebuild")
    }

    /**
     * 非阻塞刷新：调度一次后台增量重建（与 TTL 路径同款机制：单线程 + CAS
     * 合并，绝不占用请求线程）。「全部下载」等用户触发的新鲜度诉求用它——
     * 后台 walk 完成后读取/服务路径即读到刚落盘的文件；下载校验路径本身
     * 直接列目录（不依赖索引），不受调度时序影响。
     */
    fun refreshAsync() {
        scheduleRebuild()
    }

    /**
     * 一轮根 walk：列根 → 逐目录构建新表 → copy-on-write 整表交换 → 写指纹 →
     * 覆写快照。[incremental] = true 时对「路径相同且目录 mtime 未变」的旧条目
     * 直接复用（增量）；快照校验轮必须全量（快照 mtime 不可信）。
     */
    private fun rebuildRoot(incremental: Boolean, reason: String) {
        val startedAt = System.currentTimeMillis()
        walkCounter.incrementAndGet()
        // 全量 walk 本身就是一次根检查：重置节流时钟（语义沿袭 P-S3 前
        // refresh），启动/重建后首个请求不必再做一次指纹 listFiles。
        lastCheckAtMs = System.currentTimeMillis()
        if (!root.isDirectory) {
            synchronized(indexLock) { indexRef = emptyMap() }
            // 收敛为空集：根缺失时指纹恒等，避免每轮 TTL 都空转调度。
            lastDirFingerprint = emptySet()
            logger.info("DownloadDirIndex rebuild ({}): downloads root missing at {}", reason, root.absolutePath)
            return
        }
        val dirs = root.listFiles()
            ?.filter { it.isDirectory && DownloadDirs.isOursDir(it.name) }
            .orEmpty()
        val fingerprint = dirs.map { it.name }.toSet()
        val previous = indexRef
        // 顺序敏感不必要——以指纹丢失/新增判断即可（名称级）。
        val next = HashMap<Long, DirEntry>(dirs.size)
        var indexed = 0
        var files = 0
        var reused = 0
        for (dir in dirs) {
            val gid = DownloadDirs.parseGid(dir.name) ?: continue
            // 增量：路径相同 + 目录 mtime 未变 → 复用旧条目（零重扫）。
            val cached = if (incremental) {
                previous[gid]?.takeIf { it.dir.path == dir.path && it.dirMtime == dir.lastModified() }
            } else {
                null
            }
            val entry = cached ?: scanDirToEntry(gid, dir) ?: continue
            next[gid] = entry
            indexed++
            if (cached != null) reused++ else files += entry.pageCount
        }
        synchronized(indexLock) { indexRef = next }
        lastDirFingerprint = fingerprint
        val elapsedMs = System.currentTimeMillis() - startedAt
        if (incremental) {
            logger.info(
                "DownloadDirIndex background rebuild ({}): {} gid directories ({} reused, {} rescanned) in {} ms",
                reason, indexed, reused, indexed - reused, elapsedMs
            )
        } else {
            logger.info(
                "DownloadDirIndex refreshed ({}): {} gid directories, {} pushed page files under {} (full walk {} ms)",
                reason, indexed, files, root.absolutePath, elapsedMs
            )
        }
        if (dirs.size > LARGE_TREE_THRESHOLD) {
            logger.info(
                "DownloadDirIndex: large download tree ({} gid directories); refresh was one listFiles per directory",
                dirs.size
            )
        }
        saveSnapshot(next.values)
    }

    /**
     * P-S3：TTL 指纹变化触发的重建调度——**永远不在请求线程执行**。单条后台
     * 线程 + CAS 合并：在途/排队期间的再触发直接丢弃（在跑的那轮根列表已覆盖
     * 最新变化；极端时序由下一轮 TTL 兜底）。
     */
    private fun scheduleRebuild() {
        if (destroyed) return
        if (!rebuildInFlight.compareAndSet(false, true)) {
            logger.info("DownloadDirIndex: background rebuild already in flight — trigger merged")
            return
        }
        try {
            backgroundExecutor.execute {
                try {
                    val verify = verificationPending.getAndSet(false)
                    rebuildRoot(
                        incremental = !verify,
                        reason = if (verify) "verification" else "root listing changed",
                    )
                } catch (e: Throwable) {
                    logger.warn("DownloadDirIndex: background rebuild failed: {}", e.toString())
                } finally {
                    rebuildInFlight.set(false)
                }
            }
        } catch (e: RejectedExecutionException) {
            rebuildInFlight.set(false)
        }
    }

    /** 快照根：canonical 化避免 macOS /var ↔ /private/var 之类的别名误判。 */
    private fun snapshotRootPath(): String =
        runCatching { root.canonicalPath }.getOrDefault(root.absolutePath)

    /** rebuild 成功后覆写快照；开关关闭或写失败静默（store 内只告警不抛）。 */
    private fun saveSnapshot(entries: Collection<DirEntry>) {
        if (!snapshotEnabled) return
        snapshotStore.save(
            DirIndexSnapshotStore.Snapshot(
                rootPath = snapshotRootPath(),
                savedAtMs = System.currentTimeMillis(),
                entries = entries.map { e ->
                    DirIndexSnapshotStore.SnapshotEntry(
                        gid = e.gid,
                        dirPath = e.dir.path,
                        dirMtime = e.dirMtime,
                        pages = e.pageFiles.values.sortedBy { it.page }
                            .map { DirIndexSnapshotStore.SnapshotPage(it.page, it.ext, it.size, it.fileName) },
                    )
                },
            )
        )
    }

    /** Spring 优雅停机：等在途重建收尾（含最后的快照覆写），超时强停。 */
    @PreDestroy
    fun shutdown() {
        destroyed = true
        backgroundExecutor.shutdown()
        try {
            if (!backgroundExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                backgroundExecutor.shutdownNow()
            }
        } catch (_: InterruptedException) {
            backgroundExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    /**
     * 自动感知（无需重启）：根目录列表指纹与上次不同（用户复制/上传/删除缓存
     * 目录）→ 调度**后台**重建（P-S3：请求线程零 walk，期间继续用旧索引）。
     * 所有查询入口先调用；按 [refreshIntervalMs] 节流：间隔内的调用直接返回，
     * 不做根 `listFiles()`。间隔 ≤0 = 每次都检（旧行为，测试用）。
     */
    private fun ensureFresh() {
        val now = System.currentTimeMillis()
        if (refreshIntervalMs > 0 && now - lastCheckAtMs < refreshIntervalMs) return
        lastCheckAtMs = now
        val dirs = root.listFiles()
            ?.filter { it.isDirectory && DownloadDirs.isOursDir(it.name) }
            .orEmpty()
        val fingerprint = dirs.map { it.name }.toSet()
        if (fingerprint != lastDirFingerprint) {
            logger.info(
                "DownloadDirIndex: root listing changed ({} dirs) — scheduling background rebuild",
                fingerprint.size
            )
            scheduleRebuild()
        }
    }

    /**
     * Indexed page count for [gid]: 0 when the gallery has no indexed pushed
     * files (unknown gallery, empty dir or not yet refreshed).
     */
    fun pageCount(gid: Long): Int {
        ensureFresh()
        return lookup(gid)?.pageCount ?: 0
    }

    /**
     * Locate a pushed page file for a 0-based API [page] (files are 1-based,
     * hence page + 1). Returns null when the page is not present in the index.
     */
    fun findPage(gid: Long, page: Int): PageRef? {
        if (page < 0) return null
        ensureFresh()
        return lookup(gid)?.pageFiles?.get(page + 1)
    }

    /**
     * P-S3：copy-on-write 摘除单条后整表交换，语义与移除等价——下一次查询按
     * 需重扫（生命周期事件立即生效，不依赖 TTL）。
     */
    fun invalidate(gid: Long) {
        synchronized(indexLock) {
            val current = indexRef
            if (!current.containsKey(gid)) return
            val next = HashMap<Long, DirEntry>(current)
            next.remove(gid)
            indexRef = next
        }
    }

    /**
     * 纯扫描单目录为条目（不触碰索引表）：目录不存在 → null；命中页文件按
     * [selectPageFile] 决定唯一胜者（P2-5）。[dirScanCount] seam 在此计数。
     */
    private fun scanDirToEntry(gid: Long, dir: File): DirEntry? {
        if (!dir.isDirectory) return null
        dirScanCounter.incrementAndGet()
        val mtime = dir.lastModified()
        // Group same-page candidates first, then pick the deterministic winner
        // (P2-5): duplicates (4- and 8-digit twins of one page) are warned and
        // resolved by selectPageFile instead of "first found wins".
        val candidates = HashMap<Int, MutableList<File>>()
        dir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val match = FILE_NAME_PATTERN.matchEntire(file.name) ?: return@forEach
            val pageNo = match.groupValues[1].toIntOrNull() ?: return@forEach
            val ext = match.groupValues[2].lowercase()
            if (extOrder.indexOf(ext) < 0) return@forEach
            candidates.getOrPut(pageNo) { mutableListOf() }.add(file)
        }
        val pages = HashMap<Int, PageRef>()
        for ((pageNo, files) in candidates) {
            if (files.size > 1) {
                logger.warn(
                    "DownloadDirIndex: duplicate page files for gid={} page={}: {} (using {})",
                    gid, pageNo, files.map { it.name }, selectPageFile(files)?.name,
                )
            }
            val chosen = selectPageFile(files) ?: continue
            pages[pageNo] = PageRef(
                gid = gid,
                page = pageNo,
                ext = chosen.extension.lowercase(),
                size = chosen.length(),
                fileName = chosen.name,
            )
        }
        return DirEntry(gid = gid, dir = dir, dirMtime = mtime, pageFiles = pages)
    }

    /** copy-on-write 单条替换/摘除（自愈路径），串行化避免并发丢更新。 */
    private fun replaceEntry(gid: Long, entry: DirEntry?) {
        synchronized(indexLock) {
            val current = indexRef
            if (current[gid] === entry) return
            if (entry == null && !current.containsKey(gid)) return
            val next = HashMap<Long, DirEntry>(current)
            if (entry == null) next.remove(gid) else next[gid] = entry
            indexRef = next
        }
    }

    /**
     * Current entry for [gid]. Index hits self-validate against the CACHED
     * [DirEntry.dir] (exists + mtime unchanged) — zero disk traversal on the
     * root. Only an index miss or failed self-validation (directory renamed
     * or removed, files pushed/removed since the last scan) falls back to
     * [findDir] + [scanDirToEntry]: rename/delete self-healing re-locates the
     * directory by parsing the leading gid and rebuilds the entry (copy-on-write
     * swap, P-S3). Directory naming is `{gid}` (legacy) or `{gid}-{title}`
     * (Android-aligned, 2026-08-30); both resolve through the same path.
     */
    private fun lookup(gid: Long): DirEntry? {
        // 索引命中零磁盘遍历：直接自验缓存的 dir，不走 findDir()（其根
        // listFiles() 正是本任务要消除的每请求热路径）。
        indexRef[gid]?.let { entry ->
            if (entry.dir.isDirectory && entry.dir.lastModified() == entry.dirMtime) return entry
        }
        // miss 或自验失败：重找目录（rename → 新路径；删除 → {gid} 占位，
        // 由摘除逻辑清索引）并按需重扫。
        val rescanned = scanDirToEntry(gid, findDir(gid))
        replaceEntry(gid, rescanned)
        return rescanned
    }

    /** Locate the directory for [gid] under the root (legacy `{gid}` or `{gid}-{title}`). */
    fun dirFor(gid: Long): File? {
        ensureFresh()
        // 索引直读：命中直接返回缓存 dir（零 findDir/根 listFiles）；miss
        // 或目录已消失才回落 findDir。null = 目录不存在，语义不变。
        return indexRef[gid]?.dir?.takeIf { it.isDirectory }
            ?: findDir(gid).takeIf { it.isDirectory }
    }

    /** Locate the directory for [gid] under the root (legacy `{gid}` or `{gid}-{title}`). */
    private fun findDir(gid: Long): File {
        if (!root.isDirectory) return File(root, gid.toString())
        root.listFiles()
            ?.filter { it.isDirectory && DownloadDirs.parseGid(it.name) == gid }
            ?.maxByOrNull { it.lastModified() }
            ?.let { return it }
        return File(root, gid.toString())
    }

    companion object {
        /** Extension priority; must mirror ImageProxyController.PUSHED_EXTENSIONS. */
        internal val EXT_ORDER = listOf("jpg", "jpeg", "png", "gif", "webp")

        /**
         * Shared deterministic selection among same-(gid,page) candidate files
         * (P2-5, 4/8-digit duplicate page ambiguity): highest extension
         * priority (EXT_ORDER, lower index wins — jpg over webp), then the
         * LONGER digit prefix (downloader `%08d` naming wins over the legacy
         * `%04d` twin), then plain file name for a total order. Backfill
         * baselines, reverify comparison and repair overwrite all go through
         * this rule — directly, or via [findPage] (whose index wins come from
         * [scanDirToEntry] using the same rule) — so the three paths always pick the
         * SAME physical file. Deliberately companion-level (pure rule, no
         * instance state): the fallback paths call it even when the index
         * itself is a test mock.
         */
        internal fun selectPageFile(candidates: List<File>): File? =
            candidates.maxWithOrNull(
                compareBy(
                    { f: File -> -EXT_ORDER.indexOf(f.extension.lowercase()) },
                    { f: File -> f.nameWithoutExtension.length },
                    { f: File -> f.name },
                )
            )

        private val FILE_NAME_PATTERN = Regex("^(\\d{4,})\\.(.+)$")

        /** Only above this tree size does the startup scan log a hint. */
        const val LARGE_TREE_THRESHOLD = 5000
    }
}
