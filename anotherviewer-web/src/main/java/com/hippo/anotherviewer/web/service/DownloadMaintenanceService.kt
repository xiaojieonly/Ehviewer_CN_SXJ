package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.MaintenanceCleanResponse
import com.hippo.anotherviewer.web.dto.MaintenanceDownloadIssue
import com.hippo.anotherviewer.web.dto.MaintenanceFileIssue
import com.hippo.anotherviewer.web.dto.MaintenanceKind
import com.hippo.anotherviewer.web.dto.MaintenancePreviewResponse
import com.hippo.anotherviewer.web.entity.DownloadInfoEntity
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File

/**
 * 下载维护（W2-DL F2）：冗余文件 / 无效下载的扫描与清理。
 *
 * 两段式设计：`preview()` 只读扫描给出「将删清单」，`clean()` 在执行前
 * 用同一套扫描逻辑重新求差集、只删当前仍然命中的条目——预览与执行之间
 * 磁盘/DB 发生变化（新下载落盘、行被删除）时不会误删或漏删。
 *
 * 同步实现（不走 JobService 异步）的理由：
 * - 扫描 = downloads 根目录一层 listFiles + 每行至多一次 stat，清理对象是
 *   「每画廊一个目录」级别的少量条目；与 IMPORT/EXPORT/CACHE_CLEAR 那种
 *   大量网络/海量小文件的长任务不同量级，典型库规模下毫秒到秒级即完成。
 * - 同步响应让前端免于接入 job 轮询/WS 任务面板，两段式弹窗流程即可闭环；
 *   代价仅是一次较长的 HTTP 请求，可接受。
 *
 * 判定语义（自定并固化于此）：
 * - 冗余文件：downloads 根目录的直接子条目未被任何下载行引用。引用判定 =
 *   行的 downloadDir 规范路径命中，或目录名等于某行的 gid（兜底覆盖
 *   downloadDir 为空的行，其默认目录即 `<root>/<gid>`）。根目录下的散落
 *   普通文件恒为冗余——写入方只会创建 per-gid 目录。
 * - 无效下载：终态 FINISHED(state=3) 的行，其本地内容目录缺失，或目录内
 *   不存在任何 >0 字节的文件（全部页面缺失/0 字节损坏）。state 0/1/2 的行
 *   处于生命周期活跃态（排队/暂停/下载中），目录为空属正常，永不入选；
 *   state=4(FAILED) 的行仍可手动重试恢复，也不入选——保守删除面。
 */
