package com.hippo.ehviewer.web.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "ehviewer")
class EhCoreConfigProperties {
    /** Base directory for all runtime data (db, downloads, cache, security key). */
    var dataDir: String = "./data"
    var download: DownloadProperties = DownloadProperties()
    var reader: ReaderProperties = ReaderProperties()
    var smb: SmbProperties = SmbProperties()
    var security: SecurityProperties = SecurityProperties()

    class DownloadProperties {
        // Paths are bound from application.yml via `${ehviewer.data-dir}/...`
        var path: String = ""
        var cachePath: String = ""
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
        var encryptionKeyPath: String = ""
    }
}
