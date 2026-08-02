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
        private const val VERSION = "1.0.0-SNAPSHOT"
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

        val memoryMaxBytes = cacheStats.memoryCacheMaxEntries.toLong() * 1024 * 1024 // rough estimate
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
                memoryUsedBytes = imageCacheService.getMemorySizeBytes(),
                memoryMaxBytes = memoryMaxBytes,
                memoryUsagePercent = 0.0,
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
                processorAvailable = processingService.getActiveTasks().isNotEmpty()
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
