package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.config.EhCoreConfigProperties
import com.hippo.ehviewer.web.dto.HealthComponent
import com.hippo.ehviewer.web.dto.HealthResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.lang.management.ManagementFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import javax.sql.DataSource

@RestController
@RequestMapping("/api/v1/health")
class HealthController(
    private val dataSource: DataSource,
    private val config: EhCoreConfigProperties
) {
    private val logger = LoggerFactory.getLogger(HealthController::class.java)

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    // Cached E-Hentai check result
    @Volatile
    private var ehentaiLastCheck: Long = 0
    @Volatile
    private var ehentaiLastStatus: String = "UNKNOWN"
    @Volatile
    private var ehentaiLastResponseMs: Long = 0

    companion object {
        private const val EHENTAI_CHECK_INTERVAL_MS = 60_000L
        private const val DISK_MIN_FREE_BYTES = 100L * 1024 * 1024 // 100 MB
        private const val VERSION = "1.0.0-SNAPSHOT"
    }

    @GetMapping
    fun healthCheck(): ResponseEntity<HealthResponse> {
        val components = mutableMapOf<String, HealthComponent>()

        // Database check (required)
        components["database"] = checkDatabase()

        // Disk cache check (required)
        components["diskCache"] = checkDiskCache()

        // E-Hentai API check (optional)
        components["ehentaiApi"] = checkEhentaiApi()

        // Determine overall status
        val requiredUp = components["database"]?.status == "UP" &&
            components["diskCache"]?.status == "UP"
        val optionalDown = components.values.any { it.status == "DOWN" }

        val overallStatus = when {
            !requiredUp -> "DOWN"
            optionalDown -> "DEGRADED"
            else -> "UP"
        }

        val uptimeMs = ManagementFactory.getRuntimeMXBean().uptime
        val response = HealthResponse(
            status = overallStatus,
            components = components,
            version = VERSION,
            uptime = formatUptime(uptimeMs),
            uptimeMs = uptimeMs,
            timestamp = Instant.now().toString()
        )

        val httpStatus = if (overallStatus == "DOWN") 503 else 200
        return ResponseEntity.status(httpStatus).body(response)
    }

    private fun checkDatabase(): HealthComponent {
        return try {
            dataSource.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT 1").use { rs ->
                        if (rs.next()) {
                            HealthComponent(
                                status = "UP",
                                details = mapOf("type" to "sqlite")
                            )
                        } else {
                            HealthComponent(status = "DOWN", details = mapOf("reason" to "unexpected query result"))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Database health check failed", e)
            HealthComponent(status = "DOWN", details = mapOf("reason" to (e.message ?: "unknown error")))
        }
    }

    private fun checkDiskCache(): HealthComponent {
        return try {
            val cacheDir = File(config.download.cachePath)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            if (!cacheDir.canWrite()) {
                return HealthComponent(
                    status = "DOWN",
                    details = mapOf("reason" to "cache directory not writable", "path" to cacheDir.absolutePath)
                )
            }
            val freeSpace = cacheDir.usableSpace
            if (freeSpace < DISK_MIN_FREE_BYTES) {
                return HealthComponent(
                    status = "DOWN",
                    details = mapOf(
                        "reason" to "insufficient free space",
                        "freeSpace" to formatBytes(freeSpace),
                        "freeSpaceBytes" to freeSpace,
                        "path" to cacheDir.absolutePath
                    )
                )
            }
            HealthComponent(
                status = "UP",
                details = mapOf(
                    "freeSpace" to formatBytes(freeSpace),
                    "freeSpaceBytes" to freeSpace,
                    "path" to cacheDir.absolutePath
                )
            )
        } catch (e: Exception) {
            logger.warn("Disk cache health check failed", e)
            HealthComponent(status = "DOWN", details = mapOf("reason" to (e.message ?: "unknown error")))
        }
    }

    private fun checkEhentaiApi(): HealthComponent {
        val now = System.currentTimeMillis()
        if (now - ehentaiLastCheck < EHENTAI_CHECK_INTERVAL_MS && ehentaiLastCheck > 0) {
            return HealthComponent(
                status = ehentaiLastStatus,
                details = mapOf(
                    "lastCheck" to Instant.ofEpochMilli(ehentaiLastCheck).toString(),
                    "responseTimeMs" to ehentaiLastResponseMs,
                    "cached" to true
                )
            )
        }

        return try {
            val start = System.currentTimeMillis()
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://e-hentai.org"))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                // Same fingerprint as the app's ChromeRequestBuilder / core EhRequestBuilder.
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36")
                .timeout(Duration.ofSeconds(10))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
            val elapsed = System.currentTimeMillis() - start

            ehentaiLastCheck = now
            ehentaiLastResponseMs = elapsed

            if (response.statusCode() in 200..399) {
                ehentaiLastStatus = "UP"
                HealthComponent(
                    status = "UP",
                    details = mapOf(
                        "lastCheck" to Instant.ofEpochMilli(now).toString(),
                        "responseTimeMs" to elapsed
                    )
                )
            } else {
                ehentaiLastStatus = "DOWN"
                HealthComponent(
                    status = "DOWN",
                    details = mapOf(
                        "lastCheck" to Instant.ofEpochMilli(now).toString(),
                        "statusCode" to response.statusCode()
                    )
                )
            }
        } catch (e: Exception) {
            logger.warn("E-Hentai API health check failed", e)
            ehentaiLastCheck = now
            ehentaiLastStatus = "DOWN"
            HealthComponent(
                status = "DOWN",
                details = mapOf("reason" to (e.message ?: "connection failed"))
            )
        }
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

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
