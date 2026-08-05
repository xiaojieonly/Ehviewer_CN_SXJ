package com.hippo.anotherviewer.web.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

/** GET /api/v1/preferences 响应 */
data class PreferenceResponse(
    val general: GeneralPreferences = GeneralPreferences(),
    val reader: ReaderPreferences = ReaderPreferences(),
    val privacy: PrivacyPreferences = PrivacyPreferences(),
)

data class GeneralPreferences(
    @field:Size(max = 64, message = "theme must be at most 64 characters")
    val theme: String = "light",
    val themeAutoSwitch: Boolean = false,
    @field:Size(max = 64, message = "launchPage must be at most 64 characters")
    val launchPage: String = "homepage",
    // grid|list —— Wave-1 B-1: 共享 GalleryList 消费的默认布局（默认 grid）
    @field:Size(max = 64, message = "listMode must be at most 64 characters")
    val listMode: String = "grid",
    val showReadProgress: Boolean = true,
    @field:Size(max = 64, message = "detailSize must be at most 64 characters")
    val detailSize: String = "long",
    @field:Size(max = 64, message = "thumbSize must be at most 64 characters")
    val thumbSize: String = "middle",
    val historyInfoSize: Int = 100,
    val showJpnTitle: Boolean = false,
    val showGalleryPages: Boolean = false,
    val showTagTranslations: Boolean = true,
    val showGalleryComment: Boolean = true,
    val showGalleryRating: Boolean = true,
    val showEhEvents: Boolean = true,
    val showEhLimits: Boolean = true,
    // ---- Wave-1 B 组（1b 浏览一致性） ----
    val showUploader: Boolean = false,
    val showPostedTime: Boolean = false,
    // clamp -2..9（WebUI 输入侧做 clamp，此处为服务端守卫）
    @field:Min(-2, message = "defaultFavoriteSlot must be between -2 and 9")
    @field:Max(9, message = "defaultFavoriteSlot must be between -2 and 9")
    val defaultFavoriteSlot: Int = 0,
    // `|` 分隔的 10 个收藏槽名，空项回退默认（回退逻辑在消费侧）
    @field:Size(max = 255, message = "favoriteSlotNames must be at most 255 characters")
    val favoriteSlotNames: String = "",
    // 最近搜索保留条数，0 = 关闭
    @field:Min(0, message = "recentSearchMax must be non-negative")
    val recentSearchMax: Int = 10,
)

data class ReaderPreferences(
    @field:Size(max = 64, message = "readingDirection must be at most 64 characters")
    val readingDirection: String = "rtl",
    @field:Size(max = 64, message = "pageMode must be at most 64 characters")
    val pageMode: String = "dual",
    val firstPageCover: Boolean = true,
    @field:Size(max = 64, message = "pageScaling must be at most 64 characters")
    val pageScaling: String = "fit",
    @field:Size(max = 64, message = "startPosition must be at most 64 characters")
    val startPosition: String = "top_right",
    val autoPlayIntervalSec: Int = 2,
    val showProgress: Boolean = true,
    val showPageInterval: Boolean = true,
    val fullscreen: Boolean = true,
    val brightness: Int = 0,
    // ---- Wave-1 A 组（1c 阅读器深化，入 reader 节可同步） ----
    // black|gray|white
    @field:Size(max = 64, message = "backgroundColor must be at most 64 characters")
    val backgroundColor: String = "black",
    // threeZone|edgeOnly|disabled
    @field:Size(max = 64, message = "tapZoneScheme must be at most 64 characters")
    val tapZoneScheme: String = "threeZone",
    val keyboardPaging: Boolean = true,
    @field:DecimalMin(value = "1.0", inclusive = false, message = "zoomStep must be greater than 1")
    val zoomStep: Double = 1.5,
    @field:DecimalMin(value = "1.0", message = "maxZoom must be at least 1")
    val maxZoom: Double = 5.0,
    @field:Min(0, message = "dualPageGap must be non-negative")
    val dualPageGap: Int = 8,
    val splitWidePages: Boolean = false,
    @field:Min(0, message = "preloadCount must be non-negative")
    val preloadCount: Int = 2,
    // slide|fade|none
    @field:Size(max = 64, message = "pageTransition must be at most 64 characters")
    val pageTransition: String = "slide",
)

data class PrivacyPreferences(
    val enableAnalytics: Boolean = true,
)

/** PUT /api/v1/preferences 请求 — 所有字段可选，深度合并 */
data class PreferenceUpdateRequest(
    @field:Valid val general: GeneralPreferences? = null,
    @field:Valid val reader: ReaderPreferences? = null,
    @field:Valid val privacy: PrivacyPreferences? = null,
)
