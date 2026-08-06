package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.dto.JobType

/**
 * Job 生命周期事件（方案 plan-2026-08-06 附录 A3）。
 *
 * Started/Progress/Completed/Failed 经 JobEventHandler 转发为 STOMP
 * `/topic/jobs/{jobId}` 与 `/topic/jobs/all` 上的 `job.*` 事件。
 * [Progress.percent] 由 worker 端按 processed/total 计算。
 */
sealed class JobEvent {
    data class Started(
        val jobId: String,
        val type: JobType,
        val stage: String?,
        val total: Long,
    ) : JobEvent()

    data class Progress(
        val jobId: String,
        val type: JobType,
        val stage: String?,
        val percent: Double,
        val processed: Long,
        val total: Long,
    ) : JobEvent()

    data class Completed(
        val jobId: String,
        val type: JobType,
        val result: Any?,
    ) : JobEvent()

    data class Failed(
        val jobId: String,
        val type: JobType,
        val error: String,
    ) : JobEvent()
}
