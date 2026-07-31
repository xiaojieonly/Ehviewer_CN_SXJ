package com.hippo.ehviewer.web.dto

data class SettingsResponse(
    val download: DownloadSettings,
    val cache: CacheSettings,
    val smb: SmbSettings,
    val security: SecuritySettings,
    val processing: ProcessingSettings
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
    val smb: SmbSettings?,
    val security: SecuritySettings? = null,
    val processing: ProcessingSettings? = null
)

data class SecuritySettings(
    val requireAuth: Boolean = false,
    val sessionTimeout: Long = 86400,
)

data class ProcessingSettings(
    val enabled: Boolean = false,
    val defaultType: String = "UPSCALE_2X",
    val outputFormat: String = "png",
    val outputQuality: Int = 90,
)
