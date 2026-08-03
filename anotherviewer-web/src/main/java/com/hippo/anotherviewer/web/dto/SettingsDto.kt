package com.hippo.anotherviewer.web.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

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
    // Bounds mirror the admin UI stepper (AdminDownload.vue clamps 1..10).
    @field:Min(1, message = "workerCount must be between 1 and 10")
    @field:Max(10, message = "workerCount must be between 1 and 10")
    val workerCount: Int,
    @field:Min(0, message = "downloadDelay must be non-negative")
    val downloadDelay: Int,
    @field:Min(0, message = "downloadTimeout must be non-negative")
    val downloadTimeout: Long,
    // Bounds mirror the admin UI stepper (AdminDownload.vue clamps 1..20).
    @field:Min(1, message = "maxConcurrentGalleries must be between 1 and 20")
    @field:Max(20, message = "maxConcurrentGalleries must be between 1 and 20")
    val maxConcurrentGalleries: Int,
    @field:Min(1, message = "maxConcurrentImages must be between 1 and 20")
    @field:Max(20, message = "maxConcurrentImages must be between 1 and 20")
    val maxConcurrentImages: Int
)

data class CacheSettings(
    val path: String,
    @field:Min(1, message = "sizeMb must be positive")
    val sizeMb: Long
)

data class SmbSettings(
    val enabled: Boolean
)

data class SettingsUpdateRequest(
    @field:Valid val download: DownloadSettings?,
    @field:Valid val cache: CacheSettings?,
    @field:Valid val smb: SmbSettings?,
    @field:Valid val security: SecuritySettings? = null,
    @field:Valid val processing: ProcessingSettings? = null,
    @field:Valid val proxy: ProxySettings? = null,
)

data class SecuritySettings(
    val requireAuth: Boolean = false,
    @field:Min(0, message = "sessionTimeout must be non-negative")
    val sessionTimeout: Long = 86400,
)

data class ProcessingSettings(
    val enabled: Boolean = false,
    @field:Size(max = 32, message = "defaultType must be at most 32 characters")
    val defaultType: String = "UPSCALE_2X",
    @field:Size(max = 16, message = "outputFormat must be at most 16 characters")
    val outputFormat: String = "png",
    @field:Min(1, message = "outputQuality must be between 1 and 100")
    @field:Max(100, message = "outputQuality must be between 1 and 100")
    val outputQuality: Int = 90,
)

data class ProxySettings(
    val enabled: Boolean = false,
    @field:Size(max = 32, message = "type must be at most 32 characters")
    val type: String = "http",
    @field:Size(max = 255, message = "host must be at most 255 characters")
    val host: String = "",
    @field:Min(0, message = "port must be between 0 and 65535")
    @field:Max(65535, message = "port must be between 0 and 65535")
    val port: Int = 0,
    @field:Size(max = 255, message = "username must be at most 255 characters")
    val username: String = "",
    @field:Size(max = 255, message = "password must be at most 255 characters")
    val password: String = "",
    val proxyPasswordSet: Boolean = false,
)

/** Optional request body for POST /api/v1/proxy/test — tests the given values. */
data class ProxyTestRequest(
    val enabled: Boolean? = null,
    @field:Size(max = 32, message = "type must be at most 32 characters")
    val type: String? = null,
    @field:Size(max = 255, message = "host must be at most 255 characters")
    val host: String? = null,
    @field:Min(0, message = "port must be between 0 and 65535")
    @field:Max(65535, message = "port must be between 0 and 65535")
    val port: Int? = null,
    @field:Size(max = 255, message = "username must be at most 255 characters")
    val username: String? = null,
    @field:Size(max = 255, message = "password must be at most 255 characters")
    val password: String? = null,
)

data class ProxyTestResponse(
    val success: Boolean,
    val latencyMs: Long = 0,
    val error: String = "",
)
