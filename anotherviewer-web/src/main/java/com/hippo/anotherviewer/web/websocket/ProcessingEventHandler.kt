package com.hippo.anotherviewer.web.websocket

import com.hippo.anotherviewer.web.processing.ProcessingEvent
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

/**
 * Forwards [ProcessingEvent]s to STOMP topics following the frozen protocol
 * envelope (`{type, timestamp, version, payload}` — contracts/websocket-protocol.md §2):
 *
 * - `process.started` / `process.progress` / `process.completed` / `process.failed`
 *   on `/topic/process/{taskId}` and `/topic/process/all` (§3.2).
 * - `image.enhanced.ready` on `/topic/gallery/{galleryId}/enhanced` (§3.3) so
 *   the reader can hot-swap pages to their enhanced versions.
 */
@Component
class ProcessingEventHandler(private val messagingTemplate: SimpMessagingTemplate) {

    @EventListener
    fun handleProcessingEvent(event: ProcessingEvent) {
        when (event) {
            is ProcessingEvent.Started -> publishProcessEvent(
                event.taskId, "process.started", mapOf(
                    "taskId" to event.taskId,
                    "galleryId" to event.galleryId,
                    "totalPages" to event.totalPages,
                    "processingType" to event.processingType.name,
                    "processorId" to event.processorId
                )
            )

            is ProcessingEvent.Progress -> publishProcessEvent(
                event.taskId, "process.progress", mapOf(
                    "taskId" to event.taskId,
                    "galleryId" to event.galleryId,
                    "processedPages" to event.processedPages,
                    "totalPages" to event.totalPages,
                    "currentPage" to event.currentPage + 1 // contract §3.2: 1-based
                )
            )

            is ProcessingEvent.Completed -> publishProcessEvent(
                event.taskId, "process.completed", mapOf(
                    "taskId" to event.taskId,
                    "galleryId" to event.galleryId,
                    "enhancedPages" to event.enhancedPages,
                    "elapsedMs" to event.elapsedMs
                )
            )

            is ProcessingEvent.Failed -> publishProcessEvent(
                event.taskId, "process.failed", mapOf(
                    "taskId" to event.taskId,
                    "galleryId" to event.galleryId,
                    "error" to event.error,
                    "failedPage" to event.failedPage?.plus(1), // contract §3.2: 1-based
                    "processedBeforeFailure" to event.processedBeforeFailure
                )
            )

            is ProcessingEvent.EnhancedReady -> {
                val topic = "/topic/gallery/${event.galleryId}/enhanced"
                publish(topic, "image.enhanced.ready", mapOf(
                    "galleryId" to event.galleryId,
                    "page" to event.page + 1, // contract §3.3: 1-based
                    "enhancedUrl" to event.enhancedUrl,
                    "originalUrl" to event.originalUrl,
                    "processingType" to event.processingType.name,
                    "width" to event.width,
                    "height" to event.height,
                    "fileSize" to event.fileSize
                ))
            }
        }
    }

    private fun publishProcessEvent(taskId: String, type: String, payload: Map<String, Any?>) {
        publish("/topic/process/$taskId", type, payload)
        publish("/topic/process/all", type, payload)
    }

    private fun publish(topic: String, type: String, payload: Map<String, Any?>) {
        val envelope = mapOf(
            "type" to type,
            "timestamp" to System.currentTimeMillis(),
            "version" to "1.0",
            "payload" to payload
        )
        messagingTemplate.convertAndSend(topic, envelope)
    }
}
