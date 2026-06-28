package com.hippo.ehviewer.web.dto

data class SettingsResponse(
    val download: DownloadSettings,
    val cache: CacheSettings,
    val smb: SmbSettings
)

data class DownloadSettings(
    val path: String,
    val workerCount: Int,
    val downloadDelay: Int,
    val downloadTimeout: Long,
    val maxConcurrentGalleries: Int,
    val maxConcurrentImages: Int
)

data class CacheSettings(
    val path: String,
    val sizeMb: Long
)

data class SmbSettings(
    val enabled: Boolean
)

data class SettingsUpdateRequest(
    val download: DownloadSettings?,
    val cache: CacheSettings?,
    val smb: SmbSettings?
)
