package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * v4 回收站（WP3）内容目录搬运器：下载墓碑化时把内容目录从 downloads 根移入
 * `{dataDir}/trash/`，恢复时移回，到期/孤儿目录物理清除。
 *
 * 为什么 trash 放 `{dataDir}/trash` 而不是 downloads 根下的 `.trash`：
 * [DownloadMaintenanceService.scanRedundant] 把 downloads 根下「没有任何行引用」
 * 的直接子目录一律判冗余删除——回收站目录恰恰在墓碑行（已从存活扫描中消失）下，
 * 放进去会被下一次维护扫描当冗余清掉，回收站就失去意义。dataDir 根下的 trash
 * 不在 downloads 根内，天然豁免；备份（BackupService）只打包 downloads/cache
 * 子目录，trash 也不会混进分片。
 *
 * 事件时机（@TransactionalEventListener AFTER_COMMIT）：目录移动必须在「行墓碑化
 * 已提交」之后执行——事务回滚时目录必须原样保留；fallbackExecution=true 覆盖无
 * 事务上下文的自调用路径（如 deleteDownloads 内自调 deleteDownload），此时立即
 * 执行等价「已提交」。移动失败只 warn 不上抛：行删除已生效，目录残留可由维护
 * 扫描/孤儿清理兜底，绝不因文件系统问题回滚删除。
 */
@Component
class TrashMoveHandler(
    private val config: SiteCoreConfigProperties,
) {

    private val logger = LoggerFactory.getLogger(TrashMoveHandler::class.java)

    /** 回收站根目录：`{dataDir}/trash`（见类注释：刻意放在 downloads 根之外）。 */
    fun trashRoot(): Path = Path.of(config.dataDir, "trash")

    /**
     * 下载墓碑落库后（事务提交触发）把内容目录移入回收站。
     * 源目录不存在 / 位于 downloads 根之外（App 推流的任意路径）→ 跳过，不搬移。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun onDownloadTombstoned(event: DownloadTombstonedEvent) {
        val source = resolveSource(event) ?: return
        try {
            Files.createDirectories(trashRoot())
            val target = uniqueTarget(source.name)
            // ATOMIC_MOVE：trash 与 downloads 正常同在 dataDir 下（同卷）→ 目录 rename 是
            // 元数据操作，不搬字节也不阻塞请求线程；跨卷（手工把 trash 指到别的挂载点）
            // 会抛 → 落到下面的 warn 分支，目录留原地由维护扫描兜底（绝不半搬状态）。
            Files.move(source.toPath(), target, StandardCopyOption.ATOMIC_MOVE)
            logger.info("Moved tombstoned download dir {} to trash {}", source.absolutePath, target)
        } catch (e: Exception) {
            // 只 warn：行删除已提交，目录残留由孤儿清理/维护扫描兜底。
            logger.warn("Failed to move tombstoned download dir for gid={}: {}", event.gid, e.message)
        }
    }

    /**
     * 恢复：从回收站把内容目录移回行内路径。目标目录已存在 → 返回 false（跳过文件
     * 移动，仅复位行——磁盘上已有内容，不该用回收站副本覆盖），行恢复由调用方完成。
     */
    fun restoreFromTrash(gid: Long, downloadDir: String?): Boolean {
        val target = targetDir(gid, downloadDir) ?: return false
        if (target.exists()) return false
        val candidate = trashCandidates(target).firstOrNull { it.isDirectory } ?: return false
        return try {
            target.parentFile?.mkdirs()
            Files.move(candidate.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            logger.info("Restored download dir from trash {} -> {}", candidate.absolutePath, target.absolutePath)
            true
        } catch (e: Exception) {
            logger.warn("Failed to restore download dir from trash for gid={}: {}", gid, e.message)
            false
        }
    }

    /** 物理清除指定下载的回收站目录（候选名：目标名 或 `{gid}-*` 残留）。返回是否删掉了目录。 */
    fun purgeTrashDir(gid: Long, downloadDir: String?): Boolean {
        val target = targetDir(gid, downloadDir) ?: return false
        var deleted = false
        trashCandidates(target).forEach { candidate ->
            if (candidate.exists() && candidate.deleteRecursively()) deleted = true
        }
        return deleted
    }

    /**
     * 孤儿回收站清理：删除 mtime 早于 [cutoffMillis] 的回收站直接子目录（无对应
     * 墓碑行或对应的行未被清到的残留），返回删除数。为空目录/文件一并清掉。
     */
    fun purgeOrphanTrash(cutoffMillis: Long): Int {
        val root = trashRoot().toFile()
        if (!root.isDirectory) return 0
        var removed = 0
        root.listFiles()?.forEach { child ->
            if (child.lastModified() < cutoffMillis && child.deleteRecursively()) removed++
        }
        return removed
    }

    // ── 内部解析 ────────────────────────────────────────────────

    /**
     * 墓碑事件的内容目录：经 [DownloadDirs.resolve] 归一到当前 downloads 根
     * （跨机器迁移行的旧绝对路径不可用，回落 `<root>/<gid>`）；目录不存在或不在
     * downloads 根内 → null（不搬移）。
     */
    private fun resolveSource(event: DownloadTombstonedEvent): File? {
        val root = config.download.path.takeIf { it.isNotBlank() }?.let(::File) ?: return null
        val dir = DownloadDirs.resolve(root.path, event.gid, event.downloadDir, null)
        if (!dir.isDirectory) return null
        val rootCanonical = runCatching { root.canonicalPath }.getOrNull() ?: return null
        val dirCanonical = runCatching { dir.canonicalPath }.getOrNull() ?: return null
        if (!dirCanonical.startsWith(rootCanonical + File.separator)) {
            logger.warn("Skipping trash move for gid={}: {} is outside the downloads root", event.gid, dir.absolutePath)
            return null
        }
        return dir
    }

    /** 恢复/清除的目标目录（行内路径归一；无法解析 downloads 根时 null）。 */
    private fun targetDir(gid: Long, downloadDir: String?): File? {
        val root = config.download.path.takeIf { it.isNotBlank() } ?: return null
        return DownloadDirs.resolve(root, gid, downloadDir, null)
    }

    /** 回收站内该目录的候选（重名后缀 `-1`/`-2`；另含 `{gid}-*` 旧命名残留）。 */
    private fun trashCandidates(target: File): List<File> {
        val root = trashRoot().toFile()
        val exact = File(root, target.name)
        val gidPrefix = target.name.substringBefore('-')
        return listOf(exact) + buildList {
            for (i in 1..MAX_DUP_SUFFIX) add(File(root, "${target.name}-$i"))
            root.listFiles { f -> f.name.startsWith("$gidPrefix-") && f.name != target.name }
                .orEmpty()
                .sortedBy { it.name }
                .forEach { add(it) }
        }.distinct()
    }

    /** 回收站内不覆盖既有条目的目标路径：重名追加 -1/-2… 后缀。 */
    private fun uniqueTarget(name: String): Path {
        val root = trashRoot()
        var candidate = root.resolve(name)
        var suffix = 1
        while (Files.exists(candidate)) {
            candidate = root.resolve("$name-$suffix")
            suffix++
        }
        return candidate
    }

    private companion object {
        /** 重名后缀搜索上限（递归搬移同名目录的次数远超其上的场景不存在）。 */
        const val MAX_DUP_SUFFIX = 99
    }
}
