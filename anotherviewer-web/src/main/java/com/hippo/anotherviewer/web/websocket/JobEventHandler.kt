package com.hippo.anotherviewer.web.websocket

import com.hippo.anotherviewer.web.service.JobEvent
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

/**
 * Forwards [JobEvent]s to STOMP topics (方案 plan-2026-08-06 附录 A3，
 * websocket-protocol.md v1.1.0 job.* 事件族）:
 *
 * - `job.started` / `job.progress` / `job.completed` / `job.failed`
 *   on `/topic/jobs/{jobId}` and `/topic/jobs/all`.
 *
 * Envelope 沿用冻结信封 `{type, timestamp, version, payload}`，version 为 "1.1"
 * （新事件族按语义化版本提前一个 MINOR 宣布，既有 process./download. 事件不变）。
 */
@Component
class JobEventHandler(private val messagingTemplate: SimpMessagingTemplate) {

    @EventListener
    fun handleJobEvent(event: JobEvent) {
        when (event) {
            is JobEvent.Started -> publish(
                event.jobId, "job.started", mapOf(
                    "jobId" to event.jobId,
                    "type" to event.type.name,
                    "stage" to event.stage,
                    "total" to event.total,
                )
            )

            is JobEvent.Progress -> publish(
                event.jobId, "job.progress", mapOf(
                    "jobId" to event.jobId,
                    "type" to event.type.name,
                    "stage" to event.stage,
                    "percent" to event.percent,
                    "processed" to event.processed,
                    "total" to event.total,
                )
            )

            is JobEvent.Completed -> publish(
                event.jobId, "job.completed", mapOf(
                    "jobId" to event.jobId,
                    "type" to event.type.name,
                    "result" to event.result,
                )
            )

            is JobEvent.Failed -> publish(
                event.jobId, "job.failed", mapOf(
                    "jobId" to event.jobId,
                    "type" to event.type.name,
                    "error" to event.error,
                )
            )
        }
    }

    private fun publish(jobId: String, type: String, payload: Map<String, Any?>) {
        val envelope = mapOf(
            "type" to type,
            "timestamp" to System.currentTimeMillis(),
            "version" to "1.1",
            "payload" to payload
        )
        messagingTemplate.convertAndSend("/topic/jobs/$jobId", envelope)
        messagingTemplate.convertAndSend("/topic/jobs/all", envelope)
    }
}
