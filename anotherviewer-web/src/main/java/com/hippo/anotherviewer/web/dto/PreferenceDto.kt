package com.hippo.anotherviewer.web.dto

import jakarta.validation.Valid
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
    @field:Size(max = 64, message = "listMode must be at most 64 characters")
    val listMode: String = "list",
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
    val showSiteEvents: Boolean = true,
    val showSiteLimits: Boolean = true,
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
