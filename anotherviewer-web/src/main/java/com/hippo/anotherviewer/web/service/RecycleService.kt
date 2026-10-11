package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.dto.RecycleItem
import com.hippo.anotherviewer.web.dto.RecycleListResponse
import com.hippo.anotherviewer.web.dto.RecyclePurgeRequest
import com.hippo.anotherviewer.web.dto.RecyclePurgeResponse
import com.hippo.anotherviewer.web.dto.RecycleRestoreRequest
import com.hippo.anotherviewer.web.dto.RecycleRestoreResponse
import com.hippo.anotherviewer.web.repository.BookmarkInfoRepository
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import com.hippo.anotherviewer.web.repository.DownloadLabelRepository
import com.hippo.anotherviewer.web.repository.EhSessionRepository
import com.hippo.anotherviewer.web.repository.FilterRepository
import com.hippo.anotherviewer.web.repository.HistoryInfoRepository
import com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository
import com.hippo.anotherviewer.web.repository.QuickSearchRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * v4 回收站（WP3）：墓碑行的列出 / 恢复 / 物理清除。
 *
 * 语义（v4 基线 ④）：
 * - 7 类同步实体被删除 = 墓碑行（deleted=true），进回收站可 list/restore；
 * - restore = deleted=false + syncClock.next()（服务器打戳，增量 pull 可见复活事件）
 *   + 属主补落 + 行级来源记 "server"（SyncService.stampServerProvenance）；download
 *   同时把内容目录从 {dataDir}/trash 移回（目标已存在则跳过文件移动只复位行）；
 * - 到期（默认 7 天）由定时任务物理清除（含内容目录）——墓碑行只在保留窗口内是
 *   「删除的传播载体」，到期后行本身也没有回放价值；
 * - ehSession 墓碑**不进回收站**（服务器级单例会话，无用户可见的「恢复登录」交互），
 *   到期静默物理清除；
 * - trash 孤儿目录（无对应墓碑行/已清行残留）按目录 mtime 超保留期清除。
 *
 * 所有写入聚合计数发 [SyncUpdateEvent]（deviceId="server"），WebUI 免轮询刷新。
 */
