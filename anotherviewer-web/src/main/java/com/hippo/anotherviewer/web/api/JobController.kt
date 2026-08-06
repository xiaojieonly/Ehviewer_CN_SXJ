package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.dto.JobDto
import com.hippo.anotherviewer.web.dto.JobType
import com.hippo.anotherviewer.web.dto.toDto
import com.hippo.anotherviewer.web.service.JobService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 统一任务查询端点（方案 plan-2026-08-06 附录 A2）。
 *
 * 异步提交端点（import-ehviewer / export/async / restore / cache/clear）返回
 * 202 + jobId 后，前端经 WS 订阅 `job.*` 事件获得实时进度；页面刷新/导航离开后
 * 用本控制器的两个端点重挂：GET /jobs/{jobId} 按 id 查（含终态 result），
 * GET /jobs/active?type= 查当前活跃任务。
 */
@RestController
@RequestMapping("/api/v1/jobs")
class JobController(private val jobService: JobService) {

    @GetMapping("/{jobId}")
    fun getJob(@PathVariable jobId: String): ResponseEntity<*> {
        val job = jobService.getJob(jobId)
            ?: return errorEnvelope(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "任务不存在: $jobId")
        return ResponseEntity.ok(job.toDto())
    }

    @GetMapping("/active")
    fun activeJob(@RequestParam type: JobType): ResponseEntity<*> {
        val job = jobService.activeJob(type)
            ?: return errorEnvelope(HttpStatus.NOT_FOUND, "NO_ACTIVE_JOB", "当前没有进行中的 ${type} 任务")
        return ResponseEntity.ok(job.toDto())
    }
}
