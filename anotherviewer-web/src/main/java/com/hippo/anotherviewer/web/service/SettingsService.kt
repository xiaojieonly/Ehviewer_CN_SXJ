package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.*
import org.springframework.stereotype.Service

@Service
class SettingsService(
    private val config: SiteCoreConfigProperties,
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
            proxy = ProxySettings(
                enabled = serverConfig.getBoolean(WebProxyManager.KEY_ENABLED, false),
                type = serverConfig.get(WebProxyManager.KEY_TYPE, "http"),
                host = serverConfig.get(WebProxyManager.KEY_HOST),
                port = serverConfig.get(WebProxyManager.KEY_PORT, "0").toIntOrNull() ?: 0,
                username = serverConfig.get(WebProxyManager.KEY_USERNAME),
                password = "",
                proxyPasswordSet = serverConfig.get(WebProxyManager.KEY_PASSWORD).isNotEmpty(),
            ),
        )
    }

    fun updateSettings(request: SettingsUpdateRequest): Boolean {
        request.download?.let { dl ->
            if (dl.path.isNotBlank()) {
                config.download.path = dl.path
                serverConfig.set(ServerConfigService.KEY_DOWNLOAD_PATH, dl.path)
            }
            config.download.workerCount = dl.workerCount
            config.download.downloadDelay = dl.downloadDelay
            config.download.downloadTimeout = dl.downloadTimeout
            config.download.maxConcurrentGalleries = dl.maxConcurrentGalleries
            config.download.maxConcurrentImages = dl.maxConcurrentImages
        }
        request.cache?.let { cache ->
            if (cache.path.isNotBlank()) {
                config.download.cachePath = cache.path
                serverConfig.set(ServerConfigService.KEY_CACHE_PATH, cache.path)
            }
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
        request.proxy?.let { proxy ->
            serverConfig.setBoolean(WebProxyManager.KEY_ENABLED, proxy.enabled)
            serverConfig.set(WebProxyManager.KEY_TYPE, proxy.type)
            serverConfig.set(WebProxyManager.KEY_HOST, proxy.host)
            serverConfig.set(WebProxyManager.KEY_PORT, proxy.port.toString())
            serverConfig.set(WebProxyManager.KEY_USERNAME, proxy.username)
            // An empty/absent password means "keep the stored one" — the GET
            // endpoint never echoes it back, so the UI cannot resend it.
            if (!proxy.password.isNullOrEmpty()) {
                serverConfig.set(WebProxyManager.KEY_PASSWORD, proxy.password)
            }
        }
        return true
    }
}
