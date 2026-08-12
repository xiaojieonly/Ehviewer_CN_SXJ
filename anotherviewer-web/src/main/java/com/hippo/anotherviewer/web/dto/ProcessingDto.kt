package com.hippo.anotherviewer.web.dto

import com.hippo.anotherviewer.web.processing.ProcessingType
import com.hippo.anotherviewer.web.processing.TaskState
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

/**
 * Request body for POST /api/v1/process/gallery/{id}
 * See: contracts/openapi.yaml ProcessingRequest schema
 */
data class ProcessingRequest(
    val type: ProcessingType = ProcessingType.UPSCALE_2X,
    @field:Size(max = 16, message = "outputFormat must be at most 16 characters")
    @field:Pattern(regexp = "^(?i)(png|jpe?g|webp)$", message = "outputFormat must be png, jpg, jpeg or webp")
    val outputFormat: String = "png",
    @field:Min(1, message = "quality must be between 1 and 100")
    @field:Max(100, message = "quality must be between 1 and 100")
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
