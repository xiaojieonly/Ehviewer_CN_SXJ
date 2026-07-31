package com.hippo.ehviewer.web.dto

// --- Sync entity DTOs (per sync-schemas.json) ---

data class SyncFavoriteDto(
    val gid: Long,
    val token: String,
    val title: String? = null,
    val titleJpn: String? = null,
    val thumb: String? = null,
    val category: Int = 0,
    val posted: String? = null,
    val uploader: String? = null,
    val rating: Float = 0f,
    val rated: Boolean = false,
    val simpleLanguage: String? = null,
    val simpleTags: String? = null,
    val thumbWidth: Int = 0,
    val thumbHeight: Int = 0,
    val spanSize: Int = 0,
    val spanIndex: Int = 0,
    val spanGroupIndex: Int = 0,
    val favoriteSlot: Int = -2,
    val favoriteName: String? = null,
    val pages: Int = 0,
    val time: Long = 0,
    val lastModified: Long = 0,
    val deviceId: String = "",
    val deleted: Boolean = false,
)

data class SyncHistoryDto(
    val gid: Long,
    val token: String,
    val title: String? = null,
    val titleJpn: String? = null,
    val thumb: String? = null,
    val category: Int = 0,
    val posted: String? = null,
    val uploader: String? = null,
    val rating: Float = 0f,
    val rated: Boolean = false,
    val simpleLanguage: String? = null,
    val simpleTags: String? = null,
    val thumbWidth: Int = 0,
    val thumbHeight: Int = 0,
    val spanSize: Int = 0,
    val spanIndex: Int = 0,
    val spanGroupIndex: Int = 0,
    val favoriteSlot: Int = -2,
    val favoriteName: String? = null,
    val pages: Int = 0,
    val mode: Int = 0,
    val time: Long = 0,
    val lastModified: Long = 0,
    val deviceId: String = "",
    val deleted: Boolean = false,
)

data class SyncDownloadDto(
    val gid: Long,
    val token: String,
    val title: String? = null,
    val titleJpn: String? = null,
    val thumb: String? = null,
    val category: Int = 0,
    val posted: String? = null,
    val uploader: String? = null,
    val rating: Float = 0f,
    val rated: Boolean = false,
    val simpleLanguage: String? = null,
    val simpleTags: String? = null,
    val thumbWidth: Int = 0,
    val thumbHeight: Int = 0,
    val spanSize: Int = 0,
    val spanIndex: Int = 0,
    val spanGroupIndex: Int = 0,
    val favoriteSlot: Int = -2,
    val favoriteName: String? = null,
    val pages: Int = 0,
    val state: Int = 0,
    val legacy: Int = 0,
    val time: Long = 0,
    val label: String? = null,
    val total: Int = 0,
    val finished: Int = 0,
    val downloaded: Int = 0,
    val lastModified: Long = 0,
    val deviceId: String = "",
    val deleted: Boolean = false,
)

data class SyncBookmarkDto(
    val gid: Long,
    val token: String,
    val title: String? = null,
    val titleJpn: String? = null,
    val thumb: String? = null,
    val category: Int = 0,
    val posted: String? = null,
    val uploader: String? = null,
    val rating: Float = 0f,
    val rated: Boolean = false,
    val simpleLanguage: String? = null,
    val simpleTags: String? = null,
    val thumbWidth: Int = 0,
    val thumbHeight: Int = 0,
    val spanSize: Int = 0,
    val spanIndex: Int = 0,
    val spanGroupIndex: Int = 0,
    val favoriteSlot: Int = -2,
    val favoriteName: String? = null,
    val pages: Int = 0,
    val page: Int = 0,
    val time: Long = 0,
    val lastModified: Long = 0,
    val deviceId: String = "",
    val deleted: Boolean = false,
)

data class SyncFilterDto(
    val mode: Int,
    val text: String,
    val enabled: Boolean = true,
    val lastModified: Long = 0,
    val deviceId: String = "",
    val deleted: Boolean = false,
)

data class SyncQuickSearchDto(
    val name: String,
    val mode: Int = 0,
    val category: Int = 0,
    val keyword: String? = null,
    val advanceSearch: Int = 0,
    val minRating: Int = 0,
    val pageFrom: Int = 0,
    val pageTo: Int = 0,
    val time: Long = 0,
    val lastModified: Long = 0,
    val deviceId: String = "",
    val deleted: Boolean = false,
)

// --- Wrapper messages ---

data class SyncPreferencesDto(
    val preferences: String = "{}",      // 完整偏好 JSON 字符串
    val lastModified: Long = 0,
    val deviceId: String = "",
)

data class SyncEntityCollection(
    val favorites: List<SyncFavoriteDto> = emptyList(),
    val history: List<SyncHistoryDto> = emptyList(),
    val downloads: List<SyncDownloadDto> = emptyList(),
    val bookmarks: List<SyncBookmarkDto> = emptyList(),
    val filters: List<SyncFilterDto> = emptyList(),
    val quickSearches: List<SyncQuickSearchDto> = emptyList(),
    val preferences: SyncPreferencesDto? = null,   // 新增
)

data class SyncPushRequest(
    val entities: SyncEntityCollection,
    val deviceId: String,
    val timestamp: Long,
)

data class SyncPushResponse(
    val success: Boolean,
    val serverTimestamp: Long,
    val conflicts: Int = 0,
)

data class SyncPullResponse(
    val entities: SyncEntityCollection,
    val serverTimestamp: Long,
)

data class ConnectedDeviceDto(
    val deviceId: String,
    val deviceName: String? = null,
    val platform: String,
    val lastSeen: Long,
)

data class EntityCountsDto(
    val favorites: Long = 0,
    val history: Long = 0,
    val downloads: Long = 0,
    val bookmarks: Long = 0,
    val filters: Long = 0,
    val quickSearches: Long = 0,
)

data class SyncStatusResponse(
    val lastSyncTimestamp: Long,
    val connectedDevices: List<ConnectedDeviceDto>,
    val entityCounts: EntityCountsDto,
)
