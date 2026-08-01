package com.hippo.ehviewer.web.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "ehviewer")
class EhCoreConfigProperties {
    var download: DownloadProperties = DownloadProperties()
    var reader: ReaderProperties = ReaderProperties()
    var smb: SmbProperties = SmbProperties()
    var security: SecurityProperties = SecurityProperties()

    class DownloadProperties {
        var path: String = "./data/downloads"
        var cachePath: String = "./data/cache"
        var cacheSizeMb: Long = 10240
        var workerCount: Int = 3
        var downloadDelay: Int = 0
        var downloadTimeout: Long = 60000
        var maxConcurrentGalleries: Int = 3
        var maxConcurrentImages: Int = 3
    }

    class ReaderProperties {
        /** Number of pages to prefetch into the image cache after a cache-miss serve. */
        var prefetchPages: Int = 3
    }

    class SmbProperties {
        var enabled: Boolean = false
    }

    class SecurityProperties {
        var sessionTimeout: Long = 86400
        var encryptionKeyPath: String = "./data/security.key"
    }
}
