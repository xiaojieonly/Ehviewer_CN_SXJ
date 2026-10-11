package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * P-S8 下载进度批量落库器（进度批量+节流 Wave 1）。
 *
 * 下载页级进度原先每页一次 `findById + save`（每页双 SQL，[DownloadService.persistProgress]
 * 旧实现）——批量后页 worker 只做一次内存累计（[record]，无锁 map put），由单线程
 * 调度器每 [intervalMs] 合并落库：每任务仍走 `findById + save` + 原守卫（墓碑行跳过、
 * 暂停 0/失败 4 行不写——**守卫语义与旧 persistProgress 逐字一致**），但每秒每任务至多
 * 一次 SQL 对。lastModified 取 flush 时刻（同步水位照旧 bump，App 增量 pull 可见）。
 *
 * 强制 flush 语义（调用方 [DownloadService] 保证）：
 * - 任务终态（runDownload 的 stop/complete/incomplete 三分支、executeDownload 的异常
 *   FAILED）、用户取消（cancelDownload）——**先 [flush] 再写终态行**：worker 线程此
 *   刻已全部退出（或即将退出），flush 是「该 id 此后再无批量写」的屏障，终态
 *   updateEntity 因此绝无并发 flush 竞争；
 * - 用户暂停（pauseDownload）——updateEntity 包在 [locked] 里：在途 flush 先完成
 *   （flush 加载的是暂停前状态、合法写 done），暂停写随后落地，状态绝不被旧帧复活；
 * - 画廊完成化（completeIfVerified）——flush 后再写 state=3；
 * - 删除（deleteDownload）——墓碑化前 [discard] 丢弃待写进度（行都要删了，进度无意义，
 *   且 flush 的守卫也本会跳过墓碑）；
 * - 关停（[DownloadService.destroy] / [destroy]）——[flushAll] 立即落全部待写进度。
 *
 * 崩溃丢更新窗口：至多 [intervalMs]（1s）内的内存进度（受 [runCatching] 兜底，单次
 * 落库失败也只丢当秒窗口）。对断点续传无正确性影响（done 只影响续传起点/展示，
 * 磁盘页文件才是真相）。
 *
 * 可测性：调度器只在 Spring 容器 [afterPropertiesSet] 时启动——直构单测不触达后台
 * tick，直接调用 [flush]/[flushAll] 即可确定性断言；[clock] 注入可断言 lastModified
 * 取 flush 时刻。
 */
@Component
class DownloadProgressPersister(
    private val downloadRepository: DownloadInfoRepository,
    /** 可注入时钟（单测断言 lastModified 取 flush 时刻）。 */
    private val clock: () -> Long = System::currentTimeMillis,
    /** 合并落库周期（毫秒）。 */
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    /** 可注入调度器（默认单守护线程；单测可传已 shutdown 的实现禁用后台 tick）。 */
    private val scheduler: ScheduledExecutorService? = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "download-progress-persister").apply { isDaemon = true }
    },
    // v4（WP2）：进度写的水位走服务器打戳时钟（可空缺省保留 [clock] 测试缝：null 时
    // 回退原 clock()，既有直构测试的注入时钟断言不变）。
    private val syncClock: SyncClock? = null,
) : InitializingBean, DisposableBean {

    private val logger = LoggerFactory.getLogger(DownloadProgressPersister::class.java)

    /** taskId → 最新 done（put 覆盖旧值 = 同周期多页只留最新，正是合帧语义）。 */
    private val pending = ConcurrentHashMap<Long, Long>()

    /** flush/终态写互斥锁：串行化在途 flush 与 pauseDownload 的暂停态写。 */
    private val flushLock = Any()

    @Volatile
    private var shutdown = false

    override fun afterPropertiesSet() {
        scheduler?.scheduleWithFixedDelay(this::tick, intervalMs, intervalMs, TimeUnit.MILLISECONDS)
    }

    override fun destroy() {
        shutdown = true
        scheduler?.shutdownNow()
        // 关停兜底：把剩余内存进度立即落库（DownloadService.destroy 亦会调用
        // flushAll——双保险，幂等）。
        flushAll()
    }

    /** 页 worker 上报最新 done（仅内存 put，绝不阻塞在 DB 上）。 */
    fun record(taskId: Long, done: Int) {
        pending[taskId] = done.toLong()
    }

    /** 立即落库单个任务（终态/取消/完成化路径调用）。无待写进度时零 SQL。 */
    fun flush(taskId: Long) {
        synchronized(flushLock) { drain(taskId) }
    }

    /** 立即落库全部待写任务（关停路径调用）。 */
    fun flushAll() {
        synchronized(flushLock) { pending.keys.toList().forEach { drain(it) } }
    }

    /** 丢弃待写进度（删除路径：墓碑化前进度已无意义）。 */
    fun discard(taskId: Long) {
        pending.remove(taskId)
    }

    /** 在同一把 flush 锁内执行 [block]（暂停态写防在途 flush 以旧状态覆盖）。 */
    fun <T> locked(block: () -> T): T = synchronized(flushLock) { block() }

    /** 调度器 tick：批量落库。异常兜底——绝不杀死调度线程。 */
    internal fun tick() {
        if (shutdown) return
        try {
            flushAll()
        } catch (e: Exception) {
            logger.warn("Batched progress flush failed: {}", e.message)
        }
    }

    /**
     * 落库单个任务的最新的 done：守卫与旧 persistProgress 逐字一致——墓碑行
     * （deleted=true）跳过、暂停(0)/失败(4) 行跳过（进度写不得复活状态/删除）；
     * lastModified 取 flush 时刻；异常只记日志（丢当秒窗口，不影响下载继续）。
     */
    private fun drain(taskId: Long) {
        val done = pending.remove(taskId) ?: return
        try {
            downloadRepository.findById(taskId).ifPresent { e ->
                if (!e.deleted && e.state != 0 && e.state != 4) {
                    e.done = done.toInt()
                    // v4（WP2）：水位走服务器打戳；无 clock 的直构场景保留原测试缝。
                    e.lastModified = syncClock?.next() ?: clock()
                    downloadRepository.save(e)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to persist batched download progress for id={}", taskId, e)
        }
    }

    private companion object {
        const val DEFAULT_INTERVAL_MS = 1_000L
    }
}
