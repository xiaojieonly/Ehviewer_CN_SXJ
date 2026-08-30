package com.hippo.anotherviewer.web.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.RemovalCause
import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.CacheStatsResponse
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/** 全量清缓存结果：removed = 实际删除文件数，total = 清前磁盘文件数。 */
data class CacheClearOutcome(
    val removed: Long,
    val total: Long,
)

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
    private val config: SiteCoreConfigProperties,
) {
    private val logger = LoggerFactory.getLogger(ImageCacheService::class.java)

    companion object {
        private const val DEFAULT_MAX_MEMORY_ENTRIES = 200L
        /** 默认内存层字节上限（64MB）；可经 anotherviewer.download.cache-memory-mb 调整。 */
        private const val DEFAULT_MAX_MEMORY_MB = 64L
        private const val URL_DIR = "_url"
    }

    private val maxMemoryEntries: Long get() = DEFAULT_MAX_MEMORY_ENTRIES

    /** MASTER-2026-08-22 P4：内存层字节上限（真实配置值，Metrics 与淘汰共用）。 */
    val maxMemoryBytes: Long get() = config.download.cacheMemoryMb * 1024L * 1024L
    private val maxDiskBytes: Long get() = config.download.cacheSizeMb * 1024L * 1024L
    private val cacheDir: File get() = File(config.download.cachePath)

    /** Hot memory tier — Caffeine with stats recording for hit/miss tracking.
     *
     * MASTER-2026-08-22 P4：按字节加权淘汰（此前仅按条目数 200 淘汰，单张巨图
     * 常驻堆、memorySizeBytes 只统计不设限）。注意 Caffeine 不允许 maximumSize
     * 与 maximumWeight 并存，字节上限即唯一硬界。 */
    private val memoryCache: Cache<String, ByteArray> = Caffeine.newBuilder()
        .maximumWeight(maxMemoryBytes)
        .weigher { _: String, value: ByteArray -> value.size.coerceAtLeast(1) }
        .recordStats()
        .removalListener<String, ByteArray> { _, value: ByteArray?, _: RemovalCause ->
            if (value != null) memorySizeBytes.addAndGet(-value.size.toLong())
        }
        .build()

    /** Running total of bytes on disk, seeded at startup by [init]. */
    private val diskSizeBytes = AtomicLong(0)

    /**
     * Running count of files on disk, seeded at startup by [init] and
     * maintained by every disk write/delete path — [getDiskEntryCount] reads
     * this counter instead of scanning the whole tree (G4: metrics from
     * 150-200ms back to ms-level).
     */
    private val diskEntryCount = AtomicLong(0)

    /** Running total of byte sizes of values currently held in the memory cache. */
    private val memorySizeBytes = AtomicLong(0)

    // ── lifecycle ──────────────────────────────────────────────

    @PostConstruct
    fun init() {
        cacheDir.mkdirs()
        val files = collectFiles(cacheDir)
        diskSizeBytes.set(files.sumOf { it.length() })
        diskEntryCount.set(files.size.toLong())
        logger.info(
            "ImageCacheService initialised: cacheDir={}, files={}, diskSize={}MB, maxDisk={}MB, maxMemEntries={}",
            cacheDir.absolutePath,
            diskEntryCount.get(),
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

    /**
     * Locate the cached page file on disk (any extension) so callers can serve
     * it with the correct Content-Type. Returns null when not cached on disk.
     */
    fun findCachedPageFile(galleryId: Long, page: Int): File? = findPageFile(galleryId, page)

    /**
     * Locate an AI-enhanced page file under `{cachePath}/enhanced/{galleryId}/{page}.*`
     * produced by the processing pipeline. Returns null when no enhanced
     * version exists for the page.
     */
    fun getEnhancedImage(galleryId: Long, page: Int): File? {
        val dir = File(cacheDir, "enhanced/$galleryId")
        if (!dir.isDirectory) return null
        return dir.listFiles()
            ?.firstOrNull { it.isFile && it.nameWithoutExtension == page.toString() }
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
            val files = collectFiles(dir)
            diskSizeBytes.addAndGet(-files.sumOf { it.length() })
            diskEntryCount.addAndGet(-files.size.toLong())
            dir.deleteRecursively()
            removed = true
        }

        return removed
    }

    // ── whole-cache management (backward-compatible) ───────────

    /**
     * 清空内存 + 磁盘两级缓存，边删边计数。
     *
     * @param handle 异步 Job 进度句柄（可选）：先 collectFiles 得 total，
     *   逐文件删除 processed++ 并上报 `progress("删除缓存文件", ...)`；
     *   同步调用（无 handle）保持原行为，仅返回计数。
     */
    fun clearCache(handle: JobService.JobHandle? = null): CacheClearOutcome {
        memoryCache.invalidateAll()
        var removed = 0L
        val files = if (cacheDir.isDirectory) collectFiles(cacheDir) else emptyList()
        val total = files.size.toLong()
        for (file in files) {
            if (file.delete()) removed++
            handle?.progress("删除缓存文件", removed, total)
        }
        // 清理删空后的目录（与旧 deleteRecursively 行为对齐）。
        cacheDir.listFiles()
            ?.filter { it.isDirectory && (it.listFiles()?.isEmpty() != false) }
            ?.forEach { it.delete() }
        // 重新归一两个运行值（删除失败的文件仍留在磁盘上）。
        val remaining = collectFiles(cacheDir)
        diskSizeBytes.set(remaining.sumOf { it.length() })
        diskEntryCount.set(remaining.size.toLong())
        return CacheClearOutcome(removed, total)
    }

    fun getCacheSize(): Int = memoryCache.asMap().size

    /** Number of files currently stored on disk (running counter, O(1)). */
    fun getDiskEntryCount(): Long = diskEntryCount.get()

    /** Total byte size of values currently held in the memory cache. */
    fun getMemorySizeBytes(): Long = memorySizeBytes.get()

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

    /**
     * MASTER-2026-08-22 P4：同步排空 Caffeine 待处理维护队列（按权重淘汰、
     * removal listener 回调均为惰性执行）。测试与需要精确 memorySizeBytes 的
     * 路径在读取前调用。
     */
    internal fun drainMaintenance() {
        memoryCache.cleanUp()
    }

    private fun promoteToMemory(key: String, data: ByteArray) {
        putToMemory(key, data)
    }

    private fun putToMemory(key: String, data: ByteArray) {
        memoryCache.put(key, data)
        memorySizeBytes.addAndGet(data.size.toLong())
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
            // If overwriting, subtract old size first (entry count unchanged).
            val existed = file.isFile
            if (existed) {
                diskSizeBytes.addAndGet(-file.length())
            }
            file.writeBytes(data)
            diskSizeBytes.addAndGet(data.size.toLong())
            if (!existed) {
                diskEntryCount.incrementAndGet()
            }
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
                diskEntryCount.decrementAndGet()
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
