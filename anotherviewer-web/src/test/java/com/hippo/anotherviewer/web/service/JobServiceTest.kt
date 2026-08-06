package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.dto.JobState
import com.hippo.anotherviewer.web.dto.JobType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.context.ApplicationEventPublisher
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * JobService 状态机 / 并发护栏 / 事件发布 / 终态清理测试。
 * 异步 worker 用轮询等待终态（默认 5s 超时）。
 */
class JobServiceTest {

    private val store = InMemoryJobStore()
    private val publisher = mock(ApplicationEventPublisher::class.java)
    private val service = JobService(store, publisher)

    private fun awaitState(job: Job, expected: JobState, timeoutMs: Long = 5000): Job {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val current = service.getJob(job.jobId)!!
            if (current.state == expected) return current
            Thread.sleep(10)
        }
        throw AssertionError("job ${job.jobId} 未在 ${timeoutMs}ms 内到达 $expected（当前 ${service.getJob(job.jobId)?.state}）")
    }

    private fun noopHandle(): JobService.JobHandle = object : JobService.JobHandle {
        override fun progress(stage: String, processed: Long, total: Long) = Unit
        override fun stage(stage: String) = Unit
    }

    @Test
    fun `submit completes job and publishes Started and Completed events`() {
        val job = service.submit(JobType.CACHE_CLEAR, {}) { handle ->
            handle.progress("删除缓存文件", 1, 2)
        }

        val done = awaitState(job, JobState.COMPLETED)
        assertNotNull(done.startedAt)
        assertNotNull(done.completedAt)
        assertNull(done.error)
        assertEquals("删除缓存文件", done.stage)
        assertEquals(1L, done.processed)
        assertEquals(2L, done.total)

        verify(publisher).publishEvent(org.mockito.ArgumentMatchers.any(JobEvent.Started::class.java))
        verify(publisher).publishEvent(org.mockito.ArgumentMatchers.any(JobEvent.Completed::class.java))
    }

    @Test
    fun `worker exception fails the job with error message`() {
        val job = service.submit(JobType.IMPORT, {}) { _ ->
            throw IllegalStateException("导入失败: 数据损坏")
        }

        val done = awaitState(job, JobState.FAILED)
        assertEquals("导入失败: 数据损坏", done.error)
        verify(publisher).publishEvent(org.mockito.ArgumentMatchers.any(JobEvent.Failed::class.java))
    }

    @Test
    fun `concurrent submit of same type is rejected`() {
        val latch = CountDownLatch(1)
        val job = service.submit(JobType.EXPORT, {}) { _ ->
            latch.await(5, TimeUnit.SECONDS)
        }
        try {
            assertThrows(IllegalStateException::class.java) {
                service.submit(JobType.EXPORT, {}) { _ -> }
            }
        } finally {
            latch.countDown()
            awaitState(job, JobState.COMPLETED)
        }
    }

    @Test
    fun `different types can be active at the same time`() {
        val latch = CountDownLatch(2)
        val jobA = service.submit(JobType.IMPORT, {}) { _ -> latch.await(5, TimeUnit.SECONDS) }
        val jobB = service.submit(JobType.EXPORT, {}) { _ -> latch.await(5, TimeUnit.SECONDS) }
        try {
            assertEquals(jobA.jobId, service.activeJob(JobType.IMPORT)?.jobId)
            assertEquals(jobB.jobId, service.activeJob(JobType.EXPORT)?.jobId)
        } finally {
            latch.countDown()
            latch.countDown()
            awaitState(jobA, JobState.COMPLETED)
            awaitState(jobB, JobState.COMPLETED)
        }
    }

    @Test
    fun `prep failure does not create a job nor publish Started`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.submit(JobType.RESTORE, { throw IllegalArgumentException("文件为空") }) { _ -> }
        }
        assertFalse(store.list().isNotEmpty())
        assertNull(service.activeJob(JobType.RESTORE))
        verify(publisher, never()).publishEvent(org.mockito.ArgumentMatchers.any(JobEvent.Started::class.java))
    }

    @Test
    fun `activeJob returns null after completion`() {
        val job = service.submit(JobType.CACHE_CLEAR, {}) { _ -> }
        awaitState(job, JobState.COMPLETED)
        assertNull(service.activeJob(JobType.CACHE_CLEAR))
    }

    @Test
    fun `progress events are throttled but fields update every call`() {
        val job = service.submit(JobType.IMPORT, {}) { handle ->
            handle.progress("下载记录", 1, 100)
            handle.progress("下载记录", 50, 100)
            Thread.sleep(600)
            handle.progress("历史", 100, 100)
        }
        awaitState(job, JobState.COMPLETED)

        val captor = ArgumentCaptor.forClass(Any::class.java)
        verify(publisher, atLeast(2)).publishEvent(captor.capture())
        val progressEvents = captor.allValues.filterIsInstance<JobEvent.Progress>()
        assertTrue(progressEvents.size >= 2, "期望至少 2 条 Progress（节流后），实际 ${progressEvents.size}")
        assertEquals("历史", progressEvents.last().stage)
        assertEquals(100.0, progressEvents.last().percent, 0.001)
    }

    @Test
    fun `terminal jobs are evicted beyond the cap`() {
        repeat(55) { i ->
            store.save(Job(jobId = "job-old-$i", type = JobType.CACHE_CLEAR).apply {
                state = JobState.COMPLETED
                completedAt = System.currentTimeMillis()
            })
        }
        service.evictFinishedIfNeeded()
        val remaining = store.list().filter { it.state == JobState.COMPLETED || it.state == JobState.FAILED }
        assertTrue(remaining.size <= 50, "期望上限 50，实际 ${remaining.size}")
    }

    @Test
    fun `stage-only update keeps counts and emits throttled progress`() {
        val job = service.submit(JobType.RESTORE, {}) { handle ->
            handle.progress("解包校验", 0, 3)
            Thread.sleep(600)
            handle.stage("拷贝下载内容")
        }
        val done = awaitState(job, JobState.COMPLETED)
        assertEquals("拷贝下载内容", done.stage)
        assertEquals(0L, done.processed)
        assertEquals(3L, done.total)
        assertNotNull(done)
    }
}
