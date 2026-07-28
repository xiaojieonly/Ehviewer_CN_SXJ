package com.hippo.ehviewer.web.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.hippo.ehviewer.web.config.EhCoreConfigProperties
import com.hippo.ehviewer.web.dto.CacheStatsResponse
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/**
 * Two-tier image cache: Caffeine (hot memory) + disk (persistent).
 *
 * Lookup flow:  memory → disk → miss (null)
 * Write flow:   write-through to both tiers
 *
 * Disk layout:
 *   {cachePath}/{galleryId}/{page}.{ext}   — gallery/page entries
 *   {cachePath}/_url/{sha256}              — legacy URL-keyed entries
 */
@Service
class ImageCacheService(
    private val config: EhCoreConfigProperties,
) {
    private val logger = LoggerFactory.getLogger(ImageCacheService::class.java)

    companion object {
        private const val DEFAULT_MAX_MEMORY_ENTRIES = 200L
        private const val URL_DIR = "_url"
    }

    private val maxMemoryEntries: Long get() = DEFAULT_MAX_MEMORY_ENTRIES
    private val maxDiskBytes: Long get() = config.download.cacheSizeMb * 1024L * 1024L
    private val cacheDir: File get() = File(config.download.cachePath)

    /** Hot memory tier — Caffeine with stats recording for hit/miss tracking. */
    private val memoryCache: Cache<String, ByteArray> = Caffeine.newBuilder()
        .maximumSize(maxMemoryEntries)
        .recordStats()
        .build()

    /** Running total of bytes on disk, seeded at startup by [init]. */
    private val diskSizeBytes = AtomicLong(0)

    // ── lifecycle ──────────────────────────────────────────────

    @PostConstruct
    fun init() {
        cacheDir.mkdirs()
        diskSizeBytes.set(scanDiskSize(cacheDir))
        logger.info(
            "ImageCacheService initialised: cacheDir={}, diskSize={}MB, maxDisk={}MB, maxMemEntries={}",
            cacheDir.absolutePath,
            diskSizeBytes.get() / (1024 * 1024),
            maxDiskBytes / (1024 * 1024),
            maxMemoryEntries,
        )
    }

    // ── URL-keyed API (backward-compatible) ────────────────────

    fun getCachedImage(url: String): ByteArray? {
        val key = urlKey(url)
        return getFromMemory(key) ?: getFromDisk(urlDiskPath(key))?.also { promoteToMemory(key, it) }
    }

    fun cacheImage(url: String, data: ByteArray) {
        val key = urlKey(url)
        putToMemory(key, data)
        putToDisk(urlDiskPath(key), data)
    }

    fun getOrFetch(url: String): ByteArray? = getCachedImage(url)

    // ── gallery/page-keyed API ─────────────────────────────────

    fun getCachedImageByKey(galleryId: Long, page: Int): ByteArray? {
        val key = pageKey(galleryId, page)
        getFromMemory(key)?.let { return it }

        val file = findPageFile(galleryId, page) ?: return null
        val data = file.readBytes()
        promoteToMemory(key, data)
        return data
    }

    fun cacheImageByKey(galleryId: Long, page: Int, data: ByteArray, extension: String) {
        val key = pageKey(galleryId, page)
        putToMemory(key, data)
        val ext = extension.removePrefix(".").ifEmpty { "jpg" }
        putToDisk(pageDiskPath(galleryId, page, ext), data)
    }

    // ── gallery management ─────────────────────────────────────

    /**
     * Delete all cached images (memory + disk) for [galleryId].
     * @return true if any data was removed.
     */
    fun clearGalleryCache(galleryId: Long): Boolean {
        var removed = false

        // Remove memory entries whose key starts with "{galleryId}:"
        val prefix = "$galleryId:"
        val keysToRemove = memoryCache.asMap().keys.filter { it.startsWith(prefix) }
        if (keysToRemove.isNotEmpty()) {
            memoryCache.invalidateAll(keysToRemove)
            removed = true
        }

        // Remove disk directory
        val dir = File(cacheDir, galleryId.toString())
        if (dir.isDirectory) {
            val size = scanDiskSize(dir)
            dir.deleteRecursively()
            diskSizeBytes.addAndGet(-size)
            removed = true
        }

        return removed
    }

    // ── whole-cache management (backward-compatible) ───────────

    fun clearCache() {
        memoryCache.invalidateAll()
        if (cacheDir.isDirectory) {
            cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        }
        diskSizeBytes.set(0)
    }

    fun getCacheSize(): Int = memoryCache.asMap().size

    // ── stats ──────────────────────────────────────────────────

    fun getCacheStats(): CacheStatsResponse {
        val stats = memoryCache.stats()
        return CacheStatsResponse(
            diskCacheSizeBytes = diskSizeBytes.get(),
            diskCacheMaxBytes = maxDiskBytes,
            memoryCacheEntries = memoryCache.asMap().size,
            memoryCacheMaxEntries = maxMemoryEntries.toInt(),
            hitCount = stats.hitCount(),
            missCount = stats.missCount(),
            hitRate = stats.hitRate(),
        )
    }

    // ── memory helpers ─────────────────────────────────────────

    private fun getFromMemory(key: String): ByteArray? = memoryCache.getIfPresent(key)

    private fun promoteToMemory(key: String, data: ByteArray) {
        memoryCache.put(key, data)
    }

    private fun putToMemory(key: String, data: ByteArray) {
        memoryCache.put(key, data)
    }

    // ── disk helpers ───────────────────────────────────────────

    private fun getFromDisk(file: File): ByteArray? {
        if (!file.isFile) return null
        return try {
            file.readBytes()
        } catch (e: Exception) {
            logger.warn("Failed to read disk cache file: {}", file, e)
            null
        }
    }

    private fun putToDisk(file: File, data: ByteArray) {
        try {
            file.parentFile?.mkdirs()
            // If overwriting, subtract old size first
            if (file.isFile) {
                diskSizeBytes.addAndGet(-file.length())
            }
            file.writeBytes(data)
            diskSizeBytes.addAndGet(data.size.toLong())
            evictDiskIfNeeded()
        } catch (e: Exception) {
            logger.warn("Failed to write disk cache file: {}", file, e)
        }
    }

    /**
     * Evict oldest disk cache files until total size is within [maxDiskBytes].
     * Operates on a best-effort basis — races are acceptable for a cache.
     */
    private fun evictDiskIfNeeded() {
        if (diskSizeBytes.get() <= maxDiskBytes) return

        val files = collectFiles(cacheDir).sortedBy { it.lastModified() }

        for (file in files) {
            if (diskSizeBytes.get() <= maxDiskBytes) break
            val size = file.length()
            if (file.delete()) {
                diskSizeBytes.addAndGet(-size)
            }
        }

        // Clean up empty directories left behind
        cacheDir.listFiles()
            ?.filter { it.isDirectory && (it.listFiles()?.isEmpty() != false) }
            ?.forEach { it.delete() }
    }

    // ── path / key helpers ─────────────────────────────────────

    private fun urlKey(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun pageKey(galleryId: Long, page: Int): String = "$galleryId:$page"

    private fun urlDiskPath(hash: String): File = File(cacheDir, "$URL_DIR/$hash")

    private fun pageDiskPath(galleryId: Long, page: Int, ext: String): File =
        File(cacheDir, "$galleryId/$page.$ext")

    /**
     * Find a page file on disk regardless of extension: `{cachePath}/{galleryId}/{page}.*`
     */
    private fun findPageFile(galleryId: Long, page: Int): File? {
        val dir = File(cacheDir, galleryId.toString())
        if (!dir.isDirectory) return null
        return dir.listFiles()?.firstOrNull { it.nameWithoutExtension == page.toString() && it.isFile }
    }

    private fun scanDiskSize(dir: File): Long {
        if (!dir.exists()) return 0
        return collectFiles(dir).sumOf { it.length() }
    }

    private fun collectFiles(dir: File): List<File> {
        val result = mutableListOf<File>()
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    stack.addLast(child)
                } else {
                    result.add(child)
                }
            }
        }
        return result
    }
}
