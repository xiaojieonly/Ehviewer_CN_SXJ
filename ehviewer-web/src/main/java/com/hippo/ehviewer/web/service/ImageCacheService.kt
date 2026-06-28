package com.hippo.ehviewer.web.service

import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

@Service
class ImageCacheService {
    private val cache = ConcurrentHashMap<String, ByteArray>()

    fun getCachedImage(url: String): ByteArray? {
        val key = hashUrl(url)
        return cache[key]
    }

    fun cacheImage(url: String, data: ByteArray) {
        val key = hashUrl(url)
        cache[key] = data
    }

    fun getOrFetch(url: String): ByteArray? {
        return getCachedImage(url)
    }

    fun clearCache() {
        cache.clear()
    }

    fun getCacheSize(): Int = cache.size

    private fun hashUrl(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(url.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}
