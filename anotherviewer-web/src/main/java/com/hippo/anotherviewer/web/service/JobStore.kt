package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.dto.JobState
import com.hippo.anotherviewer.web.dto.JobType
import java.util.concurrent.ConcurrentHashMap

/**
 * 统一后台任务实体（方案 plan-2026-08-06 附录 A1）。
 *
 * 单写者模型：worker 线程独占写；读取方（GET 端点/JobController）经 @Volatile
 * 读字段，DTO 映射时按快照取值，不要求强一致性。
 */
class Job(
    val jobId: String,
    val type: JobType,
) {
    @Volatile
    var state: JobState = JobState.PENDING

    @Volatile
    var stage: String? = null

    @Volatile
    var processed: Long = 0L

    @Volatile
    var total: Long = 0L

    @Volatile
    var startedAt: Long? = null

    @Volatile
    var completedAt: Long? = null

    @Volatile
    var error: String? = null

    @Volatile
    var result: Any? = null
}

/**
 * 任务存储抽象：本次用内存实现；未来持久任务（下载类）经 [JpaJobStore] 接入
 * 可跨进程恢复——本接口即预留的扩展缝（方案 §3）。
 */
interface JobStore {
    fun save(job: Job)
    fun find(jobId: String): Job?
    fun remove(jobId: String)
    fun list(): List<Job>
}

/** 内存实现：ConcurrentHashMap，无 TTL——终态任务清理由 JobService 惰性执行。 */
class InMemoryJobStore : JobStore {
    private val jobs = ConcurrentHashMap<String, Job>()

    override fun save(job: Job) {
        jobs[job.jobId] = job
    }

    override fun find(jobId: String): Job? = jobs[jobId]

    override fun remove(jobId: String) {
        jobs.remove(jobId)
    }

    override fun list(): List<Job> = jobs.values.toList()
}
