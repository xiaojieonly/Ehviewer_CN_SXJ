package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.dto.*
import com.hippo.anotherviewer.web.entity.*
import com.hippo.anotherviewer.web.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val SKEW_TOLERANCE = 5000L

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
) {

    @Transactional
    fun push(request: SyncPushRequest, username: String): SyncPushResponse {
        adoptNullOwnership(username)
        var conflicts = 0
        val e = request.entities

        conflicts += e.favorites.sumOf { if (mergeFavorite(it, username)) 1 else 0 }
        conflicts += e.history.sumOf { if (mergeHistory(it, username)) 1 else 0 }
        conflicts += e.downloads.sumOf { if (mergeDownload(it, username)) 1 else 0 }
        conflicts += e.bookmarks.sumOf { if (mergeBookmark(it, username)) 1 else 0 }
        conflicts += e.filters.sumOf { if (mergeFilter(it, username)) 1 else 0 }
        conflicts += e.quickSearches.sumOf { if (mergeQuickSearch(it, username)) 1 else 0 }
        conflicts += e.downloadLabels.sumOf { if (mergeDownloadLabel(it, username)) 1 else 0 }

        e.preferences?.let { pref ->
            // last-write-wins: 仅当推送方 lastModified 明显新于存量 updatedAt 时覆盖（含时钟偏差容忍）
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
        val entities = SyncEntityCollection(
            favorites = favorites.map { it.toSyncFavoriteDto() },
            history = history.map { it.toSyncHistoryDto() },
            downloads = downloads.map { it.toSyncDownloadDto(labelNames) },
            bookmarks = bookmarks.map { it.toSyncBookmarkDto() },
            filters = filters.map { it.toSyncFilterDto() },
            quickSearches = quickSearches.map { it.toSyncQuickSearchDto() },
            downloadLabels = downloadLabels.map { it.toSyncDownloadLabelDto() },
            preferences = SyncPreferencesDto(
                preferences = preferenceService.getRaw(username),
                lastModified = prefEntity?.updatedAt ?: 0,
                deviceId = prefEntity?.updatedBy ?: "",
            ),
        )
        return SyncPullResponse(entities = entities, serverTimestamp = now)
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
    }

    /** True when the row is unowned or owned by [username] (never another user's). */
    private fun <T> ownedBy(existing: T?, username: String, owner: (T) -> String?): T? {
        if (existing == null) return null
        val ownerName = owner(existing)
        return if (ownerName == null || ownerName == username) existing else null
    }

    // ---- Merge strategies (per contracts/sync-conflict-rules.md) ----

    /** Favorites: union merge with soft-delete tombstones. */
    private fun mergeFavorite(incoming: SyncFavoriteDto, username: String): Boolean {
        val raw = favoriteRepository.findByGid(incoming.gid)
        val existing = ownedBy(raw, username) { it.username }
        if (existing == null) {
            // No own record: store the incoming row, tombstones included, so
            // deletions propagate to other devices (contract §4.1).
            if (raw == null) {
                favoriteRepository.save(incoming.toFavoriteEntity(username))
            }
            return false
        }
        // Both deleted or one side deleted:
        // incoming tombstone + existing alive -> keep alive (union).
        if (incoming.deleted && !existing.deleted) return false
        // existing tombstone + incoming alive -> resurrect.
        if (existing.deleted && !incoming.deleted) {
            applyFavoriteFields(existing, incoming)
            favoriteRepository.save(existing)
            return true
        }
        // Both alive or both deleted: last-write-wins on metadata.
        if (incoming.lastModified > existing.lastModified + SKEW_TOLERANCE) {
            applyFavoriteFields(existing, incoming)
            favoriteRepository.save(existing)
            return true
        }
        return false
    }

    /** History: last-write-wins；删除保留墓碑行（deleted=true + lastModified bump），增量 pull 才能传播删除。 */
    private fun mergeHistory(incoming: SyncHistoryDto, username: String): Boolean {
        val raw = historyRepository.findByGid(incoming.gid)
        val existing = ownedBy(raw, username) { it.username }
        if (incoming.deleted) {
            // 软删: 不真删行，bump lastModified 使 since>0 的增量 pull 能取到墓碑
            if (existing != null) {
                existing.deleted = true
                existing.lastModified = maxOf(existing.lastModified, incoming.lastModified)
                historyRepository.save(existing)
            } else if (raw == null) {
                // 未知 gid 也存墓碑，删除同样能传播到其他设备
                historyRepository.save(incoming.toHistoryEntity(username))
            }
            return false
        }
        if (existing == null) {
            if (raw == null) historyRepository.save(incoming.toHistoryEntity(username))
            return false
        }
        if (incoming.lastModified > existing.lastModified + SKEW_TOLERANCE) {
            applyHistoryFields(existing, incoming)
            historyRepository.save(existing)
            return true
        }
        if (existing.lastModified > incoming.lastModified + SKEW_TOLERANCE) return false
        // Within skew: prefer the later view time.
        if (incoming.time > existing.time) {
            applyHistoryFields(existing, incoming)
            historyRepository.save(existing)
            return true
        }
        return false
    }

    /** Downloads: union merge + status sync (soft delete, tombstone stored). */
    private fun mergeDownload(incoming: SyncDownloadDto, username: String): Boolean {
        val raw = downloadRepository.findByGid(incoming.gid)
        val existing = ownedBy(raw, username) { it.username }
        if (existing == null) {
            if (raw == null) downloadRepository.save(incoming.toDownloadEntity(username))
            return false
        }
        if (incoming.deleted && !existing.deleted) return false
        if (existing.deleted && !incoming.deleted) {
            applyDownloadFields(existing, incoming)
            downloadRepository.save(existing)
            return true
        }
        // Mutable state fields: last-write-wins.
        if (incoming.lastModified > existing.lastModified + SKEW_TOLERANCE) {
            applyDownloadFields(existing, incoming)
            downloadRepository.save(existing)
            return true
        }
        return false
    }

    /** Bookmarks: last-write-wins；删除保留墓碑行（deleted=true + lastModified bump），增量 pull 才能传播删除。 */
    private fun mergeBookmark(incoming: SyncBookmarkDto, username: String): Boolean {
        val raw = bookmarkRepository.findByGid(incoming.gid)
        val existing = ownedBy(raw, username) { it.username }
        if (incoming.deleted) {
            // 软删: 不真删行，bump lastModified 使 since>0 的增量 pull 能取到墓碑
            if (existing != null) {
                existing.deleted = true
                existing.lastModified = maxOf(existing.lastModified, incoming.lastModified)
                bookmarkRepository.save(existing)
            } else if (raw == null) {
                // 未知 gid 也存墓碑，删除同样能传播到其他设备
                bookmarkRepository.save(incoming.toBookmarkEntity(username))
            }
            return false
        }
        if (existing == null) {
            if (raw == null) bookmarkRepository.save(incoming.toBookmarkEntity(username))
            return false
        }
        if (incoming.lastModified > existing.lastModified + SKEW_TOLERANCE) {
            applyBookmarkFields(existing, incoming)
            bookmarkRepository.save(existing)
            return true
        }
        if (existing.lastModified > incoming.lastModified + SKEW_TOLERANCE) return false
        // Within skew: prefer higher page (further progress).
        val existingPage = existing.note?.toIntOrNull() ?: 0
        if (incoming.page > existingPage) {
            applyBookmarkFields(existing, incoming)
            bookmarkRepository.save(existing)
            return true
        }
        return false
    }

    /** Filters: union merge keyed by (mode, text); local-wins outside skew, additive bias inside. */
    private fun mergeFilter(incoming: SyncFilterDto, username: String): Boolean {
        val raw = filterRepository.findAll().firstOrNull {
            it.type == incoming.mode && it.text == incoming.text
        }
        val existing = ownedBy(raw, username) { it.username }
        if (existing == null) {
            if (raw == null) filterRepository.save(incoming.toFilterEntity(username))
            return false
        }
        if (incoming.deleted && !existing.deleted) return false
        if (existing.deleted && !incoming.deleted) {
            applyFilterFields(existing, incoming)
            filterRepository.save(existing)
            return true
        }
        // Local-wins: the server keeps its own record when it is newer.
        if (existing.lastModified > incoming.lastModified + SKEW_TOLERANCE) return false
        if (incoming.lastModified > existing.lastModified + SKEW_TOLERANCE) {
            applyFilterFields(existing, incoming)
            filterRepository.save(existing)
            return true
        }
        // Within skew: additive bias — prefer enabled=true.
        if (incoming.enabled != existing.enabled && incoming.enabled) {
            applyFilterFields(existing, incoming)
            filterRepository.save(existing)
            return true
        }
        return false
    }

    /** QuickSearches: union merge keyed by name; local-wins outside skew. */
    private fun mergeQuickSearch(incoming: SyncQuickSearchDto, username: String): Boolean {
        val raw = quickSearchRepository.findByName(incoming.name)
        val existing = ownedBy(raw, username) { it.username }
        if (existing == null) {
            if (raw == null) quickSearchRepository.save(incoming.toQuickSearchEntity(username))
            return false
        }
        if (incoming.deleted && !existing.deleted) return false
        if (existing.deleted && !incoming.deleted) {
            applyQuickSearchFields(existing, incoming)
            quickSearchRepository.save(existing)
            return true
        }
        if (existing.lastModified > incoming.lastModified + SKEW_TOLERANCE) return false
        if (incoming.lastModified > existing.lastModified + SKEW_TOLERANCE) {
            applyQuickSearchFields(existing, incoming)
            quickSearchRepository.save(existing)
            return true
        }
        return false
    }

    /** DownloadLabels: union merge keyed by label name; local-wins outside skew. */
    private fun mergeDownloadLabel(incoming: SyncDownloadLabelDto, username: String): Boolean {
        val raw = downloadLabelRepository.findByLabel(incoming.label)
        val existing = ownedBy(raw, username) { it.username }
        if (existing == null) {
            if (raw == null) downloadLabelRepository.save(incoming.toDownloadLabelEntity(username))
            return false
        }
        if (incoming.deleted && !existing.deleted) return false
        if (existing.deleted && !incoming.deleted) {
            applyDownloadLabelFields(existing, incoming)
            downloadLabelRepository.save(existing)
            return true
        }
        if (existing.lastModified > incoming.lastModified + SKEW_TOLERANCE) return false
        if (incoming.lastModified > existing.lastModified + SKEW_TOLERANCE) {
            applyDownloadLabelFields(existing, incoming)
            downloadLabelRepository.save(existing)
            return true
        }
        return false
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
        entity.token = dto.token
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
        entity.token = dto.token
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
        applyDownloadFields(this, this@toDownloadEntity)
        this.username = username
    }

    private fun applyDownloadFields(entity: DownloadInfoEntity, dto: SyncDownloadDto) {
        entity.gid = dto.gid
        entity.token = dto.token
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
        entity.label = resolveLabelId(dto.label, dto.lastModified)
    }

    /**
     * M-14: wire 上的 label 是标签名字符串，落库是 download_label.id（与 DownloadService 的
     * 约定一致，`DownloadAddRequest.label` 也是 id）。映射不到时按 DownloadService.createLabel
     * 的约定补建标签行（全局标签、不设 username，等 adoptNullOwnership 认领），绝不写 0 丢标签。
     */
    private fun resolveLabelId(labelName: String?, lastModified: Long): Int {
        if (labelName.isNullOrEmpty()) return 0
        downloadLabelRepository.findByLabel(labelName)?.let { return it.id.toInt() }
        val created = downloadLabelRepository.save(DownloadLabelEntity().apply {
            label = labelName
            time = System.currentTimeMillis()
            this.lastModified = lastModified
        })
        return created.id.toInt()
    }

    private fun SyncBookmarkDto.toBookmarkEntity(username: String) = BookmarkInfoEntity().apply {
        applyBookmarkFields(this, this@toBookmarkEntity)
        this.username = username
    }

    private fun applyBookmarkFields(entity: BookmarkInfoEntity, dto: SyncBookmarkDto) {
        entity.gid = dto.gid
        entity.token = dto.token
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

    private fun LocalFavoriteInfoEntity.toSyncFavoriteDto() = SyncFavoriteDto(
        gid = gid, token = token, title = title, titleJpn = titleJpn,
        thumb = thumb, category = category, posted = posted, uploader = uploader,
        rating = rating, rated = rated, simpleLanguage = simpleLanguage,
        simpleTags = simpleTags, thumbWidth = thumbWidth, thumbHeight = thumbHeight,
        spanSize = spanSize, spanIndex = spanIndex, spanGroupIndex = spanGroupIndex,
        favoriteSlot = favoriteSlot, favoriteName = favoriteName, pages = pages,
        time = time, lastModified = lastModified, deviceId = "server",
        deleted = deleted,
    )

    private fun HistoryInfoEntity.toSyncHistoryDto() = SyncHistoryDto(
        gid = gid, token = token, title = title, titleJpn = titleJpn,
        thumb = thumb, category = category, posted = posted, uploader = uploader,
        rating = rating, rated = rated, simpleLanguage = simpleLanguage,
        simpleTags = simpleTags, thumbWidth = thumbWidth, thumbHeight = thumbHeight,
        spanSize = spanSize, spanIndex = spanIndex, spanGroupIndex = spanGroupIndex,
        favoriteSlot = favoriteSlot, favoriteName = favoriteName, pages = pages,
        mode = mode, time = time, lastModified = lastModified, deviceId = "server",
        deleted = deleted,
    )

    private fun DownloadInfoEntity.toSyncDownloadDto(labelNames: Map<Int, String>) = SyncDownloadDto(
        gid = gid, token = token, title = title, titleJpn = titleJpn,
        thumb = thumb, category = category, posted = posted, uploader = uploader,
        rating = rating, rated = rated, simpleLanguage = simpleLanguage,
        simpleTags = simpleTags, thumbWidth = thumbWidth, thumbHeight = thumbHeight,
        spanSize = spanSize, spanIndex = spanIndex, spanGroupIndex = spanGroupIndex,
        favoriteSlot = favoriteSlot, favoriteName = favoriteName, pages = pages,
        state = state, legacy = legacy, total = total, finished = done,
        label = labelNames[label], time = time, lastModified = lastModified, deviceId = "server",
        deleted = deleted,
    )

    private fun BookmarkInfoEntity.toSyncBookmarkDto() = SyncBookmarkDto(
        gid = gid, token = token, title = title, titleJpn = titleJpn,
        thumb = thumb, category = category, posted = posted, uploader = uploader,
        rating = rating, rated = rated, simpleLanguage = simpleLanguage,
        simpleTags = simpleTags, thumbWidth = thumbWidth, thumbHeight = thumbHeight,
        spanSize = spanSize, spanIndex = spanIndex, spanGroupIndex = spanGroupIndex,
        favoriteSlot = favoriteSlot, favoriteName = favoriteName, pages = pages,
        page = note?.toIntOrNull() ?: 0, time = time, lastModified = lastModified, deviceId = "server",
        deleted = deleted,
    )

    private fun FilterEntity.toSyncFilterDto() = SyncFilterDto(
        mode = type, text = text, enabled = enabled,
        lastModified = lastModified, deviceId = "server",
        deleted = deleted,
    )

    private fun QuickSearchEntity.toSyncQuickSearchDto() = SyncQuickSearchDto(
        name = name, mode = mode, category = category, keyword = keyword,
        advanceSearch = advanceSearch, minRating = minRating,
        pageFrom = pageFrom, pageTo = pageTo,
        time = time, lastModified = lastModified, deviceId = "server",
        deleted = deleted,
    )

    private fun DownloadLabelEntity.toSyncDownloadLabelDto() = SyncDownloadLabelDto(
        label = label, time = time, lastModified = lastModified,
        deviceId = "server", deleted = deleted,
    )
}
