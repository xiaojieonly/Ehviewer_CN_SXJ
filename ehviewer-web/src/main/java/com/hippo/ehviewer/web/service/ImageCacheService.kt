package com.hippo.ehviewer.web.service

import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Service
class ImageCacheService {
    private val maxCacheSize = 500L
    private val maxCacheBytes = 200L * 1024 * 1024

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val accessOrder = ArrayDeque<String>()
    private val currentSize = AtomicLong(0)

    fun getCachedImage(url: String): ByteArray? {
        val key = hashUrl(url)
        val entry = cache[key] ?: return null
        synchronized(accessOrder) {
            accessOrder.remove(key)
            accessOrder.addLast(key)
        }
        return entry.data
    }

    fun cacheImage(url: String, data: ByteArray) {
        val key = hashUrl(url)
        val entry = CacheEntry(data, data.size.toLong())
        cache[key] = entry
        synchronized(accessOrder) {
            accessOrder.remove(key)
            accessOrder.addLast(key)
            currentSize.addAndGet(entry.size)
        }
        evictIfNeeded()
    }

    fun getOrFetch(url: String): ByteArray? {
        return getCachedImage(url)
    }

    fun clearCache() {
        synchronized(accessOrder) {
            cache.clear()
            accessOrder.clear()
            currentSize.set(0)
        }
    }

    fun getCacheSize(): Int = cache.size

    private fun evictIfNeeded() {
        synchronized(accessOrder) {
            while (cache.size > maxCacheSize || currentSize.get() > maxCacheBytes) {
                if (accessOrder.isEmpty()) break
                val oldest = accessOrder.removeFirst()
                val removed = cache.remove(oldest)
                if (removed != null) {
                    currentSize.addAndGet(-removed.size)
                }
            }
        }
    }

    private fun hashUrl(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(url.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    private data class CacheEntry(val data: ByteArray, val size: Long)
}
