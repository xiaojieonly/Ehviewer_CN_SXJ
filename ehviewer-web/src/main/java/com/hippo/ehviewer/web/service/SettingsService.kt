package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.web.config.EhCoreConfigProperties
import com.hippo.ehviewer.web.dto.*
import org.springframework.stereotype.Service

@Service
class SettingsService(
    private val config: EhCoreConfigProperties,
    private val serverConfig: ServerConfigService,
) {

    fun getSettings(): SettingsResponse {
        return SettingsResponse(
            download = DownloadSettings(
                path = config.download.path,
                workerCount = config.download.workerCount,
                downloadDelay = config.download.downloadDelay,
                downloadTimeout = config.download.downloadTimeout,
                maxConcurrentGalleries = config.download.maxConcurrentGalleries,
                maxConcurrentImages = config.download.maxConcurrentImages
            ),
            cache = CacheSettings(
                path = config.download.cachePath,
                sizeMb = config.download.cacheSizeMb
            ),
            smb = SmbSettings(
                enabled = config.smb.enabled
            ),
            security = SecuritySettings(
                requireAuth = serverConfig.getBoolean(ServerConfigService.KEY_REQUIRE_AUTH, false),
                sessionTimeout = serverConfig.getLong(ServerConfigService.KEY_SESSION_TIMEOUT, 86400),
            ),
            processing = ProcessingSettings(
                enabled = serverConfig.getBoolean("processing.enabled", false),
                defaultType = serverConfig.get("processing.default_type", "UPSCALE_2X"),
                outputFormat = serverConfig.get("processing.output_format", "png"),
                outputQuality = serverConfig.get("processing.output_quality", "90").toIntOrNull() ?: 90,
            ),
        )
    }

    fun updateSettings(request: SettingsUpdateRequest): Boolean {
        request.download?.let { dl ->
            config.download.path = dl.path
            config.download.workerCount = dl.workerCount
            config.download.downloadDelay = dl.downloadDelay
            config.download.downloadTimeout = dl.downloadTimeout
            config.download.maxConcurrentGalleries = dl.maxConcurrentGalleries
            config.download.maxConcurrentImages = dl.maxConcurrentImages
        }
        request.cache?.let { cache ->
            config.download.cachePath = cache.path
            config.download.cacheSizeMb = cache.sizeMb
        }
        request.smb?.let { smb ->
            config.smb.enabled = smb.enabled
        }
        request.security?.let { sec ->
            serverConfig.setBoolean(ServerConfigService.KEY_REQUIRE_AUTH, sec.requireAuth)
            serverConfig.set(ServerConfigService.KEY_SESSION_TIMEOUT, sec.sessionTimeout.toString())
        }
        request.processing?.let { proc ->
            serverConfig.setBoolean("processing.enabled", proc.enabled)
            serverConfig.set("processing.default_type", proc.defaultType)
            serverConfig.set("processing.output_format", proc.outputFormat)
            serverConfig.set("processing.output_quality", proc.outputQuality.toString())
        }
        return true
    }
}
