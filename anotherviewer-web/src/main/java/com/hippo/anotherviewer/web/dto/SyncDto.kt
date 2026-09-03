package com.hippo.anotherviewer.web.dto

import com.fasterxml.jackson.annotation.JsonProperty

// --- SyncPolicy (contract v2 §8, ADR-0003 D1/D3/D4) ---

/**
 * Conflict arbitration strategy (contracts/sync-conflict-rules.md v2 §1.4).
 * Wire values are frozen by sync-schemas.json#syncPolicy / openapi.yaml.
 */
enum class ConflictStrategy(val wire: String) {
    /** A: 同键跨平台冲突无条件 android 胜（默认）。 */
    @JsonProperty("device_priority")
    DEVICE_PRIORITY("device_priority"),

    /** B: v1.0 完整语义（LWW ±5000ms skew + 实体 tie-break），回退兜底。 */
    @JsonProperty("lww")
    LWW("lww"),

    /** C: 同键跨平台冲突无条件 web 胜。 */
    @JsonProperty("web_priority")
    WEB_PRIORITY("web_priority"),
    ;

    companion object {
        fun fromWire(value: String?): ConflictStrategy? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * Server-held sync policy (sync-schemas.json#/$defs/syncPolicy).
 * clientTier: 0=独立 / 1=同步+流式（默认） / 2=浏览代理 / 3=下载托管（押后）。
 * autoSyncIntervalSec: 网络感知自动同步间隔；0=仅网络变化触发。
 */
data class SyncPolicyDto(
    val conflictStrategy: ConflictStrategy = ConflictStrategy.DEVICE_PRIORITY,
    val clientTier: Int = 1,
    val autoSyncIntervalSec: Int = 900,
)

// --- Sync entity DTOs (per sync-schemas.json) ---

data class SyncFavoriteDto(
    val gid: Long,
    val token: String? = null,
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
    val token: String? = null,
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
    /** 阅读进度（0 起页索引）；旧 App push 不带该字段（默认 0），行胜合并取 max 防回退。 */
    val page: Int = 0,
    val time: Long = 0,
    val lastModified: Long = 0,
    val deviceId: String = "",
    val deleted: Boolean = false,
)

data class SyncDownloadDto(
    val gid: Long,
    val token: String? = null,
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
    val token: String? = null,
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

data class SyncDownloadLabelDto(
    val label: String,
    val time: Long = 0,
    val lastModified: Long = 0,
    val deviceId: String = "",
    val deleted: Boolean = false,
)

// --- ehSession（ADR-0004）：单例 EH 登录会话 + 用户设置，LWW、策略独立 ---

/** 单条 EH 会话 cookie，字段镜像 okhttp3.Cookie / SiteCookieStore。value 服务端落库 enc:v1: 加密。 */
data class SyncEhSessionCookieDto(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val expiresAt: Long,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val persistent: Boolean = false,
    val hostOnly: Boolean = false,
)

/** 单例实体（每用户至多一条活记录）；LWW 合并，不参与 conflictStrategy。 */
data class SyncEhSessionDto(
    val cookies: List<SyncEhSessionCookieDto>? = null,
    val displayName: String? = null,
    val avatar: String? = null,
    val gallerySite: Int? = null,
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
    val downloadLabels: List<SyncDownloadLabelDto> = emptyList(),
    val ehSession: List<SyncEhSessionDto> = emptyList(),   // 单例；0 或 1 条（ADR-0004）
    val preferences: SyncPreferencesDto? = null,   // 新增
)

data class SyncPushRequest(
    val entities: SyncEntityCollection,
    val deviceId: String,
    val timestamp: Long,
    /**
     * Optional (v2). android 平台 push 携带时为权威覆盖（ADR-0003 D2，等价 PUT /sync/policy）；
     * web 端 push 携带时服务端忽略该字段（契约 §8）。旧客户端不带此字段 → 不受影响。
     */
    val policy: SyncPolicyDto? = null,
)

data class SyncPushResponse(
    val success: Boolean,
    val serverTimestamp: Long,
    val conflicts: Int = 0,
)

data class SyncPullResponse(
    val entities: SyncEntityCollection,
    val serverTimestamp: Long,
    /**
     * Optional (v2). 当前服务器同步策略；客户端按 policy.conflictStrategy 执行本地 merge
     * （契约 §6.2）。旧服务器无此字段 → 客户端回退 lww 不报错；旧客户端忽略未知字段。
     */
    val policy: SyncPolicyDto? = null,
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
    val downloadLabels: Long = 0,
    val ehSession: Long = 0,   // 单例：0/1（ADR-0004）
)

data class SyncStatusResponse(
    val lastSyncTimestamp: Long,
    val connectedDevices: List<ConnectedDeviceDto>,
    val entityCounts: EntityCountsDto,
)

data class DeviceInfoDto(
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val pairedAt: Long,
    val lastSeen: Long,
)
