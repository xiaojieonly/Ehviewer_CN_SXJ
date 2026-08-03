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
            dl.path?.takeIf { it.isNotBlank() }?.let {
                config.download.path = it
                serverConfig.set(ServerConfigService.KEY_DOWNLOAD_PATH, it)
            }
            dl.workerCount?.let { config.download.workerCount = it }
            dl.downloadDelay?.let { config.download.downloadDelay = it }
            dl.downloadTimeout?.let { config.download.downloadTimeout = it }
            dl.maxConcurrentGalleries?.let { config.download.maxConcurrentGalleries = it }
            dl.maxConcurrentImages?.let { config.download.maxConcurrentImages = it }
        }
        request.cache?.let { cache ->
            cache.path?.takeIf { it.isNotBlank() }?.let {
                config.download.cachePath = it
                serverConfig.set(ServerConfigService.KEY_CACHE_PATH, it)
            }
            cache.sizeMb?.let { config.download.cacheSizeMb = it }
        }
        request.smb?.let { smb ->
            smb.enabled?.let { config.smb.enabled = it }
        }
        request.security?.let { sec ->
            sec.requireAuth?.let { serverConfig.setBoolean(ServerConfigService.KEY_REQUIRE_AUTH, it) }
            sec.sessionTimeout?.let { serverConfig.set(ServerConfigService.KEY_SESSION_TIMEOUT, it.toString()) }
        }
        request.processing?.let { proc ->
            proc.enabled?.let { serverConfig.setBoolean("processing.enabled", it) }
            proc.defaultType?.let { serverConfig.set("processing.default_type", it) }
            proc.outputFormat?.let { serverConfig.set("processing.output_format", it) }
            proc.outputQuality?.let { serverConfig.set("processing.output_quality", it.toString()) }
        }
        request.proxy?.let { proxy ->
            proxy.enabled?.let { serverConfig.setBoolean(WebProxyManager.KEY_ENABLED, it) }
            proxy.type?.let { serverConfig.set(WebProxyManager.KEY_TYPE, it) }
            proxy.host?.let { serverConfig.set(WebProxyManager.KEY_HOST, it) }
            proxy.port?.let { serverConfig.set(WebProxyManager.KEY_PORT, it.toString()) }
            proxy.username?.let { serverConfig.set(WebProxyManager.KEY_USERNAME, it) }
            // An empty/absent password means "keep the stored one" — the GET
            // endpoint never echoes it back, so the UI cannot resend it.
            if (!proxy.password.isNullOrEmpty()) {
                serverConfig.set(WebProxyManager.KEY_PASSWORD, proxy.password)
            }
        }
        return true
    }
}
