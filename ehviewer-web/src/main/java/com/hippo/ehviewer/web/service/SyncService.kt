package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.entity.*
import com.hippo.ehviewer.web.repository.*
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
    private val deviceRepository: SyncDeviceRepository,
    private val preferenceRepository: UserPreferenceRepository,
    private val preferenceService: UserPreferenceService,
) {

    @Transactional
    fun push(request: SyncPushRequest, username: String): SyncPushResponse {
        var conflicts = 0
        val e = request.entities

        conflicts += e.favorites.sumOf { if (mergeFavorite(it)) 1 else 0 }
        conflicts += e.history.sumOf { if (mergeHistory(it)) 1 else 0 }
        conflicts += e.downloads.sumOf { if (mergeDownload(it)) 1 else 0 }
        conflicts += e.bookmarks.sumOf { if (mergeBookmark(it)) 1 else 0 }
        conflicts += e.filters.sumOf { if (mergeFilter(it)) 1 else 0 }
        conflicts += e.quickSearches.sumOf { if (mergeQuickSearch(it)) 1 else 0 }

        e.preferences?.let { pref ->
            // last-write-wins: 只有推送方更新时才覆盖
            preferenceService.replace(username, pref.preferences, pref.deviceId)
        }

        val now = System.currentTimeMillis()
        updateDevice(request.deviceId, now)

        return SyncPushResponse(success = true, serverTimestamp = now, conflicts = conflicts)
    }

    fun pull(since: Long, username: String, deviceId: String = ""): SyncPullResponse {
        val now = System.currentTimeMillis()
        if (deviceId.isNotEmpty()) updateDevice(deviceId, now)
        val prefEntity = preferenceRepository.findByUsername(username)
        val entities = SyncEntityCollection(
            favorites = favoriteRepository.findAll().filter { it.time > since }.map { it.toSyncFavoriteDto() },
            history = historyRepository.findAll().filter { it.time > since }.map { it.toSyncHistoryDto() },
            downloads = downloadRepository.findAll().filter { it.time > since }.map { it.toSyncDownloadDto() },
            bookmarks = bookmarkRepository.findAll().filter { it.time > since }.map { it.toSyncBookmarkDto() },
            filters = filterRepository.findAll().map { it.toSyncFilterDto() },
            quickSearches = quickSearchRepository.findAll().map { it.toSyncQuickSearchDto() },
            preferences = SyncPreferencesDto(
                preferences = preferenceService.getRaw(username),
                lastModified = prefEntity?.updatedAt ?: 0,
                deviceId = prefEntity?.updatedBy ?: "",
            ),
        )
        return SyncPullResponse(entities = entities, serverTimestamp = now)
    }

    fun status(): SyncStatusResponse {
        val devices = deviceRepository.findAll()
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
                favorites = favoriteRepository.count(),
                history = historyRepository.count(),
                downloads = downloadRepository.count(),
                bookmarks = bookmarkRepository.count(),
                filters = filterRepository.count(),
                quickSearches = quickSearchRepository.count(),
            ),
        )
    }

    // ---- Merge strategies ----

    /** Favorites: union merge. Never delete a remote entry. Returns true if a conflict was resolved. */
    private fun mergeFavorite(incoming: SyncFavoriteDto): Boolean {
        val existing = favoriteRepository.findByGid(incoming.gid)
        if (existing == null) {
            if (!incoming.deleted) {
                favoriteRepository.save(incoming.toFavoriteEntity())
            }
            return false
        }
        // Union: if incoming is deleted but existing is alive, keep existing
        if (incoming.deleted) return false
        // Both alive: last-write-wins on metadata
        if (incoming.lastModified > existing.time + SKEW_TOLERANCE) {
            applyFavoriteFields(existing, incoming)
            favoriteRepository.save(existing)
            return true
        }
        return false
    }

    /** History: last-write-wins. Hard delete supported. Returns true if a conflict was resolved. */
    private fun mergeHistory(incoming: SyncHistoryDto): Boolean {
        val existing = historyRepository.findByGid(incoming.gid)
        if (incoming.deleted) {
            if (existing != null) {
                historyRepository.delete(existing)
            }
            return false
        }
        if (existing == null) {
            historyRepository.save(incoming.toHistoryEntity())
            return false
        }
        // LWW: compare lastModified, tie-break on time (view time)
        if (incoming.lastModified > existing.time + SKEW_TOLERANCE) {
            applyHistoryFields(existing, incoming)
            historyRepository.save(existing)
            return true
        }
        if (existing.time >= incoming.lastModified - SKEW_TOLERANCE && incoming.time > existing.time) {
            applyHistoryFields(existing, incoming)
            historyRepository.save(existing)
            return true
        }
        return false
    }

    /** Downloads: union merge + status sync. Returns true if a conflict was resolved. */
    private fun mergeDownload(incoming: SyncDownloadDto): Boolean {
        val existing = downloadRepository.findByGid(incoming.gid)
        if (existing == null) {
            if (!incoming.deleted) {
                downloadRepository.save(incoming.toDownloadEntity())
            }
            return false
        }
        // Union: if incoming is deleted but existing is alive, keep existing
        if (incoming.deleted) return false
        // Mutable state fields: last-write-wins
        if (incoming.lastModified > existing.time + SKEW_TOLERANCE) {
            applyDownloadFields(existing, incoming)
            downloadRepository.save(existing)
            return true
        }
        return false
    }

    /** Bookmarks: last-write-wins. Hard delete supported. Returns true if a conflict was resolved. */
    private fun mergeBookmark(incoming: SyncBookmarkDto): Boolean {
        val existing = bookmarkRepository.findByGid(incoming.gid)
        if (incoming.deleted) {
            if (existing != null) {
                bookmarkRepository.delete(existing)
            }
            return false
        }
        if (existing == null) {
            bookmarkRepository.save(incoming.toBookmarkEntity())
            return false
        }
        // LWW: compare lastModified, tie-break on page (further progress)
        if (incoming.lastModified > existing.time + SKEW_TOLERANCE) {
            applyBookmarkFields(existing, incoming)
            bookmarkRepository.save(existing)
            return true
        }
        if (existing.time >= incoming.lastModified - SKEW_TOLERANCE) {
            val existingPage = existing.note?.toIntOrNull() ?: 0
            if (incoming.page > existingPage) {
                applyBookmarkFields(existing, incoming)
                bookmarkRepository.save(existing)
                return true
            }
        }
        return false
    }

    /** Filters: union merge keyed by (mode, text). Returns true if a conflict was resolved. */
    private fun mergeFilter(incoming: SyncFilterDto): Boolean {
        val existing = filterRepository.findAll().firstOrNull {
            it.type == incoming.mode && it.text == incoming.text
        }
        if (existing == null) {
            if (!incoming.deleted) {
                filterRepository.save(incoming.toFilterEntity())
            }
            return false
        }
        // Union: if incoming deleted but existing alive, keep existing
        if (incoming.deleted) return false
        // LWW on enabled flag; additive bias within skew
        if (incoming.lastModified > SKEW_TOLERANCE) {
            val changed = existing.enabled != incoming.enabled
            existing.enabled = incoming.enabled
            if (changed) {
                filterRepository.save(existing)
                return true
            }
        }
        return false
    }

    /** QuickSearches: union merge keyed by name. Returns true if a conflict was resolved. */
    private fun mergeQuickSearch(incoming: SyncQuickSearchDto): Boolean {
        val existing = quickSearchRepository.findByName(incoming.name)
        if (existing == null) {
            if (!incoming.deleted) {
                quickSearchRepository.save(incoming.toQuickSearchEntity())
            }
            return false
        }
        // Union: if incoming deleted but existing alive, keep existing
        if (incoming.deleted) return false
        // LWW on mutable fields
        if (incoming.lastModified > SKEW_TOLERANCE) {
            applyQuickSearchFields(existing, incoming)
            quickSearchRepository.save(existing)
            return true
        }
        return false
    }

    // ---- Device tracking ----

    private fun updateDevice(deviceId: String, timestamp: Long) {
        val device = deviceRepository.findByDeviceId(deviceId)
        if (device != null) {
            device.lastSeen = timestamp
            device.lastSyncTimestamp = timestamp
            deviceRepository.save(device)
        } else {
            val platform = deviceId.substringBefore("-", "other")
            deviceRepository.save(SyncDeviceEntity().apply {
                this.deviceId = deviceId
                this.platform = platform
                this.lastSeen = timestamp
                this.lastSyncTimestamp = timestamp
            })
        }
    }

    // ---- DTO → Entity mapping ----

    private fun SyncFavoriteDto.toFavoriteEntity() = LocalFavoriteInfoEntity().apply {
        applyFavoriteFields(this, this@toFavoriteEntity)
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
    }

    private fun SyncHistoryDto.toHistoryEntity() = HistoryInfoEntity().apply {
        applyHistoryFields(this, this@toHistoryEntity)
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
        entity.time = dto.time
    }

    private fun SyncDownloadDto.toDownloadEntity() = DownloadInfoEntity().apply {
        applyDownloadFields(this, this@toDownloadEntity)
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
    }

    private fun SyncBookmarkDto.toBookmarkEntity() = BookmarkInfoEntity().apply {
        applyBookmarkFields(this, this@toBookmarkEntity)
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
    }

    private fun SyncFilterDto.toFilterEntity() = FilterEntity().apply {
        type = this@toFilterEntity.mode
        text = this@toFilterEntity.text
        enabled = this@toFilterEntity.enabled
    }

    private fun SyncQuickSearchDto.toQuickSearchEntity() = QuickSearchEntity().apply {
        applyQuickSearchFields(this, this@toQuickSearchEntity)
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
    }

    // ---- Entity → DTO mapping ----

    private fun LocalFavoriteInfoEntity.toSyncFavoriteDto() = SyncFavoriteDto(
        gid = gid, token = token, title = title, titleJpn = titleJpn,
        thumb = thumb, category = category, posted = posted, uploader = uploader,
        rating = rating, rated = rated, simpleLanguage = simpleLanguage,
        simpleTags = simpleTags, thumbWidth = thumbWidth, thumbHeight = thumbHeight,
        spanSize = spanSize, spanIndex = spanIndex, spanGroupIndex = spanGroupIndex,
        favoriteSlot = favoriteSlot, favoriteName = favoriteName, pages = pages,
        time = time, lastModified = time, deviceId = "server",
    )

    private fun HistoryInfoEntity.toSyncHistoryDto() = SyncHistoryDto(
        gid = gid, token = token, title = title, titleJpn = titleJpn,
        thumb = thumb, category = category, posted = posted, uploader = uploader,
        rating = rating, rated = rated, simpleLanguage = simpleLanguage,
        simpleTags = simpleTags, thumbWidth = thumbWidth, thumbHeight = thumbHeight,
        spanSize = spanSize, spanIndex = spanIndex, spanGroupIndex = spanGroupIndex,
        favoriteSlot = favoriteSlot, favoriteName = favoriteName, pages = pages,
        time = time, lastModified = time, deviceId = "server",
    )

    private fun DownloadInfoEntity.toSyncDownloadDto() = SyncDownloadDto(
        gid = gid, token = token, title = title, titleJpn = titleJpn,
        thumb = thumb, category = category, posted = posted, uploader = uploader,
        rating = rating, rated = rated, simpleLanguage = simpleLanguage,
        simpleTags = simpleTags, thumbWidth = thumbWidth, thumbHeight = thumbHeight,
        spanSize = spanSize, spanIndex = spanIndex, spanGroupIndex = spanGroupIndex,
        favoriteSlot = favoriteSlot, favoriteName = favoriteName, pages = pages,
        state = state, legacy = legacy, total = total, finished = done,
        time = time, lastModified = time, deviceId = "server",
    )

    private fun BookmarkInfoEntity.toSyncBookmarkDto() = SyncBookmarkDto(
        gid = gid, token = token, title = title, titleJpn = titleJpn,
        thumb = thumb, category = category, posted = posted, uploader = uploader,
        rating = rating, rated = rated, simpleLanguage = simpleLanguage,
        simpleTags = simpleTags, thumbWidth = thumbWidth, thumbHeight = thumbHeight,
        spanSize = spanSize, spanIndex = spanIndex, spanGroupIndex = spanGroupIndex,
        favoriteSlot = favoriteSlot, favoriteName = favoriteName, pages = pages,
        page = note?.toIntOrNull() ?: 0, time = time, lastModified = time, deviceId = "server",
    )

    private fun FilterEntity.toSyncFilterDto() = SyncFilterDto(
        mode = type, text = text, enabled = enabled,
        lastModified = 0, deviceId = "server",
    )

    private fun QuickSearchEntity.toSyncQuickSearchDto() = SyncQuickSearchDto(
        name = name, mode = mode, category = category, keyword = keyword,
        advanceSearch = advanceSearch, minRating = minRating,
        pageFrom = pageFrom, pageTo = pageTo,
        lastModified = 0, deviceId = "server",
    )
}
