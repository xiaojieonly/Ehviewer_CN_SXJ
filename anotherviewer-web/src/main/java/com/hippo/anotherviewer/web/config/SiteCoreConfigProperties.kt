package com.hippo.anotherviewer.web.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "anotherviewer")
class SiteCoreConfigProperties {
    /** Base directory for all runtime data (db, downloads, cache, security key). */
    var dataDir: String = "./data"
    var download: DownloadProperties = DownloadProperties()
    var reader: ReaderProperties = ReaderProperties()
    var smb: SmbProperties = SmbProperties()
    var security: SecurityProperties = SecurityProperties()
    var proxy: ProxyProperties = ProxyProperties()

    /** Proxy/fetch hardening (MASTER-2026-08-22 S2). */
    class ProxyProperties {
        /** Hard cap for buffering an upstream proxied response, in bytes. */
        var maxResponseBytes: Long = 32L * 1024 * 1024
    }

    class DownloadProperties {
        // Paths are bound from application.yml via `${anotherviewer.data-dir}/...`
        var path: String = ""
        var cachePath: String = ""
        var cacheSizeMb: Long = 10240
        /** MASTER-2026-08-22 P4：图片内存缓存字节上限（MB）。 */
        var cacheMemoryMb: Long = 64
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
