package com.hippo.ehviewer.web.dto

import com.hippo.ehviewer.web.processing.ProcessingType
import com.hippo.ehviewer.web.processing.TaskState
import java.time.Instant

/**
 * Request body for POST /api/v1/process/gallery/{id}
 * See: contracts/openapi.yaml ProcessingRequest schema
 */
data class ProcessingRequest(
    val type: ProcessingType = ProcessingType.UPSCALE_2X,
    val outputFormat: String = "png",
    val quality: Int = 90
)

/**
 * Response for POST /api/v1/process/gallery/{id}
 * See: contracts/openapi.yaml ProcessingTaskResponse schema
 */
data class ProcessingTaskResponse(
    val taskId: String,
    val galleryId: Long,
    val totalPages: Int,
    val state: TaskState
)

/**
 * Response for GET /api/v1/process/status/{taskId}
 * See: contracts/openapi.yaml ProcessingStatus schema
 */
data class ProcessingStatus(
    val taskId: String,
    val galleryId: Long,
    val state: TaskState,
    val totalPages: Int,
    val processedPages: Int,
    val failedPages: Int,
    val currentPage: Int,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val error: String?
)
