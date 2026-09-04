package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.ConcurrentHashMap

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
 * - Built once at startup ([loadAll]) with a single `listFiles` per numeric
 *   gid directory. Indexed lookups self-validate against the CACHED directory
 *   ([DirEntry.dir]) without ever touching the root listing — an index hit
 *   costs zero disk traversal, so the hot path (GalleryService.countPushedPages,
 *   ImageProxyController.findPushedPageFile) never pays a per-request
 *   `root.listFiles()` even on trees with thousands of gid directories.
 * - Stale entries are refreshed lazily: a lookup compares the recorded
 *   directory mtime with `entry.dir.lastModified()` and re-scans only the
 *   changed directory (push/delete writes bump the dir mtime). [invalidate]
 *   drops an entry on download lifecycle events for immediate consistency.
 * - External changes (whole gid dirs copied/uploaded/deleted) are sensed via
 *   the root-listing fingerprint check in [ensureFresh], throttled to once
 *   per `anotherviewer.download.dir-index-refresh-ms` (default 30s): such a
 *   change is picked up at the first request after the TTL elapses, i.e. the
 *   awareness latency is ≤ TTL. Interval ≤ 0 checks on every request (legacy
 *   behavior, used by tests). Per-gid healing does NOT wait for the TTL:
 *   index misses and failed self-validation (rename/delete/mtime) go straight
 *   to [findDir]/[scanDir].
 *
 * Shared concurrently; safe for the WebUI single-user model.
 */
