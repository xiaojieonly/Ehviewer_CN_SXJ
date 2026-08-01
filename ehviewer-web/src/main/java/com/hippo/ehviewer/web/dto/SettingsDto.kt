package com.hippo.ehviewer.web.dto

data class SettingsResponse(
    val download: DownloadSettings,
    val cache: CacheSettings,
    val smb: SmbSettings,
    val security: SecuritySettings,
    val processing: ProcessingSettings,
    val proxy: ProxySettings,
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
    val processing: ProcessingSettings? = null,
    val proxy: ProxySettings? = null,
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

data class ProxySettings(
    val enabled: Boolean = false,
    val type: String = "http",
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = "",
)

data class ProxyTestResponse(
    val success: Boolean,
    val latencyMs: Long = 0,
    val error: String = "",
)
