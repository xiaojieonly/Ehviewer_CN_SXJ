package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.config.WebSocketConfig
import com.hippo.anotherviewer.web.dto.*
import com.hippo.anotherviewer.web.processing.ImageProcessingService
import com.hippo.anotherviewer.web.service.DownloadService
import com.hippo.anotherviewer.web.service.ImageCacheService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.lang.management.ManagementFactory
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToLong

@RestController
@RequestMapping("/api/v1/metrics")
class MetricsController(
    private val imageCacheService: ImageCacheService,
    private val downloadService: DownloadService,
    private val config: SiteCoreConfigProperties,
    private val processingService: ImageProcessingService
) {

    companion object {
        // Loaded from /version.properties, which the generateVersionProperties
        // Gradle task writes from the webVersion project property
        // (gradle.properties: webVersion=1.1.0, override via -PwebVersion=),
        // so the reported version always matches the built jar. The fallback
        // string only appears in dev/IDE runs where processResources hasn't
        // produced the resource (e.g. direct unit-test runs in an IDE).
        private val VERSION: String = run {
            val stream = MetricsController::class.java.getResourceAsStream("/version.properties")
                ?: return@run "1.0.0-SNAPSHOT"
            stream.use { input ->
                java.util.Properties().apply { load(input) }.getProperty("anotherviewer.version")
                    ?: "1.0.0-SNAPSHOT"
            }
        }
        private val runtime = Runtime.getRuntime()
        private val startedAtMillis = System.currentTimeMillis()

        /** Total images served by the streaming endpoint (incremented by ImageProxyController). */
        val imagesServed = AtomicLong(0)
    }

    /**
     * Flat metrics response per contracts/openapi.yaml, with real values
     * (uptime, JVM memory, active downloads, processing queue, cache usage).
     */
    @GetMapping
    fun getMetrics(): MetricsV1Response {
        val cacheStats = imageCacheService.getCacheStats()
        val now = System.currentTimeMillis()

        val metrics = mutableMapOf<String, MetricValue>()

        // Cache metrics
        metrics["anotherviewer.cache.memory.entries"] = MetricValue(
            type = "gauge",
            value = cacheStats.memoryCacheEntries
        )
        metrics["anotherviewer.cache.memory.size.bytes"] = MetricValue(
            type = "gauge",
            value = imageCacheService.getMemorySizeBytes()
        )
        metrics["anotherviewer.cache.disk.size.bytes"] = MetricValue(
            type = "gauge",
            value = cacheStats.diskCacheSizeBytes
        )
        metrics["anotherviewer.cache.disk.entries"] = MetricValue(
            type = "gauge",
            value = imageCacheService.getDiskEntryCount()
        )
        metrics["anotherviewer.cache.hit.ratio"] = MetricValue(
            type = "gauge",
            value = cacheStats.hitRate
        )

        // Download metrics
        metrics["anotherviewer.download.active"] = MetricValue(
            type = "gauge",
            value = downloadService.getActiveDownloadCount()
        )
        metrics["anotherviewer.download.completed.total"] = MetricValue(
            type = "counter",
            value = downloadService.getCompletedDownloadCount()
        )

        // Processing metrics
        metrics["anotherviewer.process.queue.size"] = MetricValue(
            type = "gauge",
            value = processingService.getQueueSize()
        )
        metrics["anotherviewer.process.completed.total"] = MetricValue(
            type = "counter",
            value = processingService.getCompletedTaskCount()
        )

        // WebSocket connections with an accepted CONNECT frame
        metrics["anotherviewer.ws.connections.active"] = MetricValue(
            type = "gauge",
            value = WebSocketConfig.activeConnections.get()
        )

        val usedHeap = runtime.totalMemory() - runtime.freeMemory()
        return MetricsV1Response(
            timestamp = Instant.ofEpochMilli(now).toString(),
            uptimeSeconds = (now - startedAtMillis) / 1000,
            jvmMemoryUsedBytes = usedHeap,
            jvmMemoryMaxBytes = runtime.maxMemory(),
            activeDownloads = downloadService.getActiveDownloadCount(),
            queuedProcessingTasks = processingService.getQueueSize(),
            diskCacheUsedBytes = cacheStats.diskCacheSizeBytes,
            diskCacheMaxBytes = cacheStats.diskCacheMaxBytes,
            totalGalleriesServed = imagesServed.get(),
            metrics = metrics
        )
    }

    @GetMapping("/dashboard")
    fun getDashboard(): DashboardResponse {
        val uptimeMs = ManagementFactory.getRuntimeMXBean().uptime
        val cacheStats = imageCacheService.getCacheStats()

        // MASTER-2026-08-22 P5：真实内存上限（配置字节值），替换 entries×1MB 粗估。
        val memoryMaxBytes = imageCacheService.maxMemoryBytes
        val memoryUsedBytes = imageCacheService.getMemorySizeBytes()
        val diskMaxBytes = cacheStats.diskCacheMaxBytes

        val activeDownloads = downloadService.getActiveDownloads()

        return DashboardResponse(
            timestamp = Instant.now().toString(),
            summary = DashboardSummary(
                status = "UP",
                uptime = formatUptime(uptimeMs),
                version = VERSION
            ),
            cache = DashboardCache(
                memoryEntries = cacheStats.memoryCacheEntries,
                memoryUsedBytes = memoryUsedBytes,
                memoryMaxBytes = memoryMaxBytes,
                // P5：真实使用率（此前硬编码 0.0）。
                memoryUsagePercent = percentOf(memoryUsedBytes, memoryMaxBytes),
                diskUsedBytes = cacheStats.diskCacheSizeBytes,
                diskMaxBytes = diskMaxBytes,
                diskUsagePercent = percentOf(cacheStats.diskCacheSizeBytes, diskMaxBytes),
                hitRatio = cacheStats.hitRate
            ),
            downloads = DashboardDownloads(
                active = downloadService.getActiveDownloadCount(),
                completedTotal = downloadService.getCompletedDownloadCount(),
                failedTotal = downloadService.getFailedDownloadCount(),
                activeTasks = activeDownloads.take(10).map { item ->
                    DashboardDownloadTask(
                        taskId = item.id,
                        galleryId = item.gid,
                        galleryTitle = item.title,
                        state = downloadStateName(item.state),
                        progress = if (item.total > 0) item.done.toDouble() / item.total else 0.0,
                        downloadedPages = item.done,
                        totalPages = item.total
                    )
                }
            ),
            processing = DashboardProcessing(
                queueSize = processingService.getQueueSize(),
                completedTotal = processingService.getCompletedTaskCount(),
                // MASTER-2026-08-22 P5：契约 §4.3 语义——「非占位处理器已连接」，
                // 与是否有活跃任务无关（此前误用 activeTasks.isNotEmpty()）。
                processorAvailable = processingService.nonNoopProcessorAvailable()
            ),
            websocket = DashboardWebSocket(
                activeConnections = WebSocketConfig.activeConnections.get()
            )
        )
    }

    private fun percentOf(used: Long, max: Long): Double {
        if (max <= 0) return 0.0
        return (used.toDouble() / max * 1000.0).roundToLong() / 10.0
    }

    private fun downloadStateName(state: Int): String = when (state) {
        0 -> "pending"
        1 -> "starting"
        2 -> "downloading"
        3 -> "completed"
        4 -> "failed"
        else -> "unknown"
    }

    private fun formatUptime(ms: Long): String {
        val seconds = ms / 1000
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        return when {
            days > 0 -> "${days}d ${hours}h ${minutes}m"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }
}
