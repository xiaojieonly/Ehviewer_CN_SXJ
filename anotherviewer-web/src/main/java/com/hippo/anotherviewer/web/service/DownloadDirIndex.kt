package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
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
 *   gid directory — callers (GalleryService.countPushedPages,
 *   ImageProxyController.findPushedPageFile) never rescan the disk per request.
 * - Stale entries are refreshed lazily: a lookup compares the recorded
 *   directory mtime with `dir.lastModified()` and re-scans only the changed
 *   directory (push/delete writes bump the dir mtime). [invalidate] drops an
 *   entry on download lifecycle events for immediate consistency.
 *
 * Shared concurrently; safe for the WebUI single-user model.
 */
@Service
class DownloadDirIndex(
    private val config: SiteCoreConfigProperties,
) {
    private val logger = LoggerFactory.getLogger(DownloadDirIndex::class.java)

    /** Extension priority; must mirror ImageProxyController.PUSHED_EXTENSIONS. */
    internal val extOrder = listOf("jpg", "jpeg", "png", "gif", "webp")

    private class DirEntry(
        val gid: Long,
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
     * 缓存目录）→ 全量重建。所有查询入口先调用；触发只在指纹差异时发生
     * （幂等，无变化零开销）。refresh 与并发查询用 synchronized 串行化——
     * 避免两个请求同时全量扫描。
     */
    private fun ensureFresh() {
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
            dirMtime = mtime,
            pageFiles = pages.mapValues { (pageNo, p) ->
                PageRef(gid = gid, page = pageNo, ext = p.ext, size = p.size, fileName = p.fileName)
            }
        )
        index[gid] = entry
        return entry
    }

    /**
     * Current entry for [gid], refetching when the directory is absent from
     * the index (created after startup, e.g. by an App push) or when its mtime
     * changed (files pushed/removed since the last scan). Directory naming is
     * `{gid}` (legacy) or `{gid}-{title}` (Android-aligned, 2026-08-30); both
     * are located by parsing the leading gid from the directory name.
     */
    private fun lookup(gid: Long): DirEntry? {
        val dir = findDir(gid)
        index[gid]?.let { entry ->
            if (dir.isDirectory && dir.lastModified() == entry.dirMtime) return entry
        }
        return scanDir(gid, dir)
    }

    /** Locate the directory for [gid] under the root (legacy `{gid}` or `{gid}-{title}`). */
    fun dirFor(gid: Long): File? {
        ensureFresh()
        val dir = findDir(gid)
        return if (dir.isDirectory) dir else null
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
