package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.dto.*
import com.hippo.anotherviewer.web.entity.*
import com.hippo.anotherviewer.web.repository.*
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest

private const val SKEW_TOLERANCE = 5000L

/** 平台判定（契约 v2 §1.4）：deviceId 前缀 `android-` → android，其余（`web-`、`server-` 等）→ web 侧。 */
private enum class SyncPlatform { ANDROID, WEB }

private fun platformOf(deviceId: String): SyncPlatform =
    if (deviceId.startsWith("android-")) SyncPlatform.ANDROID else SyncPlatform.WEB

@Service
class SyncService(
    private val favoriteRepository: LocalFavoriteInfoRepository,
    private val historyRepository: HistoryInfoRepository,
    private val downloadRepository: DownloadInfoRepository,
    private val bookmarkRepository: BookmarkInfoRepository,
    private val filterRepository: FilterRepository,
    private val quickSearchRepository: QuickSearchRepository,
    private val downloadLabelRepository: DownloadLabelRepository,
    private val deviceRepository: SyncDeviceRepository,
    private val preferenceRepository: UserPreferenceRepository,
    private val preferenceService: UserPreferenceService,
    private val serverConfig: ServerConfigService,
    private val ehSessionRepository: EhSessionRepository,
    private val siteSessionManager: SiteSessionManager,
) {

    private val logger = org.slf4j.LoggerFactory.getLogger(SyncService::class.java)

    // ---- SyncPolicy（契约 v2 §8，ADR-0003 D1/D2/D3/D4） ----

    /** 当前策略；存储值被篡改/越界时回退契约缺省（device_priority / 1 / 900）。 */
    fun currentPolicy(): SyncPolicyDto = SyncPolicyDto(
        conflictStrategy = ConflictStrategy.fromWire(
            serverConfig.get(ServerConfigService.KEY_SYNC_CONFLICT_STRATEGY, ConflictStrategy.DEVICE_PRIORITY.wire)
        ) ?: ConflictStrategy.DEVICE_PRIORITY,
        clientTier = serverConfig.get(ServerConfigService.KEY_SYNC_CLIENT_TIER, "1")
            .toIntOrNull()?.takeIf { it in CLIENT_TIER_RANGE } ?: CLIENT_TIER_DEFAULT,
        autoSyncIntervalSec = serverConfig.get(ServerConfigService.KEY_SYNC_AUTO_SYNC_INTERVAL_SEC, "900")
            .toIntOrNull()?.takeIf { it >= 0 } ?: AUTO_SYNC_INTERVAL_SEC_DEFAULT,
    )

    /** §8 校验：非法值 → 返回错误消息；合法 → null。conflictStrategy 由 Jackson 反序列化约束为枚举。 */
    fun validatePolicy(policy: SyncPolicyDto): String? = when {
        policy.clientTier !in CLIENT_TIER_RANGE -> "clientTier must be one of $CLIENT_TIER_RANGE"
        policy.autoSyncIntervalSec < 0 -> "autoSyncIntervalSec must be >= 0"
        else -> null
    }

    /**
     * 持久化 policy（等价 PUT，跨重启）。调用方须先过 [validatePolicy]。
     * android push 携带 policy 时走此路径（D2 权威覆盖）；WebUI PUT 亦走此路径，
     * 但会被下一次携带 policy 的 android push 覆盖（UI 须明示，契约 §8）。
     */
    fun updatePolicy(policy: SyncPolicyDto): SyncPolicyDto {
        validatePolicy(policy)?.let { throw IllegalArgumentException(it) }
        serverConfig.set(ServerConfigService.KEY_SYNC_CONFLICT_STRATEGY, policy.conflictStrategy.wire)
        serverConfig.set(ServerConfigService.KEY_SYNC_CLIENT_TIER, policy.clientTier.toString())
        serverConfig.set(ServerConfigService.KEY_SYNC_AUTO_SYNC_INTERVAL_SEC, policy.autoSyncIntervalSec.toString())
        return policy
    }

    @Transactional
    fun push(request: SyncPushRequest, username: String): SyncPushResponse {
        // D2（契约 §8）：android 平台 push 携带 policy = 权威覆盖并立即持久化（等价 PUT）；
        // 策略即时生效于其后的 merge——本次 push 的实体即按新策略合并（切换不追溯已收敛数据）。
        // web 端 push 携带 policy：忽略该字段——WebUI 改策略走 PUT /sync/policy，
        // 且会被下一次 android push 覆盖，push 通道不作为 web 的策略入口（契约 §8）。
        request.policy?.let { policy ->
            if (platformOf(request.deviceId) == SyncPlatform.ANDROID) updatePolicy(policy)
        }
        val strategy = currentPolicy().conflictStrategy

        adoptNullOwnership(username)
        var conflicts = 0
        val e = request.entities
        val pushDeviceId = request.deviceId

        conflicts += e.favorites.sumOf { if (mergeFavorite(it, username, strategy, pushDeviceId)) 1 else 0 }
        conflicts += e.history.sumOf { if (mergeHistory(it, username, strategy, pushDeviceId)) 1 else 0 }
        conflicts += e.downloads.sumOf { if (mergeDownload(it, username, strategy, pushDeviceId)) 1 else 0 }
        conflicts += e.bookmarks.sumOf { if (mergeBookmark(it, username, strategy, pushDeviceId)) 1 else 0 }
        conflicts += e.filters.sumOf { if (mergeFilter(it, username, strategy, pushDeviceId)) 1 else 0 }
        conflicts += e.quickSearches.sumOf { if (mergeQuickSearch(it, username, strategy, pushDeviceId)) 1 else 0 }
        conflicts += e.downloadLabels.sumOf { if (mergeDownloadLabel(it, username, strategy, pushDeviceId)) 1 else 0 }
        // ehSession（ADR-0004）：单例 LWW、策略独立，不参与 conflictStrategy。
        conflicts += e.ehSession.sumOf { if (mergeEhSession(it, pushDeviceId)) 1 else 0 }

        e.preferences?.let { pref ->
            // last-write-wins: 仅当推送方 lastModified 明显新于存量 updatedAt 时覆盖（含时钟偏差容忍）。
            // preferences 不受 conflictStrategy 影响（契约 §1.3，ADR-0001：后同步者即最终设置）。
            preferenceService.replace(username, pref.preferences, pref.deviceId, pref.lastModified)
        }

        val now = System.currentTimeMillis()
        updateDevice(request.deviceId, now, username)

        return SyncPushResponse(success = true, serverTimestamp = now, conflicts = conflicts)
    }

    fun pull(since: Long, username: String, deviceId: String = ""): SyncPullResponse {
        adoptNullOwnership(username)
        val now = System.currentTimeMillis()
        if (deviceId.isNotEmpty()) updateDevice(deviceId, now, username)
        val prefEntity = preferenceRepository.findByUsername(username)
        // H-3: 增量拉取走 (username, lastModified) 派生查询，不再 findAll 全表扫描后内存过滤。
        // since == 0 是全量拉取：lastModified=0 的合法记录（如 time=0 的旧下载记录）必须一并
        // 返回，故用 findByUsername（不过滤 lastModified），不得回归 H-3 的 since=0 边界。
        fun <T> select(full: (String) -> List<T>, delta: (String, Long) -> List<T>): List<T> =
            if (since == 0L) full(username) else delta(username, since)
        val favorites = select(favoriteRepository::findByUsername, favoriteRepository::findByUsernameAndLastModifiedGreaterThan)
        val history = select(historyRepository::findByUsername, historyRepository::findByUsernameAndLastModifiedGreaterThan)
        val downloads = select(downloadRepository::findByUsername, downloadRepository::findByUsernameAndLastModifiedGreaterThan)
        val bookmarks = select(bookmarkRepository::findByUsername, bookmarkRepository::findByUsernameAndLastModifiedGreaterThan)
        val filters = select(filterRepository::findByUsername, filterRepository::findByUsernameAndLastModifiedGreaterThan)
        val quickSearches = select(quickSearchRepository::findByUsername, quickSearchRepository::findByUsernameAndLastModifiedGreaterThan)
        val downloadLabels = select(downloadLabelRepository::findByUsername, downloadLabelRepository::findByUsernameAndLastModifiedGreaterThan)
        // M-14: wire 上的 label 是标签名，落库是 download_label.id；一次拉取只解析一次 id→名字映射。
        val labelNames = downloadLabels.mapNotNull { l -> if (l.id == 0L) null else l.id.toInt() to l.label }.toMap()
        // ehSession（ADR-0004）：单例行；增量语义 = 仅当 lastModified > since 才返回（since=0 全量）。
        val ehSessionDto = siteSessionManager.loadSyncEhSession()
        val ehSessions = if (ehSessionDto != null && (since == 0L || ehSessionDto.lastModified > since)) {
            listOf(ehSessionDto)
        } else {
            emptyList()
        }
        // v2: deviceId 回显行级来源（last-writer），供客户端按策略本地 merge（契约 §6.2）；
        // 无来源记录的旧行保持 "server"（platformOf 归 web 侧，契约 §1.4）。
        val entities = SyncEntityCollection(
            favorites = favorites.map { it.toSyncFavoriteDto(provenanceOf(username, TAG_FAVORITE, it.gid.toString())) },
            history = history.map { it.toSyncHistoryDto(provenanceOf(username, TAG_HISTORY, it.gid.toString())) },
            downloads = downloads.map { it.toSyncDownloadDto(labelNames, provenanceOf(username, TAG_DOWNLOAD, it.gid.toString())) },
            bookmarks = bookmarks.map { it.toSyncBookmarkDto(provenanceOf(username, TAG_BOOKMARK, it.gid.toString())) },
            filters = filters.map { it.toSyncFilterDto(provenanceOf(username, TAG_FILTER, filterKey(it.type, it.text))) },
            quickSearches = quickSearches.map { it.toSyncQuickSearchDto(provenanceOf(username, TAG_QUICK_SEARCH, it.name)) },
            downloadLabels = downloadLabels.map { it.toSyncDownloadLabelDto(provenanceOf(username, TAG_DOWNLOAD_LABEL, it.label)) },
            ehSession = ehSessions,
            preferences = SyncPreferencesDto(
                preferences = preferenceService.getRaw(username),
                lastModified = prefEntity?.updatedAt ?: 0,
                deviceId = prefEntity?.updatedBy ?: "",
            ),
        )
        // v2: pull 附当前 policy（契约 §8）；旧客户端忽略未知字段。
        return SyncPullResponse(entities = entities, serverTimestamp = now, policy = currentPolicy())
    }

    fun status(username: String): SyncStatusResponse {
        val devices = deviceRepository.findAll().filter { it.username == username }
        val lastSync = devices.maxOfOrNull { it.lastSyncTimestamp } ?: 0L
        return SyncStatusResponse(
            lastSyncTimestamp = lastSync,
            connectedDevices = devices.map {
                ConnectedDeviceDto(
                    deviceId = it.deviceId,
                    deviceName = it.deviceName,
                    platform = it.platform,
                    lastSeen = it.lastSeen,
                )
            },
            entityCounts = EntityCountsDto(
                favorites = favoriteRepository.countByUsername(username),
                history = historyRepository.countByUsername(username),
                downloads = downloadRepository.countByUsername(username),
                bookmarks = bookmarkRepository.countByUsername(username),
                filters = filterRepository.countByUsername(username),
                quickSearches = quickSearchRepository.countByUsername(username),
                downloadLabels = downloadLabelRepository.countByUsername(username),
                ehSession = if (ehSessionRepository.findByUsername(SiteSessionManager.EH_SESSION_OWNER)?.deleted == false) 1 else 0,
            ),
        )
    }

    fun listDevices(username: String): List<DeviceInfoDto> =
        deviceRepository.findAll()
            .filter { it.username == username }
            .map {
                DeviceInfoDto(
                    deviceId = it.deviceId,
                    deviceName = it.deviceName ?: it.deviceId,
                    platform = it.platform,
                    pairedAt = it.pairedAt,
                    lastSeen = it.lastSeen,
                )
            }
            .sortedByDescending { it.pairedAt }

    // ---- Ownership migration ----

    /**
     * Legacy rows (created before per-user scoping) have a null username.
     * The first user to sync claims them by stamping their username and a
     * minimal lastModified so the very first pull (since = 0) delivers them.
     */
    private fun adoptNullOwnership(username: String) {
        // MASTER-2026-08-22 P3：迁移完成后短路。NULL 行收养是一次性数据迁移，
        // 但此前每次 push/pull 都做 7 张表的全表 NULL 扫描，迁移完成后是纯开销。
        // 标志按 username 记录（多用户场景换人首同步仍会完整扫一遍）；导入/还原
        // 均显式落 username，不会再生 NULL 行——若手工恢复出 NULL 行，删除
        // server_config 键 sync.ownership.adopted.<user> 即可重触发。
        val flagKey = "$KEY_SYNC_OWNERSHIP_ADOPTED.$username"
        if (serverConfig.getBoolean(flagKey, false)) return
        fun <T : Any> adopt(list: List<T>, setOwner: (T, String) -> Unit, save: (T) -> Unit) {
            list.forEach {
                setOwner(it, username)
                save(it)
            }
        }
        adopt(favoriteRepository.findAllByUsernameIsNull(), { e, u -> e.username = u; e.lastModified = maxOf(e.lastModified, 1L) }, { favoriteRepository.save(it) })
        adopt(historyRepository.findAllByUsernameIsNull(), { e, u -> e.username = u; e.lastModified = maxOf(e.lastModified, 1L) }, { historyRepository.save(it) })
        adopt(downloadRepository.findAllByUsernameIsNull(), { e, u -> e.username = u; e.lastModified = maxOf(e.lastModified, 1L) }, { downloadRepository.save(it) })
        adopt(bookmarkRepository.findAllByUsernameIsNull(), { e, u -> e.username = u; e.lastModified = maxOf(e.lastModified, 1L) }, { bookmarkRepository.save(it) })
        adopt(filterRepository.findAllByUsernameIsNull(), { e, u -> e.username = u; e.lastModified = maxOf(e.lastModified, 1L) }, { filterRepository.save(it) })
        adopt(quickSearchRepository.findAllByUsernameIsNull(), { e, u -> e.username = u; e.lastModified = maxOf(e.lastModified, 1L) }, { quickSearchRepository.save(it) })
        adopt(downloadLabelRepository.findAllByUsernameIsNull(), { e, u -> e.username = u; e.lastModified = maxOf(e.lastModified, 1L) }, { downloadLabelRepository.save(it) })
        // 复核一遍：全部仓库均无剩余 NULL 行才置位（并发写入理论上可能再生）。
        val remaining = favoriteRepository.findAllByUsernameIsNull().isNotEmpty() ||
            historyRepository.findAllByUsernameIsNull().isNotEmpty() ||
            downloadRepository.findAllByUsernameIsNull().isNotEmpty() ||
            bookmarkRepository.findAllByUsernameIsNull().isNotEmpty() ||
            filterRepository.findAllByUsernameIsNull().isNotEmpty() ||
            quickSearchRepository.findAllByUsernameIsNull().isNotEmpty() ||
            downloadLabelRepository.findAllByUsernameIsNull().isNotEmpty()
        if (!remaining) {
            serverConfig.setBoolean(flagKey, true)
            logger.info("Legacy null-username rows fully adopted for user '{}'; future scans short-circuited", username)
        }
    }

    /** True when the row is unowned or owned by [username] (never another user's). */
    private fun <T> ownedBy(existing: T?, username: String, owner: (T) -> String?): T? {
        if (existing == null) return null
        val ownerName = owner(existing)
        return if (ownerName == null || ownerName == username) existing else null
    }

    // ---- Merge strategies (per contracts/sync-conflict-rules.md v2) ----
    //
    // 所有 merge 带 strategy 参数（§1.4）；策略 B（lww）路径与 v1.0 语义逐字等价，
    // 完整回退兜底。A/C 下同键跨平台冲突无条件优先端胜，lastModified 不参与仲裁
    // （仅高水位/增量 pull/展示）；同平台同键回退 B 语义。

    /** Favorites: union merge with soft-delete tombstones; 删除/复活按 §3.8 soft 实体规则。 */
    private fun mergeFavorite(incoming: SyncFavoriteDto, username: String, strategy: ConflictStrategy, pushDeviceId: String): Boolean {
        val incomingDevice = incoming.deviceId.ifEmpty { pushDeviceId }
        val incomingPlatform = platformOf(incomingDevice)
        val naturalKey = incoming.gid.toString()
        val raw = favoriteRepository.findByGid(incoming.gid)
        val existing = ownedBy(raw, username) { it.username }
        if (existing == null) {
            // No own record: store the incoming row, tombstones included, so
            // deletions propagate to other devices (contract §4.1).
            if (raw == null) {
                favoriteRepository.save(incoming.toFavoriteEntity(username))
                recordProvenance(username, TAG_FAVORITE, naturalKey, incomingDevice)
            }
            return false
        }
        val existingPlatform = platformOf(provenanceOf(username, TAG_FAVORITE, naturalKey))

        // delete vs alive（soft 实体，§3.8）
        if (incoming.deleted != existing.deleted) {
            val incomingIsTomb = incoming.deleted
            val tombPlatform = if (incomingIsTomb) incomingPlatform else existingPlatform
            val livePlatform = if (incomingIsTomb) existingPlatform else incomingPlatform
            val liveWins = softDeleteLiveWins(strategy, tombPlatform, livePlatform)
            if (liveWins) {
                if (!incomingIsTomb) {
                    // incoming live 复活墓碑（B：任何设备 live push 即复活；A/C：优先端 live 胜非优先端 tomb）
                    applyFavoriteFields(existing, incoming)
                    favoriteRepository.save(existing)
                    recordProvenance(username, TAG_FAVORITE, naturalKey, incomingDevice)
                    return true
                }
                return false // incoming tombstone + existing alive -> keep alive
            }
            if (incomingIsTomb) {
                // 优先端删除无条件传播：覆盖非优先端活记录，落 deleted=true 行（§4.1）
                applyFavoriteFields(existing, incoming)
                favoriteRepository.save(existing)
                recordProvenance(username, TAG_FAVORITE, naturalKey, incomingDevice)
                return true
            }
            return false // 非优先端 live vs 优先端 tomb → tomb 胜（保留墓碑）
        }

        // 同态（双活或双墓碑）
        priorityIncomingWins(strategy, existingPlatform, incomingPlatform)?.let { incomingWins ->
            if (incomingWins) {
                applyFavoriteFields(existing, incoming)
                favoriteRepository.save(existing)
                recordProvenance(username, TAG_FAVORITE, naturalKey, incomingDevice)
                return true
            }
            return false
        }
        // B（v1 完整语义）或同平台回退：LWW ± skew。
        if (incoming.lastModified > existing.lastModified + SKEW_TOLERANCE) {
            applyFavoriteFields(existing, incoming)
            favoriteRepository.save(existing)
            recordProvenance(username, TAG_FAVORITE, naturalKey, incomingDevice)
            return true
        }
        return false
    }

    /** History: last-write-wins；删除保留墓碑行（deleted=true + lastModified bump），增量 pull 才能传播删除。 */
    private fun mergeHistory(incoming: SyncHistoryDto, username: String, strategy: ConflictStrategy, pushDeviceId: String): Boolean {
        val incomingDevice = incoming.deviceId.ifEmpty { pushDeviceId }
        val incomingPlatform = platformOf(incomingDevice)
        val naturalKey = incoming.gid.toString()
        val raw = historyRepository.findByGid(incoming.gid)
        val existing = ownedBy(raw, username) { it.username }
        if (incoming.deleted) {
            // 软删: 不真删行，bump lastModified 使 since>0 的增量 pull 能取到墓碑。
            // tombstone 实体：删除任何策略下传播（§3.8），v1 行为保持不变。
            if (existing != null) {
                existing.deleted = true
                existing.lastModified = maxOf(existing.lastModified, incoming.lastModified)
                historyRepository.save(existing)
                recordProvenance(username, TAG_HISTORY, naturalKey, incomingDevice)
            } else if (raw == null) {
                // 未知 gid 也存墓碑，删除同样能传播到其他设备
                historyRepository.save(incoming.toHistoryEntity(username))
                recordProvenance(username, TAG_HISTORY, naturalKey, incomingDevice)
            }
            return false
        }
        if (existing == null) {
            if (raw == null) {
                historyRepository.save(incoming.toHistoryEntity(username))
                recordProvenance(username, TAG_HISTORY, naturalKey, incomingDevice)
            }
            return false
        }
        if (!existing.deleted) {
            // 双活同键：A/C 跨平台无条件优先端胜（§1.4）；存量是墓碑时不走策略序——
            // tombstone 实体复活按实体专属 LWW（§3.8 镜像，v1 同）。
            priorityIncomingWins(strategy, platformOf(provenanceOf(username, TAG_HISTORY, naturalKey)), incomingPlatform)?.let { incomingWins ->
                if (incomingWins) {
                    applyHistoryFields(existing, incoming)
                    historyRepository.save(existing)
                    recordProvenance(username, TAG_HISTORY, naturalKey, incomingDevice)
                    return true
                }
                return false
            }
        }
        // B（v1 完整语义）/ 同平台回退 / 墓碑复活：LWW ± skew。
        if (incoming.lastModified > existing.lastModified + SKEW_TOLERANCE) {
            applyHistoryFields(existing, incoming)
            historyRepository.save(existing)
            recordProvenance(username, TAG_HISTORY, naturalKey, incomingDevice)
            return true
        }
        if (existing.lastModified > incoming.lastModified + SKEW_TOLERANCE) return false
        // Within skew: prefer the later view time.
        if (incoming.time > existing.time) {
            applyHistoryFields(existing, incoming)
            historyRepository.save(existing)
            recordProvenance(username, TAG_HISTORY, naturalKey, incomingDevice)
            return true
        }
        return false
    }

    /** Downloads: union merge + status sync (soft delete, tombstone stored); 删除/复活按 §3.8 soft 实体规则。 */
    private fun mergeDownload(incoming: SyncDownloadDto, username: String, strategy: ConflictStrategy, pushDeviceId: String): Boolean {
        val incomingDevice = incoming.deviceId.ifEmpty { pushDeviceId }
        val incomingPlatform = platformOf(incomingDevice)
        val naturalKey = incoming.gid.toString()
        val raw = downloadRepository.findByGid(incoming.gid)
        val existing = ownedBy(raw, username) { it.username }
        if (existing == null) {
            if (raw == null) {
                downloadRepository.save(incoming.toDownloadEntity(username))
                recordProvenance(username, TAG_DOWNLOAD, naturalKey, incomingDevice)
            }
            return false
        }
        val existingPlatform = platformOf(provenanceOf(username, TAG_DOWNLOAD, naturalKey))

        // delete vs alive（soft 实体，§3.8）
        if (incoming.deleted != existing.deleted) {
            val incomingIsTomb = incoming.deleted
            val tombPlatform = if (incomingIsTomb) incomingPlatform else existingPlatform
            val livePlatform = if (incomingIsTomb) existingPlatform else incomingPlatform
            val liveWins = softDeleteLiveWins(strategy, tombPlatform, livePlatform)
            if (liveWins) {
                if (!incomingIsTomb) {
                    applyDownloadFields(existing, incoming, username)
                    downloadRepository.save(existing)
                    recordProvenance(username, TAG_DOWNLOAD, naturalKey, incomingDevice)
                    return true
                }
                return false // incoming tombstone + existing alive -> keep alive
            }
            if (incomingIsTomb) {
                // 优先端删除无条件传播，落 deleted=true 行（§4.1）
                applyDownloadFields(existing, incoming, username)
                downloadRepository.save(existing)
                recordProvenance(username, TAG_DOWNLOAD, naturalKey, incomingDevice)
                return true
            }
            return false // 非优先端 live vs 优先端 tomb → tomb 胜
        }

        // 同态（双活或双墓碑）：mutable state fields 按策略仲裁。
        priorityIncomingWins(strategy, existingPlatform, incomingPlatform)?.let { incomingWins ->
            if (incomingWins) {
                applyDownloadFields(existing, incoming, username)
                downloadRepository.save(existing)
                recordProvenance(username, TAG_DOWNLOAD, naturalKey, incomingDevice)
                return true
            }
            return false
        }
        // B（v1 完整语义）或同平台回退：last-write-wins。
        if (incoming.lastModified > existing.lastModified + SKEW_TOLERANCE) {
            applyDownloadFields(existing, incoming, username)
            downloadRepository.save(existing)
            recordProvenance(username, TAG_DOWNLOAD, naturalKey, incomingDevice)
            return true
        }
        return false
    }

    /** Bookmarks: last-write-wins；删除保留墓碑行（deleted=true + lastModified bump），增量 pull 才能传播删除。 */
    private fun mergeBookmark(incoming: SyncBookmarkDto, username: String, strategy: ConflictStrategy, pushDeviceId: String): Boolean {
        val incomingDevice = incoming.deviceId.ifEmpty { pushDeviceId }
        val incomingPlatform = platformOf(incomingDevice)
        val naturalKey = incoming.gid.toString()
        val raw = bookmarkRepository.findByGid(incoming.gid)
        val existing = ownedBy(raw, username) { it.username }
        if (incoming.deleted) {
            // 软删: 不真删行，bump lastModified 使 since>0 的增量 pull 能取到墓碑。
            // tombstone 实体：删除任何策略下传播（§3.8），v1 行为保持不变。
            if (existing != null) {
                existing.deleted = true
                existing.lastModified = maxOf(existing.lastModified, incoming.lastModified)
                bookmarkRepository.save(existing)
                recordProvenance(username, TAG_BOOKMARK, naturalKey, incomingDevice)
            } else if (raw == null) {
                // 未知 gid 也存墓碑，删除同样能传播到其他设备
                bookmarkRepository.save(incoming.toBookmarkEntity(username))
                recordProvenance(username, TAG_BOOKMARK, naturalKey, incomingDevice)
            }
            return false
        }
        if (existing == null) {
            if (raw == null) {
                bookmarkRepository.save(incoming.toBookmarkEntity(username))
                recordProvenance(username, TAG_BOOKMARK, naturalKey, incomingDevice)
            }
            return false
        }
        if (!existing.deleted) {
            // 双活同键：A/C 跨平台无条件优先端胜（§1.4）；存量是墓碑时不走策略序——
            // tombstone 实体复活按实体专属 LWW（§3.8 镜像，v1 同）。
            priorityIncomingWins(strategy, platformOf(provenanceOf(username, TAG_BOOKMARK, naturalKey)), incomingPlatform)?.let { incomingWins ->
                if (incomingWins) {
                    applyBookmarkFields(existing, incoming)
                    bookmarkRepository.save(existing)
                    recordProvenance(username, TAG_BOOKMARK, naturalKey, incomingDevice)
                    return true
                }
                return false
            }
        }
        // B（v1 完整语义）/ 同平台回退 / 墓碑复活：LWW ± skew。
        if (incoming.lastModified > existing.lastModified + SKEW_TOLERANCE) {
            applyBookmarkFields(existing, incoming)
            bookmarkRepository.save(existing)
            recordProvenance(username, TAG_BOOKMARK, naturalKey, incomingDevice)
            return true
        }
        if (existing.lastModified > incoming.lastModified + SKEW_TOLERANCE) return false
        // Within skew: prefer higher page (further progress).
        val existingPage = existing.note?.toIntOrNull() ?: 0
        if (incoming.page > existingPage) {
            applyBookmarkFields(existing, incoming)
            bookmarkRepository.save(existing)
            recordProvenance(username, TAG_BOOKMARK, naturalKey, incomingDevice)
            return true
        }
        return false
    }

    /** Filters: union merge keyed by (mode, text); local-wins outside skew, additive bias inside; 删除/复活按 §3.8 soft 实体规则。 */
    private fun mergeFilter(incoming: SyncFilterDto, username: String, strategy: ConflictStrategy, pushDeviceId: String): Boolean {
        val incomingDevice = incoming.deviceId.ifEmpty { pushDeviceId }
        val incomingPlatform = platformOf(incomingDevice)
        val naturalKey = filterKey(incoming.mode, incoming.text)
        // MASTER-2026-08-22 P8：派生查询替代 findAll 全表扫描 + 内存 firstOrNull。
        val raw = filterRepository.findByTypeAndText(incoming.mode, incoming.text)
        val existing = ownedBy(raw, username) { it.username }
        if (existing == null) {
            if (raw == null) {
                filterRepository.save(incoming.toFilterEntity(username))
                recordProvenance(username, TAG_FILTER, naturalKey, incomingDevice)
            }
            return false
        }
        val existingPlatform = platformOf(provenanceOf(username, TAG_FILTER, naturalKey))

        // delete vs alive（soft 实体，§3.8）
        if (incoming.deleted != existing.deleted) {
            val incomingIsTomb = incoming.deleted
            val tombPlatform = if (incomingIsTomb) incomingPlatform else existingPlatform
            val livePlatform = if (incomingIsTomb) existingPlatform else incomingPlatform
            val liveWins = softDeleteLiveWins(strategy, tombPlatform, livePlatform)
            if (liveWins) {
                if (!incomingIsTomb) {
                    applyFilterFields(existing, incoming)
                    filterRepository.save(existing)
                    recordProvenance(username, TAG_FILTER, naturalKey, incomingDevice)
                    return true
                }
                return false // incoming tombstone + existing alive -> keep alive
            }
            if (incomingIsTomb) {
                // 优先端删除无条件传播，落 deleted=true 行（§4.1）
                applyFilterFields(existing, incoming)
                filterRepository.save(existing)
                recordProvenance(username, TAG_FILTER, naturalKey, incomingDevice)
                return true
            }
            return false // 非优先端 live vs 优先端 tomb → tomb 胜
        }

        // 同态（双活或双墓碑）
        priorityIncomingWins(strategy, existingPlatform, incomingPlatform)?.let { incomingWins ->
            if (incomingWins) {
                applyFilterFields(existing, incoming)
                filterRepository.save(existing)
                recordProvenance(username, TAG_FILTER, naturalKey, incomingDevice)
                return true
            }
            return false
        }
        // B（v1 完整语义）或同平台回退：local-wins outside skew, additive bias inside.
        if (existing.lastModified > incoming.lastModified + SKEW_TOLERANCE) return false
        if (incoming.lastModified > existing.lastModified + SKEW_TOLERANCE) {
            applyFilterFields(existing, incoming)
            filterRepository.save(existing)
            recordProvenance(username, TAG_FILTER, naturalKey, incomingDevice)
            return true
        }
        // Within skew: additive bias — prefer enabled=true.
        if (incoming.enabled != existing.enabled && incoming.enabled) {
            applyFilterFields(existing, incoming)
            filterRepository.save(existing)
            recordProvenance(username, TAG_FILTER, naturalKey, incomingDevice)
            return true
        }
        return false
    }

    /** QuickSearches: union merge keyed by name; local-wins outside skew; 删除/复活按 §3.8 soft 实体规则。 */
    private fun mergeQuickSearch(incoming: SyncQuickSearchDto, username: String, strategy: ConflictStrategy, pushDeviceId: String): Boolean {
        val incomingDevice = incoming.deviceId.ifEmpty { pushDeviceId }
        val incomingPlatform = platformOf(incomingDevice)
        val naturalKey = incoming.name
        val raw = quickSearchRepository.findByName(incoming.name)
        val existing = ownedBy(raw, username) { it.username }
        if (existing == null) {
            if (raw == null) {
                quickSearchRepository.save(incoming.toQuickSearchEntity(username))
                recordProvenance(username, TAG_QUICK_SEARCH, naturalKey, incomingDevice)
            }
            return false
        }
        val existingPlatform = platformOf(provenanceOf(username, TAG_QUICK_SEARCH, naturalKey))

        // delete vs alive（soft 实体，§3.8）
        if (incoming.deleted != existing.deleted) {
            val incomingIsTomb = incoming.deleted
            val tombPlatform = if (incomingIsTomb) incomingPlatform else existingPlatform
            val livePlatform = if (incomingIsTomb) existingPlatform else incomingPlatform
            val liveWins = softDeleteLiveWins(strategy, tombPlatform, livePlatform)
            if (liveWins) {
                if (!incomingIsTomb) {
                    applyQuickSearchFields(existing, incoming)
                    quickSearchRepository.save(existing)
                    recordProvenance(username, TAG_QUICK_SEARCH, naturalKey, incomingDevice)
                    return true
                }
                return false // incoming tombstone + existing alive -> keep alive
            }
            if (incomingIsTomb) {
                // 优先端删除无条件传播，落 deleted=true 行（§4.1）
                applyQuickSearchFields(existing, incoming)
                quickSearchRepository.save(existing)
                recordProvenance(username, TAG_QUICK_SEARCH, naturalKey, incomingDevice)
                return true
            }
            return false // 非优先端 live vs 优先端 tomb → tomb 胜
        }

        // 同态（双活或双墓碑）
        priorityIncomingWins(strategy, existingPlatform, incomingPlatform)?.let { incomingWins ->
            if (incomingWins) {
                applyQuickSearchFields(existing, incoming)
                quickSearchRepository.save(existing)
                recordProvenance(username, TAG_QUICK_SEARCH, naturalKey, incomingDevice)
                return true
            }
            return false
        }
        // B（v1 完整语义）或同平台回退：local-wins outside skew。
        if (existing.lastModified > incoming.lastModified + SKEW_TOLERANCE) return false
        if (incoming.lastModified > existing.lastModified + SKEW_TOLERANCE) {
            applyQuickSearchFields(existing, incoming)
            quickSearchRepository.save(existing)
            recordProvenance(username, TAG_QUICK_SEARCH, naturalKey, incomingDevice)
            return true
        }
        return false
    }

    /** DownloadLabels: union merge keyed by label name; local-wins outside skew; 删除/复活按 §3.8 soft 实体规则。 */
    private fun mergeDownloadLabel(incoming: SyncDownloadLabelDto, username: String, strategy: ConflictStrategy, pushDeviceId: String): Boolean {
        val incomingDevice = incoming.deviceId.ifEmpty { pushDeviceId }
        val incomingPlatform = platformOf(incomingDevice)
        val naturalKey = incoming.label
        val raw = downloadLabelRepository.findByLabel(incoming.label)
        val existing = ownedBy(raw, username) { it.username }
        if (existing == null) {
            if (raw == null) {
                downloadLabelRepository.save(incoming.toDownloadLabelEntity(username))
                recordProvenance(username, TAG_DOWNLOAD_LABEL, naturalKey, incomingDevice)
            }
            return false
        }
        val existingPlatform = platformOf(provenanceOf(username, TAG_DOWNLOAD_LABEL, naturalKey))

        // delete vs alive（soft 实体，§3.8）
        if (incoming.deleted != existing.deleted) {
            val incomingIsTomb = incoming.deleted
            val tombPlatform = if (incomingIsTomb) incomingPlatform else existingPlatform
            val livePlatform = if (incomingIsTomb) existingPlatform else incomingPlatform
            val liveWins = softDeleteLiveWins(strategy, tombPlatform, livePlatform)
            if (liveWins) {
                if (!incomingIsTomb) {
                    applyDownloadLabelFields(existing, incoming)
                    downloadLabelRepository.save(existing)
                    recordProvenance(username, TAG_DOWNLOAD_LABEL, naturalKey, incomingDevice)
                    return true
                }
                return false // incoming tombstone + existing alive -> keep alive
            }
            if (incomingIsTomb) {
                // 优先端删除无条件传播，落 deleted=true 行（§4.1）
                applyDownloadLabelFields(existing, incoming)
                downloadLabelRepository.save(existing)
                recordProvenance(username, TAG_DOWNLOAD_LABEL, naturalKey, incomingDevice)
                return true
            }
            return false // 非优先端 live vs 优先端 tomb → tomb 胜
        }

        // 同态（双活或双墓碑）
        priorityIncomingWins(strategy, existingPlatform, incomingPlatform)?.let { incomingWins ->
            if (incomingWins) {
                applyDownloadLabelFields(existing, incoming)
                downloadLabelRepository.save(existing)
                recordProvenance(username, TAG_DOWNLOAD_LABEL, naturalKey, incomingDevice)
                return true
            }
            return false
        }
        // B（v1 完整语义）或同平台回退：local-wins outside skew。
        if (existing.lastModified > incoming.lastModified + SKEW_TOLERANCE) return false
        if (incoming.lastModified > existing.lastModified + SKEW_TOLERANCE) {
            applyDownloadLabelFields(existing, incoming)
            downloadLabelRepository.save(existing)
            recordProvenance(username, TAG_DOWNLOAD_LABEL, naturalKey, incomingDevice)
            return true
        }
        return false
    }

    /**
     * ehSession（ADR-0004）：单例实体，LWW ±skew、策略独立（与 preferences 同级，A/C 平台序不适用）；
     * deleted=true tombstone 无条件传播。合并逻辑委托 [SiteSessionManager.applySyncEhSession]
     * （写库 + 生效到内存 cookieStore），返回 true = 覆盖了存量行（冲突计数）。
     */
    private fun mergeEhSession(incoming: SyncEhSessionDto, pushDeviceId: String): Boolean =
        siteSessionManager.applySyncEhSession(incoming, pushDeviceId)

    // ---- Strategy arbitration helpers（契约 v2 §1.4 / §3.8） ----

    /**
     * soft 实体（favorite/download/filter/quickSearch/downloadLabel）删除 vs 保留（§3.8）：
     * B = live 胜（v1 union：单边删除不传播）；A/C = 优先端删除无条件传播，
     * 非优先端删除 vs 优先端持有该键 → live 胜（保留），双方均非优先端 → 删除传播。
     * 返回 true = 保留 live 记录，false = tombstone 胜。
     */
    private fun softDeleteLiveWins(strategy: ConflictStrategy, tombPlatform: SyncPlatform, livePlatform: SyncPlatform): Boolean {
        if (strategy == ConflictStrategy.LWW) return true
        val prio = priorityPlatformOf(strategy)
        return when {
            tombPlatform == prio -> false
            livePlatform == prio -> true
            else -> false
        }
    }

    /**
     * A/C 同键跨平台冲突的无条件仲裁（§1.4）：返回 incoming 是否胜。
     * B 策略或同平台（如两个 android 设备）返回 null → 调用方回退 v1 LWW + skew tie-break。
     */
    private fun priorityIncomingWins(strategy: ConflictStrategy, existingPlatform: SyncPlatform, incomingPlatform: SyncPlatform): Boolean? {
        if (strategy == ConflictStrategy.LWW) return null
        if (existingPlatform == incomingPlatform) return null
        return incomingPlatform == priorityPlatformOf(strategy)
    }

    /** A → android 优先；C → web 优先。仅 A/C 调用。 */
    private fun priorityPlatformOf(strategy: ConflictStrategy): SyncPlatform = when (strategy) {
        ConflictStrategy.DEVICE_PRIORITY -> SyncPlatform.ANDROID
        ConflictStrategy.WEB_PRIORITY -> SyncPlatform.WEB
        ConflictStrategy.LWW -> throw IllegalStateException("lww has no priority platform; callers must guard")
    }

    // ---- Row provenance（行级 last-writer deviceId，A/C 平台判定依据） ----
    //
    // 契约 §3.8 以记录的 deviceId 判平台，但 v1 实体行不落 deviceId 列；此处以
    // server_config KV（键 sync.prov.{tag}.{sha1(username/key)}，值=last-writer deviceId）
    // 持久化行级来源，跨重启有效。无来源记录的旧行按 "server" 处理（platformOf 归
    // web 侧，契约 §1.4），与 pull 回显的缺省 deviceId 一致。

    private fun provenanceOf(username: String, tag: String, naturalKey: String): String =
        serverConfig.get(provenanceConfigKey(username, tag, naturalKey), PROVENANCE_FALLBACK_DEVICE)

    private fun recordProvenance(username: String, tag: String, naturalKey: String, deviceId: String) {
        serverConfig.set(provenanceConfigKey(username, tag, naturalKey), deviceId)
    }

    private fun provenanceConfigKey(username: String, tag: String, naturalKey: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$username/$naturalKey".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "${ServerConfigService.KEY_PREFIX_SYNC_PROVENANCE}$tag.$digest"
    }

    /**
     * MASTER-2026-08-22 P2：provenance 孤儿键每日清理。
     *
     * sync.prov.* 键按「行」写入且从不删除——行被物理删除（或历史导入重置）后
     * 其 provenance 键成为孤儿，server_config 表随同步总量无界增长。此处按
     * 「存活行（含 tombstone 行，§3.8 平台仲裁仍需）重建期望键集」，删除不在
     * 集合内的键。与并发 push 的竞态窗口内误删的键会在该行下次推送时重写，
     * 平台判定短暂回退 "server" 兜底后自愈。
     */
    @Scheduled(fixedDelayString = "\${anotherviewer.sync.provenance-prune-interval-ms:86400000}")
    fun pruneOrphanProvenance() {
        val expected = HashSet<String>()
        fun add(username: String?, tag: String, naturalKey: String) {
            // 未采纳的 legacy NULL 行从未写过 provenance 键；跳过。
            if (username.isNullOrEmpty()) return
            expected += provenanceConfigKey(username, tag, naturalKey)
        }
        favoriteRepository.findAll().forEach { add(it.username, TAG_FAVORITE, it.gid.toString()) }
        historyRepository.findAll().forEach { add(it.username, TAG_HISTORY, it.gid.toString()) }
        downloadRepository.findAll().forEach { add(it.username, TAG_DOWNLOAD, it.gid.toString()) }
        bookmarkRepository.findAll().forEach { add(it.username, TAG_BOOKMARK, it.gid.toString()) }
        filterRepository.findAll().forEach { add(it.username, TAG_FILTER, filterKey(it.type, it.text)) }
        quickSearchRepository.findAll().forEach { add(it.username, TAG_QUICK_SEARCH, it.name) }
        downloadLabelRepository.findAll().forEach { add(it.username, TAG_DOWNLOAD_LABEL, it.label) }

        var removed = 0
        serverConfig.findByKeyStartingWith(ServerConfigService.KEY_PREFIX_SYNC_PROVENANCE).forEach { cfg ->
            if (cfg.key !in expected) {
                serverConfig.delete(cfg)
                removed++
            }
        }
        if (removed > 0) logger.info("Pruned {} orphan sync provenance keys", removed)
    }

    private fun filterKey(mode: Int, text: String): String = "$mode|$text"

    companion object {
        const val TAG_FAVORITE = "fav"
        const val TAG_HISTORY = "hist"
        const val TAG_DOWNLOAD = "dl"
        const val TAG_BOOKMARK = "bm"
        const val TAG_FILTER = "flt"
        const val TAG_QUICK_SEARCH = "qs"
        const val TAG_DOWNLOAD_LABEL = "lbl"

        /** 无来源记录（v1 旧行/本地建行）的缺省 deviceId；platformOf("server") = web 侧（契约 §1.4）。 */
        const val PROVENANCE_FALLBACK_DEVICE = "server"

        /** MASTER-2026-08-22 P3：NULL 行收养完成标志（按 username 后缀）。 */
        const val KEY_SYNC_OWNERSHIP_ADOPTED = "sync.ownership.adopted"

        // 契约 §7 缺省常量
        const val CLIENT_TIER_DEFAULT = 1
        const val AUTO_SYNC_INTERVAL_SEC_DEFAULT = 900
        val CLIENT_TIER_RANGE = 0..3
    }

    // ---- Device tracking ----

    private fun updateDevice(deviceId: String, timestamp: Long, username: String) {
        val device = deviceRepository.findByDeviceId(deviceId)
        if (device != null) {
            // Never touch another user's device row.
            if (device.username != null && device.username != username) return
            device.username = username
            device.lastSeen = timestamp
            device.lastSyncTimestamp = timestamp
            deviceRepository.save(device)
        } else {
            val platform = deviceId.substringBefore("-", "other")
            deviceRepository.save(SyncDeviceEntity().apply {
                this.deviceId = deviceId
                this.platform = platform
                this.username = username
                this.lastSeen = timestamp
                this.lastSyncTimestamp = timestamp
            })
        }
    }

    // ---- DTO → Entity mapping ----

    private fun SyncFavoriteDto.toFavoriteEntity(username: String) = LocalFavoriteInfoEntity().apply {
        applyFavoriteFields(this, this@toFavoriteEntity)
        this.username = username
    }

    private fun applyFavoriteFields(entity: LocalFavoriteInfoEntity, dto: SyncFavoriteDto) {
        entity.gid = dto.gid
        entity.token = dto.token ?: ""
        entity.title = dto.title
        entity.titleJpn = dto.titleJpn
        entity.thumb = dto.thumb
        entity.category = dto.category
        entity.posted = dto.posted
        entity.uploader = dto.uploader
        entity.rating = dto.rating
        entity.rated = dto.rated
        entity.simpleLanguage = dto.simpleLanguage
        entity.simpleTags = dto.simpleTags
        entity.thumbWidth = dto.thumbWidth
        entity.thumbHeight = dto.thumbHeight
        entity.spanSize = dto.spanSize
        entity.spanIndex = dto.spanIndex
        entity.spanGroupIndex = dto.spanGroupIndex
        entity.favoriteSlot = dto.favoriteSlot
        entity.favoriteName = dto.favoriteName
        entity.pages = dto.pages
        entity.time = dto.time
        entity.lastModified = dto.lastModified
        entity.deleted = dto.deleted
    }

    private fun SyncHistoryDto.toHistoryEntity(username: String) = HistoryInfoEntity().apply {
        applyHistoryFields(this, this@toHistoryEntity)
        this.username = username
    }

    private fun applyHistoryFields(entity: HistoryInfoEntity, dto: SyncHistoryDto) {
        entity.gid = dto.gid
        entity.token = dto.token ?: ""
        entity.title = dto.title
        entity.titleJpn = dto.titleJpn
        entity.thumb = dto.thumb
        entity.category = dto.category
        entity.posted = dto.posted
        entity.uploader = dto.uploader
        entity.rating = dto.rating
        entity.rated = dto.rated
        entity.simpleLanguage = dto.simpleLanguage
        entity.simpleTags = dto.simpleTags
        entity.thumbWidth = dto.thumbWidth
        entity.thumbHeight = dto.thumbHeight
        entity.spanSize = dto.spanSize
        entity.spanIndex = dto.spanIndex
        entity.spanGroupIndex = dto.spanGroupIndex
        entity.favoriteSlot = dto.favoriteSlot
        entity.favoriteName = dto.favoriteName
        entity.pages = dto.pages
        entity.mode = dto.mode
        entity.time = dto.time
        entity.lastModified = dto.lastModified
        entity.deleted = dto.deleted
    }

    private fun SyncDownloadDto.toDownloadEntity(username: String) = DownloadInfoEntity().apply {
        applyDownloadFields(this, this@toDownloadEntity, username)
        this.username = username
    }

    private fun applyDownloadFields(entity: DownloadInfoEntity, dto: SyncDownloadDto, username: String) {
        entity.gid = dto.gid
        entity.token = dto.token ?: ""
        entity.title = dto.title
        entity.titleJpn = dto.titleJpn
        entity.thumb = dto.thumb
        entity.category = dto.category
        entity.posted = dto.posted
        entity.uploader = dto.uploader
        entity.rating = dto.rating
        entity.rated = dto.rated
        entity.simpleLanguage = dto.simpleLanguage
        entity.simpleTags = dto.simpleTags
        entity.thumbWidth = dto.thumbWidth
        entity.thumbHeight = dto.thumbHeight
        entity.spanSize = dto.spanSize
        entity.spanIndex = dto.spanIndex
        entity.spanGroupIndex = dto.spanGroupIndex
        entity.favoriteSlot = dto.favoriteSlot
        entity.favoriteName = dto.favoriteName
        entity.pages = dto.pages
        entity.state = dto.state
        entity.legacy = dto.legacy
        entity.total = dto.total
        entity.done = dto.finished
        entity.time = dto.time
        entity.lastModified = dto.lastModified
        entity.deleted = dto.deleted
        entity.label = resolveLabelId(dto.label, dto.lastModified, username)
    }

    /**
     * M-14: wire 上的 label 是标签名字符串，落库是 download_label.id（与 DownloadService 的
     * 约定一致，`DownloadAddRequest.label` 也是 id）。映射不到时按 DownloadService.createLabel
     * 的语义新建。MASTER-2026-08-22 P3：创建即落 username（此前留 NULL 依赖
     * adoptNullOwnership 认领；短路优化后不再有后续认养扫描，必须当场落属主）。
     */
    private fun resolveLabelId(labelName: String?, lastModified: Long, username: String): Int {
        if (labelName.isNullOrEmpty()) return 0
        downloadLabelRepository.findByLabel(labelName)?.let { return it.id.toInt() }
        val created = downloadLabelRepository.save(DownloadLabelEntity().apply {
            label = labelName
            time = System.currentTimeMillis()
            this.lastModified = lastModified
            this.username = username
        })
        return created.id.toInt()
    }

    private fun SyncBookmarkDto.toBookmarkEntity(username: String) = BookmarkInfoEntity().apply {
        applyBookmarkFields(this, this@toBookmarkEntity)
        this.username = username
    }

    private fun applyBookmarkFields(entity: BookmarkInfoEntity, dto: SyncBookmarkDto) {
        entity.gid = dto.gid
        entity.token = dto.token ?: ""
        entity.title = dto.title
        entity.titleJpn = dto.titleJpn
        entity.thumb = dto.thumb
        entity.category = dto.category
        entity.posted = dto.posted
        entity.uploader = dto.uploader
        entity.rating = dto.rating
        entity.rated = dto.rated
        entity.simpleLanguage = dto.simpleLanguage
        entity.simpleTags = dto.simpleTags
        entity.thumbWidth = dto.thumbWidth
        entity.thumbHeight = dto.thumbHeight
        entity.spanSize = dto.spanSize
        entity.spanIndex = dto.spanIndex
        entity.spanGroupIndex = dto.spanGroupIndex
        entity.favoriteSlot = dto.favoriteSlot
        entity.favoriteName = dto.favoriteName
        entity.pages = dto.pages
        entity.time = dto.time
        entity.note = dto.page.toString()
        entity.lastModified = dto.lastModified
        entity.deleted = dto.deleted
    }

    private fun SyncFilterDto.toFilterEntity(username: String) = FilterEntity().apply {
        applyFilterFields(this, this@toFilterEntity)
        this.username = username
    }

    private fun applyFilterFields(entity: FilterEntity, dto: SyncFilterDto) {
        entity.type = dto.mode
        entity.text = dto.text
        entity.enabled = dto.enabled
        entity.lastModified = dto.lastModified
        entity.deleted = dto.deleted
    }

    private fun SyncQuickSearchDto.toQuickSearchEntity(username: String) = QuickSearchEntity().apply {
        applyQuickSearchFields(this, this@toQuickSearchEntity)
        this.username = username
    }

    private fun applyQuickSearchFields(entity: QuickSearchEntity, dto: SyncQuickSearchDto) {
        entity.name = dto.name
        entity.mode = dto.mode
        entity.category = dto.category
        entity.keyword = dto.keyword
        entity.advanceSearch = dto.advanceSearch
        entity.minRating = dto.minRating
        entity.pageFrom = dto.pageFrom
        entity.pageTo = dto.pageTo
        entity.time = dto.time
        entity.lastModified = dto.lastModified
        entity.deleted = dto.deleted
    }

    private fun SyncDownloadLabelDto.toDownloadLabelEntity(username: String) = DownloadLabelEntity().apply {
        applyDownloadLabelFields(this, this@toDownloadLabelEntity)
        this.username = username
    }

    private fun applyDownloadLabelFields(entity: DownloadLabelEntity, dto: SyncDownloadLabelDto) {
        entity.label = dto.label
        entity.time = dto.time
        entity.lastModified = dto.lastModified
        entity.deleted = dto.deleted
    }

    // ---- Entity → DTO mapping ----
    //
    // deviceId 回显行级来源（last-writer，provenanceOf）；无来源记录的旧行为
    // PROVENANCE_FALLBACK_DEVICE（"server"），与 v1 输出一致（客户端按 §1.4 归 web 侧）。

    private fun LocalFavoriteInfoEntity.toSyncFavoriteDto(writerDeviceId: String) = SyncFavoriteDto(
        gid = gid, token = token, title = title, titleJpn = titleJpn,
        thumb = thumb, category = category, posted = posted, uploader = uploader,
        rating = rating, rated = rated, simpleLanguage = simpleLanguage,
        simpleTags = simpleTags, thumbWidth = thumbWidth, thumbHeight = thumbHeight,
        spanSize = spanSize, spanIndex = spanIndex, spanGroupIndex = spanGroupIndex,
        favoriteSlot = favoriteSlot, favoriteName = favoriteName, pages = pages,
        time = time, lastModified = lastModified, deviceId = writerDeviceId,
        deleted = deleted,
    )

    private fun HistoryInfoEntity.toSyncHistoryDto(writerDeviceId: String) = SyncHistoryDto(
        gid = gid, token = token, title = title, titleJpn = titleJpn,
        thumb = thumb, category = category, posted = posted, uploader = uploader,
        rating = rating, rated = rated, simpleLanguage = simpleLanguage,
        simpleTags = simpleTags, thumbWidth = thumbWidth, thumbHeight = thumbHeight,
        spanSize = spanSize, spanIndex = spanIndex, spanGroupIndex = spanGroupIndex,
        favoriteSlot = favoriteSlot, favoriteName = favoriteName, pages = pages,
        mode = mode, time = time, lastModified = lastModified, deviceId = writerDeviceId,
        deleted = deleted,
    )

    private fun DownloadInfoEntity.toSyncDownloadDto(labelNames: Map<Int, String>, writerDeviceId: String) = SyncDownloadDto(
        gid = gid, token = token, title = title, titleJpn = titleJpn,
        thumb = thumb, category = category, posted = posted, uploader = uploader,
        rating = rating, rated = rated, simpleLanguage = simpleLanguage,
        simpleTags = simpleTags, thumbWidth = thumbWidth, thumbHeight = thumbHeight,
        spanSize = spanSize, spanIndex = spanIndex, spanGroupIndex = spanGroupIndex,
        favoriteSlot = favoriteSlot, favoriteName = favoriteName, pages = pages,
        state = state, legacy = legacy, total = total, finished = done,
        label = labelNames[label], time = time, lastModified = lastModified, deviceId = writerDeviceId,
        deleted = deleted,
    )

    private fun BookmarkInfoEntity.toSyncBookmarkDto(writerDeviceId: String) = SyncBookmarkDto(
        gid = gid, token = token, title = title, titleJpn = titleJpn,
        thumb = thumb, category = category, posted = posted, uploader = uploader,
        rating = rating, rated = rated, simpleLanguage = simpleLanguage,
        simpleTags = simpleTags, thumbWidth = thumbWidth, thumbHeight = thumbHeight,
        spanSize = spanSize, spanIndex = spanIndex, spanGroupIndex = spanGroupIndex,
        favoriteSlot = favoriteSlot, favoriteName = favoriteName, pages = pages,
        page = note?.toIntOrNull() ?: 0, time = time, lastModified = lastModified, deviceId = writerDeviceId,
        deleted = deleted,
    )

    private fun FilterEntity.toSyncFilterDto(writerDeviceId: String) = SyncFilterDto(
        mode = type, text = text, enabled = enabled,
        lastModified = lastModified, deviceId = writerDeviceId,
        deleted = deleted,
    )

    private fun QuickSearchEntity.toSyncQuickSearchDto(writerDeviceId: String) = SyncQuickSearchDto(
        name = name, mode = mode, category = category, keyword = keyword,
        advanceSearch = advanceSearch, minRating = minRating,
        pageFrom = pageFrom, pageTo = pageTo,
        time = time, lastModified = lastModified, deviceId = writerDeviceId,
        deleted = deleted,
    )

    private fun DownloadLabelEntity.toSyncDownloadLabelDto(writerDeviceId: String) = SyncDownloadLabelDto(
        label = label, time = time, lastModified = lastModified,
        deviceId = writerDeviceId, deleted = deleted,
    )
}
