package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.client.EhRequestBuilder
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.web.config.EhCoreConfigProperties
import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.entity.DownloadInfoEntity
import com.hippo.ehviewer.web.entity.DownloadLabelEntity
import com.hippo.ehviewer.web.repository.DownloadInfoRepository
import com.hippo.ehviewer.web.repository.DownloadLabelRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Download manager with bounded concurrency:
 *
 * - Gallery-level concurrency is capped by [EhCoreConfigProperties.DownloadProperties.maxConcurrentGalleries]
 *   via a bounded thread pool; page-level concurrency per gallery is capped by
 *   [EhCoreConfigProperties.DownloadProperties.maxConcurrentImages].
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
    private val config: EhCoreConfigProperties,
    private val eventPublisher: ApplicationEventPublisher,
    private val imageCacheService: ImageCacheService,
    private val sessionManager: EhSessionManager,
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

    fun listDownloads(labelId: Int? = null): DownloadListResponse {
        val downloads = if (labelId != null && labelId != 0) {
            downloadRepository.findByLabel(labelId)
        } else {
            downloadRepository.findAll()
        }
        val labels = labelRepository.findAll()

        return DownloadListResponse(
            downloads = downloads.map { it.toItem() },
            labels = labels.map { DownloadLabel(it.id, it.label, it.time) }
        )
    }

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
        // Expired E-Hentai logins surface as a 401 before any download begins.
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
     * Fetches the total page count via ehviewer-core's GalleryDetailParser
     * (EhEngine.getGalleryDetail). Returns null when the upstream call fails —
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
     * Backs off on E-Hentai 509 rate limiting.
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
     * [EhCoreConfigProperties.DownloadProperties.downloadTimeout] per request
     * and backs off on 509 responses.
     */
    private fun downloadImage(url: String): ByteArray? {
        val cached = imageCacheService.getCachedImage(url)
        if (cached != null) return cached

        for (attempt in 0 until 3) {
            if (attempt > 0 && !sleepQuietly(config.download.downloadDelay.toLong())) return null
            try {
                val request = EhRequestBuilder(url, EhUrl.getReferer()).build()
                val call = okHttpClient.newCall(request)
                call.timeout().timeout(config.download.downloadTimeout, TimeUnit.MILLISECONDS)
                call.execute().use { response ->
                    if (response.code() == 509) continue
                    if (response.isSuccessful) return response.body()?.bytes()
                    logger.warn("Image download HTTP {} from {}", response.code(), url)
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
        downloadDir = downloadDir,
        error = error
    )

    override fun destroy() {
        tasks.values.forEach { it.requestStop() }
        workerPool.shutdownNow()
        tasks.values.forEach { it.pageExecutor.shutdownNow() }
    }
}
