package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.CacheStatsResponse
import com.hippo.anotherviewer.web.dto.DownloadItem
import com.hippo.anotherviewer.web.processing.ImageProcessingService
import com.hippo.anotherviewer.web.service.DownloadService
import com.hippo.anotherviewer.web.service.ImageCacheService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class MetricsControllerTest {

    private lateinit var imageCacheService: ImageCacheService
    private lateinit var downloadService: DownloadService
    private lateinit var processingService: ImageProcessingService
    private lateinit var config: SiteCoreConfigProperties
    private lateinit var controller: MetricsController

    private val defaultCacheStats = CacheStatsResponse(
        diskCacheSizeBytes = 3221225472L,
        diskCacheMaxBytes = 10737418240L,
        memoryCacheEntries = 142,
        memoryCacheMaxEntries = 200,
        hitCount = 870,
        missCount = 130,
        hitRate = 0.87
    )

    /** Mirrors the controller's own loader: read version.properties from the classpath. */
    private fun versionFromResource(): String {
        val stream = MetricsControllerTest::class.java.getResourceAsStream("/version.properties")
            ?: return "1.0.0-SNAPSHOT"
        return stream.use {
            java.util.Properties().apply { load(it) }.getProperty("anotherviewer.version")
                ?: "1.0.0-SNAPSHOT"
        }
    }

    @BeforeEach
    fun setUp() {
        imageCacheService = mock(ImageCacheService::class.java)
        downloadService = mock(DownloadService::class.java)
        processingService = mock(ImageProcessingService::class.java)
        config = SiteCoreConfigProperties()
        config.download.cachePath = "./data/cache"
        config.download.cacheSizeMb = 10240
        controller = MetricsController(imageCacheService, downloadService, config, processingService)

        `when`(imageCacheService.getCacheStats()).thenReturn(defaultCacheStats)
        `when`(imageCacheService.getDiskEntryCount()).thenReturn(8432L)
        `when`(imageCacheService.getMemorySizeBytes()).thenReturn(0L)
        `when`(downloadService.getActiveDownloadCount()).thenReturn(0)
        `when`(downloadService.getCompletedDownloadCount()).thenReturn(0L)
        `when`(downloadService.getFailedDownloadCount()).thenReturn(0L)
        `when`(downloadService.getActiveDownloads()).thenReturn(emptyList())
        `when`(processingService.getQueueSize()).thenReturn(0)
        `when`(processingService.getCompletedTaskCount()).thenReturn(0L)
        `when`(processingService.getActiveTasks()).thenReturn(emptyList())
    }

    @Test
    fun `metrics returns all expected metric keys`() {
        val response = controller.getMetrics()

        assertNotNull(response.timestamp)
        assertTrue(response.metrics.containsKey("anotherviewer.cache.memory.entries"))
        assertTrue(response.metrics.containsKey("anotherviewer.cache.disk.size.bytes"))
        assertTrue(response.metrics.containsKey("anotherviewer.cache.hit.ratio"))
        assertTrue(response.metrics.containsKey("anotherviewer.download.active"))
        assertTrue(response.metrics.containsKey("anotherviewer.download.completed.total"))
        assertTrue(response.metrics.containsKey("anotherviewer.process.queue.size"))
        assertTrue(response.metrics.containsKey("anotherviewer.process.completed.total"))
        assertTrue(response.metrics.containsKey("anotherviewer.ws.connections.active"))
    }

    @Test
    fun `metrics exposes flat contract fields with real values`() {
        `when`(downloadService.getActiveDownloadCount()).thenReturn(3)
        `when`(processingService.getQueueSize()).thenReturn(5)

        val response = controller.getMetrics()

        // Flat fields per openapi.yaml MetricsResponse (C5).
        assertTrue(response.uptimeSeconds >= 0)
        assertTrue(response.jvmMemoryUsedBytes > 0)
        assertTrue(response.jvmMemoryMaxBytes > 0)
        assertEquals(3, response.activeDownloads)
        assertEquals(5, response.queuedProcessingTasks)
        assertEquals(3221225472L, response.diskCacheUsedBytes)
        assertEquals(10737418240L, response.diskCacheMaxBytes)
        assertTrue(response.totalGalleriesServed >= 0)
    }

    @Test
    fun `metrics returns correct cache values`() {
        val response = controller.getMetrics()

        assertEquals(142, response.metrics["anotherviewer.cache.memory.entries"]?.value)
        assertEquals(3221225472L, response.metrics["anotherviewer.cache.disk.size.bytes"]?.value)
        assertEquals(8432L, response.metrics["anotherviewer.cache.disk.entries"]?.value)
        assertEquals(0.87, response.metrics["anotherviewer.cache.hit.ratio"]?.value)
        assertEquals("gauge", response.metrics["anotherviewer.cache.memory.entries"]?.type)
        assertEquals("gauge", response.metrics["anotherviewer.cache.hit.ratio"]?.type)
    }

    @Test
    fun `metrics returns correct download values`() {
        `when`(downloadService.getActiveDownloadCount()).thenReturn(3)
        `when`(downloadService.getCompletedDownloadCount()).thenReturn(47L)

        val response = controller.getMetrics()

        assertEquals(3, response.metrics["anotherviewer.download.active"]?.value)
        assertEquals(47L, response.metrics["anotherviewer.download.completed.total"]?.value)
        assertEquals("gauge", response.metrics["anotherviewer.download.active"]?.type)
        assertEquals("counter", response.metrics["anotherviewer.download.completed.total"]?.type)
    }

    @Test
    fun `metrics returns real values for memory size, completed processing and ws connections`() {
        `when`(imageCacheService.getMemorySizeBytes()).thenReturn(5242880L)
        `when`(processingService.getCompletedTaskCount()).thenReturn(17L)
        com.hippo.anotherviewer.web.config.WebSocketConfig.activeConnections.set(3)

        try {
            val response = controller.getMetrics()

            assertEquals(5242880L, response.metrics["anotherviewer.cache.memory.size.bytes"]?.value)
            assertEquals("gauge", response.metrics["anotherviewer.cache.memory.size.bytes"]?.type)
            assertEquals(17L, response.metrics["anotherviewer.process.completed.total"]?.value)
            assertEquals("counter", response.metrics["anotherviewer.process.completed.total"]?.type)
            assertEquals(3, response.metrics["anotherviewer.ws.connections.active"]?.value)
            assertEquals("gauge", response.metrics["anotherviewer.ws.connections.active"]?.type)
        } finally {
            com.hippo.anotherviewer.web.config.WebSocketConfig.activeConnections.set(0)
        }
    }

    @Test
    fun `dashboard returns complete structure`() {
        `when`(downloadService.getActiveDownloadCount()).thenReturn(2)
        `when`(downloadService.getCompletedDownloadCount()).thenReturn(47L)
        `when`(downloadService.getFailedDownloadCount()).thenReturn(3L)
        `when`(downloadService.getActiveDownloads()).thenReturn(
            listOf(
                DownloadItem(
                    id = 1, gid = 123456, token = "abc", title = "Test Gallery",
                    titleJpn = null, thumb = null, category = 0,
                    state = 2, total = 42, done = 17, label = 0, downloadDir = null
                )
            )
        )

        val response = controller.getDashboard()

        assertNotNull(response.timestamp)

        // Summary
        assertEquals("UP", response.summary.status)
        assertEquals(versionFromResource(), response.summary.version)
        assertTrue(response.summary.uptime.isNotEmpty())

        // Cache
        assertEquals(142, response.cache.memoryEntries)
        assertEquals(3221225472L, response.cache.diskUsedBytes)
        assertEquals(10737418240L, response.cache.diskMaxBytes)
        assertTrue(response.cache.diskUsagePercent > 0)
        assertEquals(0.87, response.cache.hitRatio)

        // Downloads
        assertEquals(2, response.downloads.active)
        assertEquals(47L, response.downloads.completedTotal)
        assertEquals(3L, response.downloads.failedTotal)
        assertEquals(1, response.downloads.activeTasks.size)
        assertEquals(123456L, response.downloads.activeTasks[0].galleryId)
        assertEquals("downloading", response.downloads.activeTasks[0].state)

        // Processing
        assertEquals(0, response.processing.queueSize)
        assertFalse(response.processing.processorAvailable)

        // WebSocket
        assertEquals(0, response.websocket.activeConnections)
    }

    @Test
    fun `dashboard download task progress is computed correctly`() {
        `when`(downloadService.getActiveDownloadCount()).thenReturn(1)
        `when`(downloadService.getActiveDownloads()).thenReturn(
            listOf(
                DownloadItem(
                    id = 1, gid = 999, token = "xyz", title = "Gallery",
                    titleJpn = null, thumb = null, category = 0,
                    state = 2, total = 100, done = 25, label = 0, downloadDir = null
                )
            )
        )

        val response = controller.getDashboard()
        val task = response.downloads.activeTasks[0]

        assertEquals(0.25, task.progress, 0.001)
        assertEquals(25, task.downloadedPages)
        assertEquals(100, task.totalPages)
    }

    @Test
    fun `dashboard limits active tasks to 10`() {
        `when`(downloadService.getActiveDownloadCount()).thenReturn(15)
        `when`(downloadService.getActiveDownloads()).thenReturn(
            (1..15).map { i ->
                DownloadItem(
                    id = i.toLong(), gid = i.toLong(), token = "t$i", title = "Gallery $i",
                    titleJpn = null, thumb = null, category = 0,
                    state = 2, total = 10, done = 5, label = 0, downloadDir = null
                )
            }
        )

        val response = controller.getDashboard()

        assertEquals(10, response.downloads.activeTasks.size)
    }
}
