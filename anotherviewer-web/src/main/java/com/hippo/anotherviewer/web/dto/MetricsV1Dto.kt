package com.hippo.anotherviewer.web.dto

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Flat /api/v1/metrics response per contracts/openapi.yaml (MetricsResponse):
 * uptimeSeconds, jvmMemoryUsedBytes, jvmMemoryMaxBytes, activeDownloads,
 * queuedProcessingTasks, diskCacheUsedBytes, diskCacheMaxBytes,
 * totalGalleriesServed.
 *
 * The legacy `metrics` map (contracts/observability.md §3.2) is retained for
 * backward compatibility with existing consumers.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MetricsV1Response(
    val timestamp: String,
    val uptimeSeconds: Long,
    val jvmMemoryUsedBytes: Long,
    val jvmMemoryMaxBytes: Long,
    val activeDownloads: Int,
    val queuedProcessingTasks: Int,
    val diskCacheUsedBytes: Long,
    val diskCacheMaxBytes: Long,
    val totalGalleriesServed: Long,
    val metrics: Map<String, MetricValue>
)