@Service
class RecycleService(
    private val favoriteRepository: LocalFavoriteInfoRepository,
    private val historyRepository: HistoryInfoRepository,
    private val downloadRepository: DownloadInfoRepository,
    private val bookmarkRepository: BookmarkInfoRepository,
    private val filterRepository: FilterRepository,
    private val quickSearchRepository: QuickSearchRepository,
    private val downloadLabelRepository: DownloadLabelRepository,
    private val ehSessionRepository: EhSessionRepository,
    private val syncClock: SyncClock,
    private val syncService: SyncService,
    private val trashMoveHandler: TrashMoveHandler,
    private val eventPublisher: ApplicationEventPublisher,
    /** 墓碑保留天数（到期物理清除）；@Value 注入，直构测试可显式传值。 */
    @Value("\${anotherviewer.sync.trash-retention-days:7}")
    private val retentionDays: Long = 7,
) {

    private val logger = LoggerFactory.getLogger(RecycleService::class.java)

    /** 回收站实体类别：wire 名与 7 类同步实体一一对应（非法值 → 400 由控制器转）。 */
    internal enum class Entity(val wire: String) {
        FAVORITES("favorites"),
        DOWNLOADS("downloads"),
        HISTORY("history"),
        BOOKMARKS("bookmarks"),
        FILTERS("filters"),
        QUICK_SEARCHES("quickSearches"),
        DOWNLOAD_LABELS("downloadLabels"),
        ;

        companion object {
            fun fromWire(value: String?): Entity? = entries.firstOrNull { it.wire == value }
        }
    }

    /** 回收站列表条目（服务内部形态；分页/剩余天数在 [list] 内计算）。 */
    private data class Tombstone(val entity: String, val key: String, val title: String, val deletedAt: Long)

    // ── list ────────────────────────────────────────────────────

    /**
     * 回收站列表：按 lastModified（= 删除时刻）倒序分页；pageSize 钳 1..200。
     * @throws IllegalArgumentException entity 非法（控制器转 400 VALIDATION_ERROR）。
     */
    fun list(username: String, entity: String?, page: Int, pageSize: Int): RecycleListResponse {
        val e = Entity.fromWire(entity)
            ?: throw IllegalArgumentException("unknown recycle entity: ${entity ?: "(missing)"}")
        val size = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        val start = page.coerceAtLeast(0)
        val now = System.currentTimeMillis()
        val rows = tombstones(username, e).sortedByDescending { it.deletedAt }
        val items = rows.drop(start * size).take(size).map { row ->
            RecycleItem(
                entity = row.entity,
                key = row.key,
                title = row.title,
                deletedAt = row.deletedAt,
                remainingDays = remainingDays(row.deletedAt, now),
            )
        }
        return RecycleListResponse(items = items, total = rows.size, page = start, pageSize = size)
    }

    private fun remainingDays(deletedAt: Long, now: Long): Long =
        (retentionDays - (now - deletedAt) / DAY_MS).coerceAtLeast(0L)

    private fun tombstones(username: String, e: Entity): List<Tombstone> = when (e) {
        Entity.FAVORITES -> favoriteRepository.findByUsernameAndDeletedTrueOrderById(username)
            .map { Tombstone(e.wire, it.gid.toString(), it.title ?: "", it.lastModified) }
        Entity.HISTORY -> historyRepository.findByUsernameAndDeletedTrueOrderById(username)
            .map { Tombstone(e.wire, it.gid.toString(), it.title ?: "", it.lastModified) }
        Entity.DOWNLOADS -> downloadRepository.findByUsernameAndDeletedTrueOrderById(username)
            .map { Tombstone(e.wire, it.gid.toString(), it.title ?: "", it.lastModified) }
        // bookmark 无自有标题列：`{gid}-{category}` 作展示标题（与 Android 书签命名同构）。
        Entity.BOOKMARKS -> bookmarkRepository.findByUsernameAndDeletedTrueOrderById(username)
            .map { Tombstone(e.wire, it.gid.toString(), "${it.gid}-${it.category}", it.lastModified) }
        Entity.FILTERS -> filterRepository.findByUsernameAndDeletedTrueOrderById(username)
            .map { Tombstone(e.wire, "${it.type}:${it.text}", "${it.type}:${it.text}", it.lastModified) }
        Entity.QUICK_SEARCHES -> quickSearchRepository.findByUsernameAndDeletedTrueOrderById(username)
            .map { Tombstone(e.wire, it.name, it.name, it.lastModified) }
        Entity.DOWNLOAD_LABELS -> downloadLabelRepository.findByUsernameAndDeletedTrueOrderById(username)
            .map { Tombstone(e.wire, it.label, it.label, it.lastModified) }
    }

    // ── restore ─────────────────────────────────────────────────

    /** 恢复墓碑行：deleted=false + 服务器新戳 + 属主补落 + 行级来源 "server"。 */
    @Transactional
    fun restore(username: String, request: RecycleRestoreRequest): RecycleRestoreResponse {
        var restored = 0
        var failed = 0
        val counts = LinkedHashMap<String, Int>()
        request.items.forEach { item ->
            val e = Entity.fromWire(item.entity)
            if (e == null || !restoreOne(username, e, item.key)) {
                failed++
            } else {
                restored++
                counts.merge(e.wire, 1, Int::plus)
            }
        }
        publishCounts(counts)
        return RecycleRestoreResponse(restored = restored, failed = failed)
    }

    private fun restoreOne(username: String, e: Entity, key: String): Boolean {
        val stamp = { syncClock.next() }
        return when (e) {
            Entity.FAVORITES -> {
                val gid = key.toLongOrNull() ?: return false
                val row = favoriteRepository.findAllByGid(gid)
                    .firstOrNull { (it.username == null || it.username == username) && it.deleted } ?: return false
                row.deleted = false
                row.lastModified = stamp()
                if (row.username == null) row.username = username
                favoriteRepository.save(row)
                syncService.stampServerProvenance(username, SyncService.TAG_FAVORITE, gid.toString())
                true
            }
            Entity.HISTORY -> {
                val gid = key.toLongOrNull() ?: return false
                val row = historyRepository.findAllByGid(gid)
                    .firstOrNull { (it.username == null || it.username == username) && it.deleted } ?: return false
                row.deleted = false
                row.lastModified = stamp()
                if (row.username == null) row.username = username
                historyRepository.save(row)
                syncService.stampServerProvenance(username, SyncService.TAG_HISTORY, gid.toString())
                true
            }
            Entity.DOWNLOADS -> {
                val gid = key.toLongOrNull() ?: return false
                val row = downloadRepository.findAllByGid(gid)
                    .firstOrNull { (it.username == null || it.username == username) && it.deleted } ?: return false
                row.deleted = false
                row.lastModified = stamp()
                if (row.username == null) row.username = username
                downloadRepository.save(row)
                syncService.stampServerProvenance(username, SyncService.TAG_DOWNLOAD, gid.toString())
                // 内容目录从回收站移回；目标已存在 → 跳过文件移动（只复位行，不覆盖现有内容）。
                trashMoveHandler.restoreFromTrash(gid, row.downloadDir)
                true
            }
            Entity.BOOKMARKS -> {
                val gid = key.toLongOrNull() ?: return false
                val row = bookmarkRepository.findAllByGid(gid)
                    .firstOrNull { (it.username == null || it.username == username) && it.deleted } ?: return false
                row.deleted = false
                row.lastModified = stamp()
                if (row.username == null) row.username = username
                bookmarkRepository.save(row)
                syncService.stampServerProvenance(username, SyncService.TAG_BOOKMARK, gid.toString())
                true
            }
            Entity.FILTERS -> {
                val type = key.substringBefore(':').toIntOrNull() ?: return false
                val text = key.substringAfter(':', "")
                val row = filterRepository.findByTypeAndTextIgnoreCaseOrderByLastModifiedDesc(type, text)
                    .firstOrNull { (it.username == null || it.username == username) && it.deleted } ?: return false
                row.deleted = false
                row.lastModified = stamp()
                if (row.username == null) row.username = username
                filterRepository.save(row)
                syncService.stampServerProvenance(username, SyncService.TAG_FILTER, SyncService.filterKey(type, text))
                true
            }
            Entity.QUICK_SEARCHES -> {
                val row = quickSearchRepository.findByName(key)
                    ?.takeIf { (it.username == null || it.username == username) && it.deleted } ?: return false
                row.deleted = false
                row.lastModified = stamp()
                if (row.username == null) row.username = username
                quickSearchRepository.save(row)
                syncService.stampServerProvenance(username, SyncService.TAG_QUICK_SEARCH, key)
                true
            }
            Entity.DOWNLOAD_LABELS -> {
                val row = downloadLabelRepository.findByLabel(key)
                    ?.takeIf { (it.username == null || it.username == username) && it.deleted } ?: return false
                row.deleted = false
                row.lastModified = stamp()
                if (row.username == null) row.username = username
                downloadLabelRepository.save(row)
                syncService.stampServerProvenance(username, SyncService.TAG_DOWNLOAD_LABEL, key)
                true
            }
        }
    }

    // ── purge ───────────────────────────────────────────────────

    /**
     * 物理清除：
     * - items 为空/缺省 = 清全部到期项（deleted=true 且 lastModified < now - retentionDays）
     *   + 到期 ehSession 墓碑（静默）+ trash 孤儿目录；
     * - items 非空 = 立即清除指定项（不看保留期，用户在回收站显式点「永久删除」）。
     * @param username 非空时只清该用户的墓碑（显式项路径）；null（定时任务）清全部。
     */
    @Transactional
    fun purge(username: String?, request: RecyclePurgeRequest?): RecyclePurgeResponse {
        val counts = LinkedHashMap<String, Int>()
        var purged = 0
        val items = request?.items
        if (items.isNullOrEmpty()) {
            val cutoff = System.currentTimeMillis() - retentionDays * DAY_MS
            purged += purgeExpired(counts, cutoff)
            val expiredSessions = ehSessionRepository.findByDeletedTrueAndLastModifiedBefore(cutoff)
            if (expiredSessions.isNotEmpty()) {
                ehSessionRepository.deleteAll(expiredSessions)
                counts["ehSession"] = expiredSessions.size
            }
            trashMoveHandler.purgeOrphanTrash(cutoff)
        } else {
            items.forEach { item ->
                val e = Entity.fromWire(item.entity)
                if (e != null && purgeOne(username, e, item.key)) {
                    purged++
                    counts.merge(e.wire, 1, Int::plus)
                }
            }
        }
        publishCounts(counts)
        return RecyclePurgeResponse(purged = purged)
    }

    /**
     * 定时到期清除（默认每日一次）。间隔可配：anotherviewer.sync.trash-purge-interval-ms。
     */
    @Scheduled(fixedDelayString = "\${anotherviewer.sync.trash-purge-interval-ms:86400000}")
    fun purgeExpiredScheduled() {
        try {
            purge(null, null)
        } catch (e: Exception) {
            // 调度线程兜底：单轮失败不得中断后续轮次。
            logger.warn("Scheduled trash purge failed: {}", e.message)
        }
    }

    /** 到期行（全部用户）物理删除；download 行同时清其 trash 目录。 */
    private fun purgeExpired(counts: MutableMap<String, Int>, cutoff: Long): Int {
        var purged = 0
        fun <T : Any> sweep(wire: String, rows: List<T>, beforeDelete: (List<T>) -> Unit, delete: (List<T>) -> Unit) {
            if (rows.isEmpty()) return
            beforeDelete(rows)
            delete(rows)
            counts.merge(wire, rows.size, Int::plus)
            purged += rows.size
        }
        sweep(Entity.FAVORITES.wire, favoriteRepository.findByDeletedTrueAndLastModifiedBefore(cutoff), {}, favoriteRepository::deleteAll)
        sweep(Entity.HISTORY.wire, historyRepository.findByDeletedTrueAndLastModifiedBefore(cutoff), {}, historyRepository::deleteAll)
        sweep(
            Entity.DOWNLOADS.wire,
            downloadRepository.findByDeletedTrueAndLastModifiedBefore(cutoff),
            { rows -> rows.forEach { trashMoveHandler.purgeTrashDir(it.gid, it.downloadDir) } },
            downloadRepository::deleteAll,
        )
        sweep(Entity.BOOKMARKS.wire, bookmarkRepository.findByDeletedTrueAndLastModifiedBefore(cutoff), {}, bookmarkRepository::deleteAll)
        sweep(Entity.FILTERS.wire, filterRepository.findByDeletedTrueAndLastModifiedBefore(cutoff), {}, filterRepository::deleteAll)
        sweep(Entity.QUICK_SEARCHES.wire, quickSearchRepository.findByDeletedTrueAndLastModifiedBefore(cutoff), {}, quickSearchRepository::deleteAll)
        sweep(Entity.DOWNLOAD_LABELS.wire, downloadLabelRepository.findByDeletedTrueAndLastModifiedBefore(cutoff), {}, downloadLabelRepository::deleteAll)
        return purged
    }

    /** 指定墓碑行立即物理删除（download 含 trash 目录）。 */
    private fun purgeOne(username: String?, e: Entity, key: String): Boolean {
        fun ownedByUser(rowUsername: String?): Boolean = username == null || rowUsername == null || rowUsername == username
        return when (e) {
            Entity.FAVORITES -> {
                val gid = key.toLongOrNull() ?: return false
                val row = favoriteRepository.findAllByGid(gid)
                    .firstOrNull { it.deleted && ownedByUser(it.username) } ?: return false
                favoriteRepository.delete(row); true
            }
            Entity.HISTORY -> {
                val gid = key.toLongOrNull() ?: return false
                val row = historyRepository.findAllByGid(gid)
                    .firstOrNull { it.deleted && ownedByUser(it.username) } ?: return false
                historyRepository.delete(row); true
            }
            Entity.DOWNLOADS -> {
                val gid = key.toLongOrNull() ?: return false
                val row = downloadRepository.findAllByGid(gid)
                    .firstOrNull { it.deleted && ownedByUser(it.username) } ?: return false
                trashMoveHandler.purgeTrashDir(row.gid, row.downloadDir)
                downloadRepository.delete(row); true
            }
            Entity.BOOKMARKS -> {
                val gid = key.toLongOrNull() ?: return false
                val row = bookmarkRepository.findAllByGid(gid)
                    .firstOrNull { it.deleted && ownedByUser(it.username) } ?: return false
                bookmarkRepository.delete(row); true
            }
            Entity.FILTERS -> {
                val type = key.substringBefore(':').toIntOrNull() ?: return false
                val text = key.substringAfter(':', "")
                val row = filterRepository.findByTypeAndTextIgnoreCaseOrderByLastModifiedDesc(type, text)
                    .firstOrNull { it.deleted && ownedByUser(it.username) } ?: return false
                filterRepository.delete(row); true
            }
            Entity.QUICK_SEARCHES -> {
                val row = quickSearchRepository.findByName(key)
                    ?.takeIf { it.deleted && ownedByUser(it.username) } ?: return false
                quickSearchRepository.delete(row); true
            }
            Entity.DOWNLOAD_LABELS -> {
                val row = downloadLabelRepository.findByLabel(key)
                    ?.takeIf { it.deleted && ownedByUser(it.username) } ?: return false
                downloadLabelRepository.delete(row); true
            }
        }
    }

    /** 聚合非空计数发 sync.update（恢复/清除都是服务器自身动作 → deviceId="server"）。 */
    private fun publishCounts(counts: Map<String, Int>) {
        if (counts.isEmpty()) return
        eventPublisher.publishEvent(
            SyncUpdateEvent(deviceId = "server", counts = counts, serverTimestamp = syncClock.next())
        )
    }

    private companion object {
        /** pageSize 上限（与 REST 列表端点同口径）。 */
        const val MAX_PAGE_SIZE = 200
        const val DAY_MS = 86_400_000L
    }
}
