package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.client.SiteRequestBuilder
import com.hippo.anotherviewer.client.SiteUrl
import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.*
import com.hippo.anotherviewer.web.entity.DownloadInfoEntity
import com.hippo.anotherviewer.web.entity.DownloadLabelEntity
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import com.hippo.anotherviewer.web.repository.DownloadLabelRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Download manager with bounded concurrency:
 *
 * - Gallery-level concurrency is capped by [SiteCoreConfigProperties.DownloadProperties.maxConcurrentGalleries]
 *   via a bounded thread pool; page-level concurrency per gallery is capped by
 *   [SiteCoreConfigProperties.DownloadProperties.maxConcurrentImages].
 * - Pause/cancel/delete use a cooperative stop flag (checked between pages and
 *   immediately before file writes) instead of `Thread.interrupt()`, which
 *   OkHttp ignores.
 * - Rows are only finalised after the worker has exited; the final save checks
 *   row existence so a finished worker can never resurrect a deleted row.
 * - A failed page-count fetch marks the task FAILED (state 4) instead of
 *   fabricating a fake 1-page completion.
 *
 * State semantics (Android `DownloadInfo.STATE_*`): 0=NONE/WAIT(paused),
 * 1=WAIT, 2=DOWNLOADING, 3=FINISHED, 4=FAILED.
 */
