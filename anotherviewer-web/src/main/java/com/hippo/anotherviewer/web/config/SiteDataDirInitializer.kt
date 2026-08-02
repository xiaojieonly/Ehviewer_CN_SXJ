package com.hippo.anotherviewer.web.config

import com.hippo.anotherviewer.web.service.ServerConfigService
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

/**
 * Merges persisted download/cache paths from [ServerConfigService] into
 * [SiteCoreConfigProperties] at startup, so values set via the settings UI
 * survive restarts. Persisted values win over the defaults derived from
 * `anotherviewer.data-dir`.
 */
@Component
class SiteDataDirInitializer(
    private val config: SiteCoreConfigProperties,
    private val serverConfig: ServerConfigService,
) {

    @PostConstruct
    fun applyPersistedPaths() {
        serverConfig.get(ServerConfigService.KEY_DOWNLOAD_PATH)
            .takeIf { it.isNotBlank() }
            ?.let { config.download.path = it }
        serverConfig.get(ServerConfigService.KEY_CACHE_PATH)
            .takeIf { it.isNotBlank() }
            ?.let { config.download.cachePath = it }
    }
}
