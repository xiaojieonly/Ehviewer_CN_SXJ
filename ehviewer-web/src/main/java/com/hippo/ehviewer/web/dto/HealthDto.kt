package com.hippo.ehviewer.web.dto

import com.fasterxml.jackson.annotation.JsonInclude

// ── Health ──────────────────────────────────────────────────────────

@JsonInclude(JsonInclude.Include.NON_NULL)
data class HealthResponse(
    val status: String,
    val components: Map<String, HealthComponent>,
    val version: String,
    val uptime: String,
    val uptimeMs: Long,
    val timestamp: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class HealthComponent(
    val status: String,
    // NOTE: openapi.yaml documents `details` as a single string (compact
    // JSON). The implementation intentionally keeps a structured map so
    // docker/curl healthchecks stay human-readable; the contract owner
    // should align openapi.yaml with the Map shape.
    val details: Map<String, String>? = null
)

// ── Metrics ─────────────────────────────────────────────────────────

@JsonInclude(JsonInclude.Include.NON_NULL)
data class MetricsResponse(
    val timestamp: String,
    val metrics: Map<String, MetricValue>
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class MetricValue(
    val type: String,
    val value: Any? = null,
    // Timer-specific fields
    val count: Long? = null,
    val totalMs: Long? = null,
    val meanMs: Double? = null,
    val maxMs: Long? = null
)

// ── Dashboard ───────────────────────────────────────────────────────

@JsonInclude(JsonInclude.Include.NON_NULL)
data class DashboardResponse(
    val timestamp: String,
    val summary: DashboardSummary,
    val cache: DashboardCache,
    val downloads: DashboardDownloads,
    val processing: DashboardProcessing,
    val websocket: DashboardWebSocket
)

data class DashboardSummary(
    val status: String,
    val uptime: String,
    val version: String
)

data class DashboardCache(
    val memoryEntries: Int,
    val memoryUsedBytes: Long,
    val memoryMaxBytes: Long,
    val memoryUsagePercent: Double,
    val diskUsedBytes: Long,
    val diskMaxBytes: Long,
    val diskUsagePercent: Double,
    val hitRatio: Double
)

data class DashboardDownloads(
    val active: Int,
    val completedTotal: Long,
    val failedTotal: Long,
    val activeTasks: List<DashboardDownloadTask>
)

data class DashboardDownloadTask(
    val taskId: Long,
    val galleryId: Long,
    val galleryTitle: String?,
    val state: String,
    val progress: Double,
    val downloadedPages: Int,
    val totalPages: Int
)

data class DashboardProcessing(
    val queueSize: Int,
    val completedTotal: Long,
    val processorAvailable: Boolean
)

data class DashboardWebSocket(
    val activeConnections: Int
)