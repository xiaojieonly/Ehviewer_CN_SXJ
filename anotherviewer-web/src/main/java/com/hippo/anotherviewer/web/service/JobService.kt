package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.dto.JobState
import com.hippo.anotherviewer.web.dto.JobType
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 统一后台任务中心（方案 plan-2026-08-06 §3）。
 *
 * - 提交即返回：同步部分（[submit] 的 prep 参数：落临时文件/读入参）在 HTTP 线程
 *   完成，重活（worker）进专用线程池，线程绑定语义保证 worker 内调用的
 *   `@Transactional` 业务方法照常生效。
 * - 并发护栏：每 [JobType] 同时只允许一个活跃（PENDING/RUNNING）任务，
 *   [ConcurrentHashMap.putIfAbsent] 原子防双提交；重复提交抛 IllegalStateException
 *   （控制器映射 409）。
 * - 进度：worker 经 [JobHandle] 上报（阶段/计数），事件按 500ms 节流推送，
 *   Job 字段本身每次上报都更新（读取端不受节流影响）。
 * - 生命周期事件经 ApplicationEventPublisher 发布（JobEventHandler 转 WS）。
 * - 终态清理：COMPLETED/FAILED 任务 1h 后惰性移除，注册表上限 50 条（超限丢最旧终态）。
 */
@Service
class JobService(
    private val store: JobStore,
    private val publisher: ApplicationEventPublisher,
) {
    private val logger = LoggerFactory.getLogger(JobService::class.java)

    private val executor = Executors.newFixedThreadPool(POOL_SIZE) { r ->
        Thread(r, "job-worker").apply { isDaemon = true }
    }

    /** type → 活跃 jobId（原子护栏）。 */
    private val active = ConcurrentHashMap<JobType, String>()

    /** 进度事件节流：jobId → 上次推送时间戳。 */
    private val lastPublish = ConcurrentHashMap<String, Long>()

    /**
     * 提交任务。
     *
     * @param prep 同步准备（落盘/校验等），异常直接向上抛（控制器转错误信封，不建任务）
     * @param worker 后台执行体（在线程池线程上运行）
     * @throws IllegalStateException 同 type 已有活跃任务（控制器映射 409）
     */
    fun submit(type: JobType, prep: () -> Unit, worker: (JobHandle) -> Unit): Job {
        evictFinishedIfNeeded()
        val job = Job(jobId = "job-${UUID.randomUUID().toString().take(8)}", type = type)
        val taken = active.putIfAbsent(type, job.jobId)
        if (taken != null) {
            throw IllegalStateException("已有 ${type} 任务进行中")
        }
        try {
            prep()
        } catch (e: Exception) {
            active.remove(type, job.jobId)
            throw e
        }
        store.save(job)
        publisher.publishEvent(JobEvent.Started(job.jobId, type, "已提交", 0L))

        executor.execute {
            try {
                job.state = JobState.RUNNING
                job.startedAt = System.currentTimeMillis()
                worker(JobHandleImpl(job))
                job.state = JobState.COMPLETED
                job.completedAt = System.currentTimeMillis()
                publisher.publishEvent(JobEvent.Completed(job.jobId, type, job.result))
                logger.info("Job 完成: jobId={}, type={}", job.jobId, type)
            } catch (e: Exception) {
                job.state = JobState.FAILED
                job.completedAt = System.currentTimeMillis()
                job.error = e.message ?: e.javaClass.simpleName
                publisher.publishEvent(JobEvent.Failed(job.jobId, type, job.error!!))
                logger.warn("Job 失败: jobId={}, type={}, error={}", job.jobId, type, e.message)
            } finally {
                active.remove(type, job.jobId)
                lastPublish.remove(job.jobId)
            }
        }
        return job
    }

    fun getJob(jobId: String): Job? = store.find(jobId)

    fun activeJob(type: JobType): Job? = active[type]?.let(store::find)

    /** worker 上报句柄：progress 报阶段+计数，stage 仅改阶段文案。 */
    interface JobHandle {
        fun progress(stage: String, processed: Long, total: Long)

        /** 仅更新阶段文案（计数不变）。 */
        fun stage(stage: String)
    }

    /** 终态清理：TTL 1h + 上限 50 条（懒执行，提交时触发）。internal 供测试直接调用。 */
    internal fun evictFinishedIfNeeded() {
        val now = System.currentTimeMillis()
        val terminal = store.list().filter { it.state == JobState.COMPLETED || it.state == JobState.FAILED }
        terminal
            .filter { it.completedAt != null && now - it.completedAt!! > TTL_MS }
            .forEach { store.remove(it.jobId) }
        val remainingTerminal = store.list().filter { it.state == JobState.COMPLETED || it.state == JobState.FAILED }
        if (remainingTerminal.size > MAX_JOBS) {
            remainingTerminal
                .sortedBy { it.completedAt ?: 0L }
                .take(remainingTerminal.size - MAX_JOBS)
                .forEach { store.remove(it.jobId) }
        }
    }

    private inner class JobHandleImpl(private val job: Job) : JobHandle {
        override fun progress(stage: String, processed: Long, total: Long) {
            job.stage = stage
            job.processed = processed
            job.total = total
            publishThrottled(job, stage, processed, total)
        }

        override fun stage(stage: String) {
            job.stage = stage
            publishThrottled(job, stage, job.processed, job.total)
        }
    }

    private fun publishThrottled(job: Job, stage: String, processed: Long, total: Long) {
        val now = System.currentTimeMillis()
        val last = lastPublish[job.jobId] ?: 0L
        if (now - last < PUBLISH_INTERVAL_MS) return
        lastPublish[job.jobId] = now
        val percent = if (total <= 0) 0.0 else (processed.toDouble() / total * 100).coerceIn(0.0, 100.0)
        publisher.publishEvent(JobEvent.Progress(job.jobId, job.type, stage, percent, processed, total))
    }

    private companion object {
        const val POOL_SIZE = 2
        const val PUBLISH_INTERVAL_MS = 500L
        const val TTL_MS = 60 * 60 * 1000L
        const val MAX_JOBS = 50
    }
}