@Service
class DownloadService(
    private val downloadRepository: DownloadInfoRepository,
    private val labelRepository: DownloadLabelRepository,
    private val config: SiteCoreConfigProperties,
    private val eventPublisher: ApplicationEventPublisher,
    private val imageCacheService: ImageCacheService,
    private val sessionManager: SiteSessionManager,
    private val galleryLookup: GalleryLookupService
) : DisposableBean {
    private val logger = LoggerFactory.getLogger(DownloadService::class.java)

    /** In-flight download tasks, keyed by download id. */
    private val tasks = ConcurrentHashMap<Long, DownloadTask>()

    /**
     * Bounded gallery worker pool — at most [maxConcurrentGalleries] galleries
     * run concurrently; the rest wait in the queue.
     */
    private val workerPool: ThreadPoolExecutor = ThreadPoolExecutor(
        config.download.maxConcurrentGalleries,
        config.download.maxConcurrentGalleries,
        60, TimeUnit.SECONDS,
        LinkedBlockingQueue()
    )

    private val okHttpClient get() = sessionManager.okHttpClient

    /**
     * One in-flight download. [stopRequested] is the cooperative pause/delete
     * flag; [finished] is counted down when the worker thread has fully exited
     * so callers can join before mutating the row.
     */
    private class DownloadTask(
        val id: Long,
        val gid: Long,
        val token: String,
        val downloadDir: String,
        val label: Int,
        maxConcurrentImages: Int
    ) {
        val stopRequested = AtomicBoolean(false)
        val finished = CountDownLatch(1)
        val pageExecutor: ExecutorService = Executors.newFixedThreadPool(maxConcurrentImages)

        fun requestStop() {
            stopRequested.set(true)
        }

        /** Wait for the worker to exit; returns false on timeout. */
        fun awaitFinished(timeoutMs: Long): Boolean {
            pageExecutor.shutdown()
            return try {
                finished.await(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
        }
    }

    // ── query ───────────────────────────────────────────────────

    fun listDownloads(
        labelId: Int? = null,
        offset: Int = 0,
        limit: Int = 100,
        sort: String = "time_desc",
        q: String? = null,
    ): DownloadListResponse {
        // A5 契约：offset 为行偏移、limit 为每页条数。PageRequest 以页码为参数，
        // 换算 pageIndex = offset / limit（前端按 limit 倍数递增，语义精确）。
        // 排序（sort 参数，未知值回落默认 time_desc=添加时间倒序=最新在前）：
        //   time_desc / time_asc / title_asc / title_desc
        // 搜索（q 非空时按标题/标题日文模糊匹配，服务端过滤——负载留在服务器）。
        val size = limit.coerceIn(1, 500)
        val pageable = PageRequest.of(offset.coerceAtLeast(0) / size, size, sortOf(sort))
        val labels = labelRepository.findAll()

        val labelFilter = labelId?.takeIf { it != 0 }
        val qFilter = q?.takeIf { it.isNotBlank() }?.let(::escapeLike)

        val (page, totalCount) = when {
            qFilter != null -> downloadRepository.searchDownloads(labelFilter, qFilter, pageable) to
                downloadRepository.countSearchDownloads(labelFilter, qFilter)
            labelFilter != null -> downloadRepository.findByLabel(labelFilter, pageable) to
                downloadRepository.countByLabel(labelFilter)
            else -> downloadRepository.findAll(pageable) to downloadRepository.count()
        }
        return DownloadListResponse(
            downloads = page.content.map { it.toItem() },
            labels = labels.map { DownloadLabel(it.id, it.label, it.time) },
            total = totalCount.toInt()
        )
    }

    /** sort 参数 → Spring Data Sort；未知取值回落默认（time_desc）。 */
    private fun sortOf(sort: String): Sort = when (sort) {
        "time_asc" -> Sort.by(Sort.Direction.ASC, "time")
        "title_asc" -> Sort.by(Sort.Direction.ASC, "title")
        "title_desc" -> Sort.by(Sort.Direction.DESC, "title")
        else -> Sort.by(Sort.Direction.DESC, "time")
    }

    /** LIKE 通配符转义（与 repository 查询的 ESCAPE '\' 配对）。 */
    private fun escapeLike(raw: String): String =
        raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    fun getDownloadInfo(id: Long): DownloadItem? {
        return downloadRepository.findById(id).orElse(null)?.toItem()
    }

    // ── lifecycle ───────────────────────────────────────────────

    fun addDownload(request: DownloadAddRequest): Boolean {
        val existing = downloadRepository.findByGid(request.gid)
        if (existing != null) return false

        val downloadPath = File(config.download.path, "${request.gid}")
        downloadPath.mkdirs()

        val entity = DownloadInfoEntity().apply {
            gid = request.gid
            token = request.token
            title = request.title
            titleJpn = ""
            thumb = request.thumb
            category = 0
            state = 0
            total = 0
            done = 0
            label = request.label
            downloadDir = downloadPath.absolutePath
            time = System.currentTimeMillis()
        }
        downloadRepository.save(entity)
        return true
    }

    fun startDownload(id: Long): Boolean {
        // Expired Gallery Site logins surface as a 401 before any download begins.
        sessionManager.requireValidSession()

        val entity = downloadRepository.findById(id).orElse(null) ?: return false
        if (entity.state == 1 || entity.state == 2) return false

        val downloadDir = entity.downloadDir
            ?: File(config.download.path, "${entity.gid}").absolutePath
        updateEntity(id) {
            it.state = 1
            it.error = null
            if (it.downloadDir == null) it.downloadDir = downloadDir
        }

        val task = DownloadTask(
            id = entity.id,
            gid = entity.gid,
            token = entity.token,
            downloadDir = downloadDir,
            label = entity.label,
            maxConcurrentImages = config.download.maxConcurrentImages.coerceAtLeast(1)
        )
        tasks[id] = task
        try {
            workerPool.execute { executeDownload(task) }
        } catch (e: RejectedExecutionException) {
            tasks.remove(id, task)
            updateEntity(id) {
                it.state = 4
                it.error = "Download queue rejected the task"
            }
            return false
        }
        return true
    }

    fun pauseDownload(id: Long): Boolean {
        val entity = downloadRepository.findById(id).orElse(null) ?: return false
        if (entity.state == 0) return false

        // Cooperative stop: the worker checks the flag between page downloads
        // and stops writing; the row transitions to state 0 (WAIT/paused).
        tasks[id]?.requestStop()
        updateEntity(id) { it.state = 0 }
        return true
    }

    fun cancelDownload(id: Long): Boolean {
        val entity = downloadRepository.findById(id).orElse(null) ?: return false
        val task = tasks[id]
        task?.requestStop()
        // Wait for the worker to exit before marking the row cancelled so a
        // late worker save cannot resurrect it as finished.
        task?.awaitFinished(90_000)
        if (downloadRepository.existsById(id)) {
            updateEntity(id) {
                it.state = 4
                it.error = "Cancelled"
            }
        }
        tasks.remove(id)
        return true
    }

    fun deleteDownload(id: Long): Boolean {
        val entity = downloadRepository.findById(id).orElse(null) ?: return false
        val task = tasks[id]
        task?.requestStop()
        task?.awaitFinished(90_000)
        entity.downloadDir?.let { dir ->
            val dirFile = File(dir)
            if (dirFile.exists()) dirFile.deleteRecursively()
        }
        if (downloadRepository.existsById(id)) {
            downloadRepository.deleteById(id)
        }
        tasks.remove(id)
        return true
    }

    fun startAllDownloads() {
        val waiting = downloadRepository.findByState(0)
        waiting.forEach { startDownload(it.id) }
    }

    fun pauseAllDownloads() {
        val active = downloadRepository.findByState(1) + downloadRepository.findByState(2)
        active.forEach { pauseDownload(it.id) }
    }

    // ── 批量操作（Android 多选模式 Start/Stop/Delete/Move 的 WebUI 对等物）──
    // all=true 时忽略 ids，按 (label, q) 过滤条件在服务端解析全集（跨页全选）。

    /** 批量开始：返回成功开始的数量。 */
    fun startDownloads(ids: List<Long>?, all: Boolean, label: Int?, q: String?): Int {
        var started = 0
        resolveBatchIds(ids, all, label, q).forEach { if (startDownload(it)) started++ }
        return started
    }

    /** 批量停止（暂停）：返回成功暂停的数量。 */
    fun pauseDownloads(ids: List<Long>?, all: Boolean, label: Int?, q: String?): Int {
        var paused = 0
        resolveBatchIds(ids, all, label, q).forEach { if (pauseDownload(it)) paused++ }
        return paused
    }

    /** 批量删除：返回已删除的数量（含下载目录文件）。 */
    fun deleteDownloads(ids: List<Long>?, all: Boolean, label: Int?, q: String?): Int {
        var removed = 0
        resolveBatchIds(ids, all, label, q).forEach { if (deleteDownload(it)) removed++ }
        return removed
    }

    /** 批量移动标签：labelId=0 表示移回默认标签；返回成功更新的数量。 */
    fun moveDownloads(ids: List<Long>?, all: Boolean, label: Int?, q: String?, labelId: Int): Int {
        if (labelId != 0 && !labelRepository.existsById(labelId.toLong())) return 0
        var moved = 0
        resolveBatchIds(ids, all, label, q).forEach { id ->
            updateEntity(id) { it.label = labelId }
            moved++
        }
        return moved
    }

    /** 批量目标解析：all=true → 按 (label, q) 过滤全集取 id（服务端投影，不加载实体）；
     *  否则直接用 ids。 */
    private fun resolveBatchIds(ids: List<Long>?, all: Boolean, label: Int?, q: String?): List<Long> {
        if (!all) return ids.orEmpty()
        val labelFilter = label?.takeIf { it != 0 }
        val qFilter = q?.takeIf { it.isNotBlank() }?.let(::escapeLike)
        return downloadRepository
            .findAllIdsBy(labelFilter, qFilter, PageRequest.of(0, MAX_BATCH_IDS))
            .content
    }

    // ── labels ──────────────────────────────────────────────────

    fun createLabel(label: String): Boolean {
        val existing = labelRepository.findByLabel(label)
        if (existing != null) return false

        val entity = DownloadLabelEntity().apply {
            this.label = label
            time = System.currentTimeMillis()
        }
        labelRepository.save(entity)
        return true
    }

    fun deleteLabel(id: Long): Boolean {
        if (!labelRepository.existsById(id)) return false
        labelRepository.deleteById(id)
        return true
    }

    // ── stats ───────────────────────────────────────────────────

    fun getActiveDownloadCount(): Int = tasks.size

    fun getCompletedDownloadCount(): Long = downloadRepository.findByState(3).size.toLong()

    fun getFailedDownloadCount(): Long = downloadRepository.findByState(4).size.toLong()

    fun getActiveDownloads(): List<DownloadItem> {
        return tasks.keys.mapNotNull { id ->
            getDownloadInfo(id)
        }
    }

    // ── worker ──────────────────────────────────────────────────

    private fun executeDownload(task: DownloadTask) {
        try {
            runDownload(task)
        } catch (e: Exception) {
            logger.error("Download failed for gid=${task.gid}", e)
            updateEntity(task.id) {
                it.state = 4
                it.error = e.message ?: "Download failed"
            }
            publishProgress(task, 4, 0, 0)
        } finally {
            task.pageExecutor.shutdown()
            tasks.remove(task.id, task)
            task.finished.countDown()
        }
    }

    private fun runDownload(task: DownloadTask) {
        val downloadDir = File(task.downloadDir)
        downloadDir.mkdirs()

        val existingTotal = downloadRepository.findById(task.id)
            .map { it.total }.orElse(0)
        val totalPages = if (existingTotal > 0) existingTotal else fetchPageCount(task.gid, task.token)

        if (totalPages == null || totalPages <= 0) {
            // A failed page-count fetch must NEVER masquerade as a 1-page
            // completed download — mark the task failed instead.
            updateEntity(task.id) {
                it.state = 4
                it.total = 0
                it.error = "Failed to fetch page count for gallery ${task.gid}"
            }
            publishProgress(task, 4, 0, 0)
            return
        }

        updateEntity(task.id) {
            it.state = 2
            it.total = totalPages
            it.done = 0
            it.error = null
        }
        publishProgress(task, 2, 0, totalPages)

        val done = AtomicInteger(0)
        val submissions = ArrayList<Future<*>>(totalPages)
        for (page in 1..totalPages) {
            if (task.stopRequested.get()) break
            submissions.add(task.pageExecutor.submit {
                downloadPage(task, downloadDir, page, done, totalPages)
            })
        }

        task.pageExecutor.shutdown()
        try {
            if (!task.pageExecutor.awaitTermination(10, TimeUnit.MINUTES)) {
                task.pageExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            task.pageExecutor.shutdownNow()
        }

        // Final state decision — only after every page worker has exited.
        if (task.stopRequested.get()) {
            updateEntity(task.id) {
                it.state = 0
                it.done = done.get()
            }
            return
        }

        val completed = done.get()
        if (completed >= totalPages) {
            updateEntity(task.id) {
                it.state = 3
                it.done = completed
                it.error = null
            }
            publishProgress(task, 3, completed, totalPages)
        } else {
            updateEntity(task.id) {
                it.state = 4
                it.done = completed
                it.error = "Download incomplete: $completed of $totalPages pages completed"
            }
            publishProgress(task, 4, completed, totalPages)
        }
    }

    /**
     * Download a single page. The stop flag is checked before resolving the
     * image URL and immediately before writing the file, so a paused task
     * never writes additional files.
     */
    private fun downloadPage(
        task: DownloadTask,
        downloadDir: File,
        page: Int,
        done: AtomicInteger,
        totalPages: Int
    ) {
        try {
            if (task.stopRequested.get()) return

            val fileName = "%04d.jpg".format(page)
            val file = File(downloadDir, fileName)
            if (file.exists() && file.length() > 0) {
                val current = done.incrementAndGet()
                persistProgress(task, current)
                return
            }

            val imageUrl = fetchImageUrl(task, page) ?: return
            if (task.stopRequested.get()) return

            val imageData = downloadImage(imageUrl) ?: return
            if (task.stopRequested.get()) return

            file.writeBytes(imageData)
            val current = done.incrementAndGet()
            imageCacheService.cacheImage(imageUrl, imageData)
            persistProgress(task, current)
            publishProgress(task, 2, current, totalPages)
        } catch (e: Exception) {
            logger.warn("Page download failed for gid={} page={}: {}", task.gid, page, e.message)
        }
    }

    /**
     * Persist progress periodically (not only at completion) so restarts
     * resume from the last persisted `done`. Never resurrects a paused (0) or
     * cancelled/failed (4) row.
     */
    private fun persistProgress(task: DownloadTask, done: Int) {
        try {
            downloadRepository.findById(task.id).ifPresent { e ->
                if (e.state != 0 && e.state != 4) {
                    e.done = done
                    downloadRepository.save(e)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to persist download progress for id={}", task.id, e)
        }
    }

    /**
     * Idempotent entity update through a fresh load — guards the final save so
     * a finished worker cannot resurrect a deleted row.
     */
    private fun updateEntity(id: Long, transform: (DownloadInfoEntity) -> Unit) {
        try {
            downloadRepository.findById(id).ifPresent { e ->
                transform(e)
                downloadRepository.save(e)
            }
        } catch (e: Exception) {
            logger.warn("Failed to update download row id={}", id, e)
        }
    }

    private fun publishProgress(task: DownloadTask, state: Int, done: Int, total: Int) {
        eventPublisher.publishEvent(DownloadProgress(
            gid = task.gid,
            state = state,
            downloaded = done,
            total = total,
            speed = 0,
            label = task.label
        ))
    }

    /**
     * Fetches the total page count via anotherviewer-core's GalleryDetailParser
     * (SiteEngine.getGalleryDetail). Returns null when the upstream call fails —
     * callers must NOT treat that as a 1-page gallery.
     */
    private fun fetchPageCount(gid: Long, token: String): Int? {
        for (attempt in 0 until 3) {
            if (attempt > 0 && !sleepQuietly(config.download.downloadDelay.toLong())) return null
            val count = try {
                galleryLookup.fetchPageCount(gid, token)
            } catch (e: Exception) {
                logger.warn("Failed to fetch page count for gid=$gid (attempt ${attempt + 1})", e)
                null
            }
            if (count != null && count > 0) return count
        }
        return null
    }

    /**
     * Fetches the image URL for a 1-based gallery page via GalleryPageParser.
     * Backs off on Gallery Site 509 rate limiting.
     */
    private fun fetchImageUrl(task: DownloadTask, page: Int): String? {
        for (attempt in 0 until 3) {
            if (attempt > 0 && !sleepQuietly(config.download.downloadDelay.toLong())) return null
            return try {
                galleryLookup.fetchImageUrl(task.gid, task.token, page)
            } catch (e: Exception) {
                logger.warn("Failed to fetch image URL for gid={} page={} (attempt ${attempt + 1})", task.gid, page, e)
                null
            } ?: continue
        }
        return null
    }

    /**
     * Downloads image bytes with the shared session client. Honors
     * [SiteCoreConfigProperties.DownloadProperties.downloadTimeout] per request
     * and backs off on 509 responses.
     */
    private fun downloadImage(url: String): ByteArray? {
        val cached = imageCacheService.getCachedImage(url)
        if (cached != null) return cached

        for (attempt in 0 until 3) {
            if (attempt > 0 && !sleepQuietly(config.download.downloadDelay.toLong())) return null
            try {
                val request = SiteRequestBuilder(url, SiteUrl.getReferer()).build()
                val call = okHttpClient.newCall(request)
                call.timeout().timeout(config.download.downloadTimeout, TimeUnit.MILLISECONDS)
                call.execute().use { response ->
                    if (response.code == 509) continue
                    if (response.isSuccessful) return response.body?.bytes()
                    logger.warn("Image download HTTP {} from {}", response.code, url)
                    return null
                }
            } catch (e: Exception) {
                logger.warn("Failed to download image from $url", e)
                return null
            }
        }
        return null
    }

    private fun sleepQuietly(ms: Long): Boolean {
        if (ms <= 0) return true
        return try {
            Thread.sleep(ms)
            true
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private fun DownloadInfoEntity.toItem() = DownloadItem(
        id = id,
        gid = gid,
        token = token,
        title = title,
        titleJpn = titleJpn,
        thumb = thumb,
        category = category,
        state = state,
        total = total,
        done = done,
        label = label,
        // The server's absolute download path is never exposed to clients.
        downloadDir = null,
        error = error
    )

    override fun destroy() {
        tasks.values.forEach { it.requestStop() }
        workerPool.shutdownNow()
        tasks.values.forEach { it.pageExecutor.shutdownNow() }
    }

    private companion object {
        /** 跨页全选/批量单次解析的全集上限（9000+ 级规模安全；超限取前 N 条）。 */
        const val MAX_BATCH_IDS = 100_000
    }
}
