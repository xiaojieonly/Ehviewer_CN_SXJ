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

    @PostConstruct
    fun loadAll() {
        if (!root.isDirectory) {
            logger.info("DownloadDirIndex initialised: downloads root missing at {}", root.absolutePath)
            return
        }
        val dirs = root.listFiles()
            ?.filter { it.isDirectory && DownloadDirs.isOursDir(it.name) }
            .orEmpty()
        var indexed = 0
        var files = 0
        for (dir in dirs) {
            val gid = DownloadDirs.parseGid(dir.name) ?: continue
            val entry = scanDir(gid, dir) ?: continue
            indexed++
            files += entry.pageCount
        }
        logger.info(
            "DownloadDirIndex initialised: {} gid directories, {} pushed page files under {}",
            indexed, files, root.absolutePath
        )
        if (dirs.size > LARGE_TREE_THRESHOLD) {
            logger.info(
                "DownloadDirIndex: large download tree ({} gid directories); startup scan was one listFiles per directory",
                dirs.size
            )
        }
    }

    /**
     * Indexed page count for [gid]: 0 when the gallery has no indexed pushed
     * files (unknown gallery, empty dir or not yet refreshed).
     */
    fun pageCount(gid: Long): Int = lookup(gid)?.pageCount ?: 0

    /**
     * Locate a pushed page file for a 0-based API [page] (files are 1-based,
     * hence page + 1). Returns null when the page is not present in the index.
     */
    fun findPage(gid: Long, page: Int): PageRef? {
        if (page < 0) return null
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