@Service
class DownloadMaintenanceService(
    private val downloadRepository: DownloadInfoRepository,
    private val config: SiteCoreConfigProperties
) {
    private val logger = LoggerFactory.getLogger(DownloadMaintenanceService::class.java)

    /** 只读扫描：返回当前全部冗余文件与无效下载清单。 */
    fun preview(): MaintenancePreviewResponse {
        val rows = downloadRepository.findAll()
        return MaintenancePreviewResponse(
            // 防风控/防泄漏（傻快方案，2026-09-04 用户裁决）：预览响应里的
            // 路径一律只出前 10 个字符。clean() 执行前会用 scanRedundant()
            // 重新扫描、拿内部完整路径删除，不消费预览响应里的路径——截断
            // 不影响两段式语义。代价：CSV 导出的路径同为前缀，无法直接定位
            // 原文件（需要全路径时用 mask 关不掉，这是该方案的取舍）。
            redundantFiles = scanRedundant(rows).map { it.copy(path = it.path.take(PATH_PREVIEW_MAX)) },
            invalidDownloads = scanInvalid(rows)
        )
    }

    /**
     * 执行清理：重新扫描后只删当前命中的条目（两段式的第二段）。
     * 返回实际删除计数与释放字节数。
     */
    fun clean(kind: MaintenanceKind): MaintenanceCleanResponse = synchronized(this) {
        val rows = downloadRepository.findAll()
        var removedFiles = 0
        var removedDownloads = 0
        var freedBytes = 0L
        when (kind) {
            MaintenanceKind.REDUNDANT_FILES -> scanRedundant(rows).forEach { issue ->
                val target = File(config.download.path, issue.path)
                freedBytes += sizeOf(target)
                if (target.deleteRecursively()) removedFiles++
                else logger.warn("Failed to delete redundant path {}", target.absolutePath)
            }
            MaintenanceKind.INVALID_DOWNLOADS -> scanInvalid(rows).forEach { issue ->
                val row = rows.firstOrNull { it.id == issue.id } ?: return@forEach
                // 内容目录若存在且已无可用页面，一并清掉（否则会转为冗余文件）。
                run {
                    val dir = resolveDir(row)
                    if (dir.isDirectory && !isInsideRoot(dir)) {
                        // 目录指向 downloads 根之外（如 App 推流的任意路径）：
                        // 只删 DB 行，绝不递归删除根外文件系统内容。
                        logger.warn("Invalid download {} points outside the downloads root; row deleted, files kept", dir.absolutePath)
                    } else if (dir.exists()) {
                        freedBytes += sizeOf(dir)
                        if (!dir.deleteRecursively()) {
                            logger.warn("Failed to delete content dir of invalid download {}", dir.absolutePath)
                        }
                    }
                }
                downloadRepository.deleteById(issue.id)
                removedDownloads++
            }
        }
        logger.info(
            "Download maintenance clean kind={} removedFiles={} removedDownloads={} freedBytes={}",
            kind, removedFiles, removedDownloads, freedBytes
        )
        MaintenanceCleanResponse(kind, removedFiles, removedDownloads, freedBytes)
    }

    // ── 扫描实现 ────────────────────────────────────────────────

    private fun rootOrNull(): File? =
        config.download.path.takeIf { it.isNotBlank() }?.let(::File)

    /**
     * 引用集合：所有行的内容目录（经 [DownloadDirs.resolve] 归一到当前
     * 主机——跨机器迁移行的旧绝对路径不可用，回落 `<root>/<gid>`）。
     */
    private fun referencedDirs(root: File, rows: List<DownloadInfoEntity>): Set<String> =
        rows.map { row ->
            runCatching { DownloadDirs.resolve(root.path, row.gid, row.downloadDir).canonicalPath }
                .getOrDefault(File(root, row.gid.toString()).path)
        }.toSet()

    private fun scanRedundant(rows: List<DownloadInfoEntity>): List<MaintenanceFileIssue> {
        val root = rootOrNull() ?: return emptyList()
        if (!root.isDirectory) return emptyList()
        val referenced = referencedDirs(root, rows)
        // 行引用 gid 集合：目录只要命中任何一个行引用即「有效」。
        val referencedGids = rows.map { it.gid }.toSet()
        val children = root.listFiles() ?: return emptyList()

        val redundant = mutableListOf<MaintenanceFileIssue>()
        val cataloged = mutableMapOf<Long, MutableList<File>>() // gid → 目录组（本应用布局）

        for (child in children.sortedBy { it.name }) {
            val gid = if (child.isDirectory) DownloadDirs.parseGid(child.name) else null
            if (gid == null) {
                // 非本应用布局（杂项/文件/空目录）→ 一律冗余（行引用的自定义
                // 目录如 elsewhere-200 无 gid 前缀，在下方 canonical 判定豁免）。
                if (child.isDirectory &&
                    runCatching { child.canonicalPath in referenced }.getOrDefault(false)
                ) continue
                redundant += MaintenanceFileIssue(path = child.name, sizeBytes = sizeOf(child))
            } else {
                cataloged.getOrPut(gid) { mutableListOf() }.add(child)
            }
        }

        // 副本判定（2026-08-31 用户裁决）：
        //  - 行能匹配到的目录（gid 在行集合 referencedGids 且该目录被行引用）→
        //    不算冗余（环前已 continue）；
        //  - gid 在行集合但出现多目录（副本：-1/-2 后缀或同名）→ 保留被行引用
        //    的（keep），其余冗余；
        //  - gid 不在行集合（磁盘-only，匹配不到行）→ 全部冗余。
        for ((gid, gidDirs) in cataloged) {
            val isReferenced = gid in referencedGids
            val keep = if (isReferenced) keepOne(gidDirs, referenced) else null
            for (dir in gidDirs) {
                val isKept = dir == keep
                if (isKept) continue
                redundant += MaintenanceFileIssue(path = dir.name, sizeBytes = sizeOf(dir))
            }
        }

        // 行引用的 gid 目录保留（keepOne 已处理），磁盘-only 的目录已被并入冗余
        return redundant.sortedBy { it.path }
    }

    /**
     * 一个 gid 目录组中应保留的一个：优先行引用指向（referenced 的 canonicalPath），
     * 无则在组内选 mtime 最新者。
     */
    private fun keepOne(gidDirs: List<File>, referenced: Set<String>): File {
        gidDirs.firstOrNull { dir ->
            runCatching { dir.canonicalPath in referenced }.getOrDefault(false)
        }?.let { return it }
        return gidDirs.maxByOrNull { it.lastModified() } ?: gidDirs.first()
    }

    private fun scanInvalid(rows: List<DownloadInfoEntity>): List<MaintenanceDownloadIssue> =
        rows.filter { it.state == STATE_FINISHED }.mapNotNull { row ->
            val dir = resolveDir(row)
            when {
                // 行声称已完成，但内容目录整个不存在 → 记录与磁盘不一致。
                !dir.exists() ->
                    MaintenanceDownloadIssue(row.id, row.gid, row.title, REASON_DIR_MISSING)
                // 目录在但没有任何 >0 字节的常规文件 → 全部页面缺失或 0 字节损坏。
                !hasUsableContent(dir) ->
                    MaintenanceDownloadIssue(row.id, row.gid, row.title, REASON_NO_USABLE_FILES)
                else -> null
            }
        }

    /** 行的内容目录：downloadDir 经 [DownloadDirs.resolve] 归一（缺省回落 `<root>/<gid>`）。 */
    private fun resolveDir(row: DownloadInfoEntity): File =
        DownloadDirs.resolve(config.download.path, row.gid, row.downloadDir)

    /** 目录内是否存在至少一个 >0 字节的常规文件（一层足够：页面均为平铺 %04d.jpg）。 */
    private fun hasUsableContent(dir: File): Boolean =
        dir.listFiles()?.any { it.isFile && it.length() > 0 } ?: false

    /** 目录是否位于 downloads 根之内（规范路径前缀判定；任一侧解析失败视为否）。 */
    private fun isInsideRoot(dir: File): Boolean {
        val root = rootOrNull() ?: return false
        return runCatching {
            dir.canonicalPath.startsWith(root.canonicalPath + File.separator)
        }.getOrDefault(false)
    }

    private fun sizeOf(file: File): Long = when {
        file.isFile -> file.length()
        file.isDirectory -> file.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        else -> 0L
    }

    private companion object {
        /** Android `DownloadInfo.STATE_FINISHED`。 */
        const val STATE_FINISHED = 3
        const val REASON_DIR_MISSING = "content_dir_missing"
        const val REASON_NO_USABLE_FILES = "no_usable_page_files"

        /** 预览响应里路径的最大输出长度（防风控傻快方案，见 [preview]）。 */
        const val PATH_PREVIEW_MAX = 10
    }
}