@Service
class DownloadDirIndex(
    private val config: SiteCoreConfigProperties,
    /**
     * 根指纹检查的节流间隔 ms；≤0 = 每次都检（旧行为，测试用）。
     * Kotlin 默认 0 仅供非 Spring 构造（既有测试）保持旧语义。
     */
    @Value("\${anotherviewer.download.dir-index-refresh-ms:30000}")
    private val refreshIntervalMs: Long = 0,
) {
    private val logger = LoggerFactory.getLogger(DownloadDirIndex::class.java)

    /** Extension priority; must mirror ImageProxyController.PUSHED_EXTENSIONS. */
    internal val extOrder = listOf("jpg", "jpeg", "png", "gif", "webp")

    private class DirEntry(
        val gid: Long,
        /** Cached directory handle: hit-path self-validation avoids findDir (a root listFiles). */
        val dir: File,
        val dirMtime: Long,
        /** 1-based page → located page ref (best extension priority wins). */
        val pageFiles: Map<Int, PageRef>,
    ) {
        val pageCount: Int get() = pageFiles.size
    }

    private val index = ConcurrentHashMap<Long, DirEntry>()

    private val root: File get() = File(config.download.path)

    /** 上次全量扫描的根列表指纹（目录名集合）；查询时轻量检测以触发自愈刷新。 */
    @Volatile
    private var lastDirFingerprint: Set<String>? = null

    /** 上次根目录检查（指纹或全量扫描）的时间戳；按 [refreshIntervalMs] 节流。 */
    @Volatile
    private var lastCheckAtMs: Long = 0L

    @PostConstruct
    fun loadAll() {
        refresh()
    }

    /**
     * 全量重建索引（启动与自动感知共用）：清空 index 后重扫 root 下全部
     * `{gid}`/`{gid}-{title}` 目录。运行时被 [ensureFresh] 在检测到根目录
     * 列表变化时调用——复制/上传新缓存无需重启。
     */
    fun refresh() {
        // 全量重建本身就是一次根检查：重置节流时钟，启动后首个请求不必再做
        // 一次指纹 listFiles（TTL 的“上次检查”以最后一次扫描为准）。
        lastCheckAtMs = System.currentTimeMillis()
        if (!root.isDirectory) {
            logger.info("DownloadDirIndex refresh: downloads root missing at {}", root.absolutePath)
            lastDirFingerprint = null
            index.clear()
            return
        }
        val dirs = root.listFiles()
            ?.filter { it.isDirectory && DownloadDirs.isOursDir(it.name) }
            .orEmpty()
        val fingerprint = dirs.map { it.name }.toSet()
        // 顺序敏感不必要——以指纹丢失/新增判断即可（名称级）。
        val next = ConcurrentHashMap<Long, DirEntry>()
        var indexed = 0
        var files = 0
        for (dir in dirs) {
            val gid = DownloadDirs.parseGid(dir.name) ?: continue
            val entry = scanDir(gid, dir) ?: continue
            next[gid] = entry
            indexed++
            files += entry.pageCount
        }
        index.clear()
        index.putAll(next)
        lastDirFingerprint = fingerprint
        logger.info(
            "DownloadDirIndex refreshed: {} gid directories, {} pushed page files under {}",
            indexed, files, root.absolutePath
        )
        if (dirs.size > LARGE_TREE_THRESHOLD) {
            logger.info(
                "DownloadDirIndex: large download tree ({} gid directories); refresh was one listFiles per directory",
                dirs.size
            )
        }
    }

    /**
     * 自动感知（无需重启）：根目录列表指纹与上次不同（用户复制/上传/删除
     * 缓存目录）→ 全量重建。所有查询入口先调用；触发只在指纹差异时发生。
     * 按 [refreshIntervalMs] 节流：间隔内的调用直接返回，不做根 `listFiles()`
     * （命中路径 per-request 零磁盘遍历的另一半），到点才检查一次——外部
     * 改动的感知延迟由此从实时退化为 ≤TTL（默认 30s）。间隔 ≤0 = 每次都检
     * （旧行为，测试用）。refresh 与并发查询用 synchronized 串行化——
     * 避免两个请求同时全量扫描。
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
            synchronized(this) {
                // double-check：持锁后重试，避免并发都进 refresh
                val dirsLocked = root.listFiles()
                    ?.filter { it.isDirectory && DownloadDirs.isOursDir(it.name) }
                    .orEmpty()
                val fpLocked = dirsLocked.map { it.name }.toSet()
                if (fpLocked != lastDirFingerprint) {
                    logger.info(
                        "DownloadDirIndex: root listing changed ({} dirs); auto-refreshing",
                        fpLocked.size
                    )
                    refresh()
                }
            }
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

    /** Drop the entry so the next lookup re-scans from disk. */
    fun invalidate(gid: Long) {
        index.remove(gid)
    }

    /** Index snapshot of one gid directory; null when the dir is absent. */
    private fun scanDir(gid: Long, dir: File): DirEntry? {
        if (!dir.isDirectory) {
            index.remove(gid)
            return null
        }
        val mtime = dir.lastModified()
        // Keep the highest-priority extension per page number (first found wins).
        val pages = HashMap<Int, MutablePage>()
        dir.listFiles()?.forEach { file ->
            val match = FILE_NAME_PATTERN.matchEntire(file.name) ?: return@forEach
            val pageNo = match.groupValues[1].toIntOrNull() ?: return@forEach
            val ext = match.groupValues[2].lowercase()
            val priority = extOrder.indexOf(ext)
            if (priority < 0) return@forEach
            val existing = pages[pageNo]
            if (existing == null || priority < existing.priority) {
                pages[pageNo] = MutablePage(ext, file.length(), priority, file.name)
            }
        }
        val entry = DirEntry(
            gid = gid,
            dir = dir,
            dirMtime = mtime,
            pageFiles = pages.mapValues { (pageNo, p) ->
                PageRef(gid = gid, page = pageNo, ext = p.ext, size = p.size, fileName = p.fileName)
            }
        )
        index[gid] = entry
        return entry
    }

    /**
     * Current entry for [gid]. Index hits self-validate against the CACHED
     * [DirEntry.dir] (exists + mtime unchanged) — zero disk traversal on the
     * root. Only an index miss or failed self-validation (directory renamed
     * or removed, files pushed/removed since the last scan) falls back to
     * [findDir] + [scanDir]: rename/delete self-healing re-locates the
     * directory by parsing the leading gid and rebuilds the entry. Directory
     * naming is `{gid}` (legacy) or `{gid}-{title}` (Android-aligned,
     * 2026-08-30); both resolve through the same path.
     */
    private fun lookup(gid: Long): DirEntry? {
        // 索引命中零磁盘遍历：直接自验缓存的 dir，不走 findDir()（其根
        // listFiles() 正是本任务要消除的每请求热路径）。
        index[gid]?.let { entry ->
            if (entry.dir.isDirectory && entry.dir.lastModified() == entry.dirMtime) return entry
        }
        // miss 或自验失败：重找目录（rename → 新路径；删除 → {gid} 占位，
        // 由 scanDir 摘除索引）并按需重扫。
        return scanDir(gid, findDir(gid))
    }

    /** Locate the directory for [gid] under the root (legacy `{gid}` or `{gid}-{title}`). */
    fun dirFor(gid: Long): File? {
        ensureFresh()
        // 索引直读：命中直接返回缓存 dir（零 findDir/根 listFiles）；miss
        // 或目录已消失才回落 findDir。null = 目录不存在，语义不变。
        return index[gid]?.dir?.takeIf { it.isDirectory }
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

    private data class MutablePage(val ext: String, val size: Long, val priority: Int, val fileName: String)

    private companion object {
        val FILE_NAME_PATTERN = Regex("^(\\d{4,})\\.(.+)$")

        /** Only above this tree size does the startup scan log a hint. */
        const val LARGE_TREE_THRESHOLD = 5000
    }
}
