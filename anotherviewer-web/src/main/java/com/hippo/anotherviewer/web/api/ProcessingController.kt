package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.dto.ProcessingRequest
import com.hippo.anotherviewer.web.dto.ProcessingStatus
import com.hippo.anotherviewer.web.dto.ProcessingTaskResponse
import com.hippo.anotherviewer.web.processing.ImageProcessingService
import com.hippo.anotherviewer.web.processing.ProcessingOptions
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST controller for the image processing pipeline.
 *
 * Endpoints:
 * - POST /api/v1/process/gallery/{id} — trigger enhancement for all pages
 * - GET /api/v1/process/status/{taskId} — query progress
 * - POST /api/v1/process/cancel/{taskId} — request cancellation
 *
 * See: contracts/openapi.yaml Processing tag, contracts/websocket-protocol.md §3.2
 * Error paths use the uniform error envelope (M-6).
 */
@RestController
@RequestMapping("/api/v1/process")
class ProcessingController(
    private val processingService: ImageProcessingService
) {

    @PostMapping("/gallery/{id}")
    fun triggerGalleryProcessing(
        @PathVariable id: Long,
        @Valid @RequestBody(required = false) request: ProcessingRequest?
    ): ResponseEntity<*> {
        val req = request ?: ProcessingRequest()
        val options = ProcessingOptions(
            type = req.type,
            outputFormat = req.outputFormat,
            quality = req.quality
        )

        return try {
            // Resolve the real page count from Gallery Site gallery metadata so the
            // whole gallery is processed, not a placeholder single page.
            val count = processingService.resolvePageCount(id)
                ?: return errorEnvelope(HttpStatus.NOT_FOUND, "NOT_FOUND", "Gallery not found")
            if (count <= 0) {
                return errorEnvelope(HttpStatus.NOT_FOUND, "NOT_FOUND", "Gallery not found")
            }
            val pages = 0 until count
            val taskId = processingService.submitGallery(id, pages, options)
            val status = processingService.getTaskStatus(taskId)!!

            ResponseEntity.ok(ProcessingTaskResponse(
                taskId = status.taskId,
                galleryId = status.galleryId,
                totalPages = status.totalPages,
                state = status.state
            ))
        } catch (e: IllegalStateException) {
            errorEnvelope(HttpStatus.CONFLICT, "CONFLICT", "Processing is already in progress for this gallery")
        }
    }

    @GetMapping("/status/{taskId}")
    fun getProcessingStatus(@PathVariable taskId: String): ResponseEntity<*> {
        val status = processingService.getTaskStatus(taskId)
            ?: return errorEnvelope(HttpStatus.NOT_FOUND, "NOT_FOUND", "Task not found")

        return ResponseEntity.ok(ProcessingStatus(
            taskId = status.taskId,
            galleryId = status.galleryId,
            state = status.state,
            totalPages = status.totalPages,
            processedPages = status.processedPages,
            failedPages = status.failedPages,
            currentPage = status.currentPage,
            startedAt = status.startedAt,
            completedAt = status.completedAt,
            error = status.error
        ))
    }

    @PostMapping("/cancel/{taskId}")
    fun cancelProcessing(@PathVariable taskId: String): ResponseEntity<*> {
        val cancelled = processingService.cancelTask(taskId)
        if (!cancelled) {
            return errorEnvelope(HttpStatus.NOT_FOUND, "NOT_FOUND", "Task not found")
        }
        return ResponseEntity.ok(mapOf("success" to true))
    }
}
