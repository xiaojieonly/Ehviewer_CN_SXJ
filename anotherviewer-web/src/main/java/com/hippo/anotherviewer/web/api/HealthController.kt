package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.HealthComponent
import com.hippo.anotherviewer.web.dto.HealthResponse
import com.hippo.anotherviewer.web.service.EhAvailabilityService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.lang.management.ManagementFactory
import java.time.Instant
import java.util.Properties
import javax.sql.DataSource

@RestController
@RequestMapping("/api/v1/health")
class HealthController(
    private val dataSource: DataSource,
    private val config: SiteCoreConfigProperties,
    private val availability: EhAvailabilityService
) {
    private val logger = LoggerFactory.getLogger(HealthController::class.java)

    companion object {
        private const val GALLERY_CHECK_INTERVAL_MS = 60_000L
        private const val DISK_MIN_FREE_BYTES = 100L * 1024 * 1024 // 100 MB

        /**
         * Status aggregation per observability.md §2.3 (rev. 1.1).
         *
         * `galleryApi` is informational-only: E-Hentai reachability does not
         * affect the server's core business, so a galleryApi DOWN is excluded
         * from aggregation (overall stays UP instead of degrading to
         * DEGRADED). Other optional components (e.g. a future `waifu2x`
         * probe) remain aggregating and do trigger DEGRADED when DOWN.
         */
        internal fun computeOverallStatus(components: Map<String, HealthComponent>): String {
            val requiredUp = components["database"]?.status == "UP" &&
                components["diskCache"]?.status == "UP"
            val aggregatingOptionalDown = components.entries.any { (name, component) ->
                name != "galleryApi" && component.status == "DOWN"
            }
            return when {
                !requiredUp -> "DOWN"
                aggregatingOptionalDown -> "DEGRADED"
                else -> "UP"
            }
        }

        // Loaded from /version.properties, which the generateVersionProperties
        // Gradle task writes from the webVersion project property
        // (gradle.properties: webVersion=1.1.0, override via -PwebVersion=),
        // so the reported version always matches the built jar. The fallback
        // string only appears in dev/IDE runs where processResources hasn't
        // produced the resource (e.g. direct unit-test runs in an IDE).
        private val VERSION: String = run {
            val stream = HealthController::class.java.getResourceAsStream("/version.properties")
                ?: return@run "1.0.0-SNAPSHOT"
            stream.use { input ->
                Properties().apply { load(input) }.getProperty("anotherviewer.version")
                    ?: "1.0.0-SNAPSHOT"
            }
        }
    }

    @GetMapping
    fun healthCheck(): ResponseEntity<HealthResponse> {
        val components = mutableMapOf<String, HealthComponent>()

        // Database check (required)
        components["database"] = checkDatabase()

        // Disk cache check (required)
        components["diskCache"] = checkDiskCache()

        // Gallery Site API check (optional)
        components["galleryApi"] = checkGalleryApi()

        // Determine overall status (see computeOverallStatus).
        val overallStatus = computeOverallStatus(components)

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
                        "freeSpaceBytes" to freeSpace.toString(),
                        "path" to cacheDir.absolutePath
                    )
                )
            }
            HealthComponent(
                status = "UP",
                details = mapOf(
                    "freeSpace" to formatBytes(freeSpace),
                    "freeSpaceBytes" to freeSpace.toString(),
                    "path" to cacheDir.absolutePath
                )
            )
        } catch (e: Exception) {
            logger.warn("Disk cache health check failed", e)
            HealthComponent(status = "DOWN", details = mapOf("reason" to (e.message ?: "unknown error")))
        }
    }

    /**
     * Gallery Site reachability (informational component) is delegated to
     * [EhAvailabilityService]: no HTTP happens on this request thread beyond
     * the probe. Result is cached for [GALLERY_CHECK_INTERVAL_MS] since the
     * last actual probe; an older entry (or none) triggers ONE manual probe
     * (single-flighted, probe-timeout bounded). See observability.md §2.3 —
     * galleryApi DOWN never degrades the overall status.
     */
    private fun checkGalleryApi(): HealthComponent {
        val now = System.currentTimeMillis()
        val status = availability.status()
        val lastProbeAt = status.lastProbeAt ?: 0L
        if (lastProbeAt > 0 && now - lastProbeAt < GALLERY_CHECK_INTERVAL_MS) {
            return HealthComponent(
                status = galleryStatusString(status.state),
                details = mapOf(
                    "lastCheck" to Instant.ofEpochMilli(lastProbeAt).toString(),
                    "cached" to "true"
                )
            )
        }

        // probeNow() records success/failure into the state machine and returns
        // the outcome: true = UP (probe succeeded or the site is not blocked).
        availability.probeNow()
        val fresh = availability.status()
        val freshProbeAt = fresh.lastProbeAt ?: now
        val details = mutableMapOf<String, String>(
            "lastCheck" to Instant.ofEpochMilli(freshProbeAt).toString()
        )
        fresh.lastReason?.let { details["reason"] = it }
        return HealthComponent(
            status = galleryStatusString(fresh.state),
            details = details
        )
    }

    /** UNKNOWN/UP are informational "UP" for health; DOWN stays DOWN. */
    private fun galleryStatusString(state: String): String =
        if (state == EhAvailabilityService.State.DOWN.name) "DOWN" else "UP"

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
