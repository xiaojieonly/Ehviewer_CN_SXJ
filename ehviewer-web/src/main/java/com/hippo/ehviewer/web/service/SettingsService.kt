package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.web.config.EhCoreConfigProperties
import com.hippo.ehviewer.web.dto.*
import org.springframework.stereotype.Service

@Service
class SettingsService(private val config: EhCoreConfigProperties) {

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
            )
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
        return true
    }
}
