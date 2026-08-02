package com.hippo.anotherviewer.web.dto

/** GET /api/v1/preferences 响应 */
data class PreferenceResponse(
    val general: GeneralPreferences = GeneralPreferences(),
    val reader: ReaderPreferences = ReaderPreferences(),
    val privacy: PrivacyPreferences = PrivacyPreferences(),
)

data class GeneralPreferences(
    val theme: String = "light",
    val themeAutoSwitch: Boolean = false,
    val launchPage: String = "homepage",
    val listMode: String = "list",
    val showReadProgress: Boolean = true,
    val detailSize: String = "long",
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
    val readingDirection: String = "rtl",
    val pageMode: String = "dual",
    val firstPageCover: Boolean = true,
    val pageScaling: String = "fit",
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
    val general: GeneralPreferences? = null,
    val reader: ReaderPreferences? = null,
    val privacy: PrivacyPreferences? = null,
)
