package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.ProcessingRequest
import com.hippo.ehviewer.web.dto.ProcessingStatus
import com.hippo.ehviewer.web.dto.ProcessingTaskResponse
import com.hippo.ehviewer.web.processing.ImageProcessingService
import com.hippo.ehviewer.web.processing.ProcessingOptions
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST controller for the image processing pipeline.
 *
 * Endpoints:
 * - POST /api/v1/process/gallery/{id} — trigger enhancement
 * - GET /api/v1/process/status/{taskId} — query progress
 *
 * See: contracts/openapi.yaml Processing tag
 */
@RestController
@RequestMapping("/api/v1/process")
class ProcessingController(
    private val processingService: ImageProcessingService
) {

    @PostMapping("/gallery/{id}")
    fun triggerGalleryProcessing(
        @PathVariable id: Long,
        @RequestBody(required = false) request: ProcessingRequest?
    ): ResponseEntity<ProcessingTaskResponse> {
        val req = request ?: ProcessingRequest()
        val options = ProcessingOptions(
            type = req.type,
            outputFormat = req.outputFormat,
            quality = req.quality
        )

        return try {
            // TODO: resolve actual page count from gallery metadata; default to 1..1 for now
            val pages = 0..0
            val taskId = processingService.submitGallery(id, pages, options)
            val status = processingService.getTaskStatus(taskId)!!

            ResponseEntity.ok(ProcessingTaskResponse(
                taskId = status.taskId,
                galleryId = status.galleryId,
                totalPages = status.totalPages,
                state = status.state
            ))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(409).build()
        }
    }

    @GetMapping("/status/{taskId}")
    fun getProcessingStatus(@PathVariable taskId: String): ResponseEntity<ProcessingStatus> {
        val status = processingService.getTaskStatus(taskId)
            ?: return ResponseEntity.notFound().build()

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
}
