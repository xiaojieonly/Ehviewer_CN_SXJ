package com.hippo.anotherviewer.web.processing

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.service.GalleryLookupService
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.imageio.ImageIO

/**
 * Queue scheduler for image processing tasks.
 *
 * Maintains an in-memory task queue with configurable concurrency.
 * Publishes progress events via Spring [ApplicationEventPublisher]
 * for WebSocket forwarding.
 *
 * See: docs/webui-roadmap.md Phase 1 §1.3, contracts/websocket-protocol.md §3.2
 */
@Service
class ImageProcessingService(
    private val processors: List<ImageProcessor>,
    private val eventPublisher: ApplicationEventPublisher,
    private val galleryLookup: GalleryLookupService? = null,
    private val config: SiteCoreConfigProperties,
    @Value("\${anotherviewer.processing.concurrency:1}") private val concurrency: Int,
    @Value("\${anotherviewer.processing.task-ttl-ms:600000}") private val taskTtlMs: Long,
    @Value("\${anotherviewer.processing.max-tasks:100}") private val maxTasks: Int
) : DisposableBean {
    private val logger = LoggerFactory.getLogger(ImageProcessingService::class.java)

    private val tasks = ConcurrentHashMap<String, ProcessingTaskStatus>()
    private val cancelRequests = ConcurrentHashMap.newKeySet<String>()
    private val completedTasks = AtomicLong(0)
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("image-processing")
    )
    private val semaphore = kotlinx.coroutines.sync.Semaphore(concurrency)

    /**
     * Resolve the real page count of a gallery from Gallery Site metadata so the
     * whole gallery is processed, not a placeholder single page.
     *
     * @return number of pages (0-based range `0 until count`), or null when
     * the gallery is unknown or the count cannot be fetched.
     */
    fun resolvePageCount(galleryId: Long): Int? = galleryLookup?.resolvePageCount(galleryId)

    /**
     * Submit a gallery for image enhancement processing.
     *
     * @param galleryId the gallery to process
     * @param pages range of page indices to process (0-based)
     * @param options processing options (type, format, quality)
     * @return taskId for status polling
     * @throws IllegalStateException if no processor is available
     */
    fun submitGallery(
        galleryId: Long,
        pages: IntRange,
        options: ProcessingOptions = ProcessingOptions(ProcessingType.UPSCALE_2X)
    ): String {
        val processor = selectProcessor(options.type)
            ?: throw IllegalStateException("No available processor for type ${options.type}")

        evictFinishedIfNeeded()

        val taskId = "proc-${UUID.randomUUID().toString().take(8)}"
        val totalPages = pages.count()

        val status = ProcessingTaskStatus(
            taskId = taskId,
            galleryId = galleryId,
            state = TaskState.PENDING,
            totalPages = totalPages,
            processedPages = 0,
            failedPages = 0,
            currentPage = -1,
            startedAt = null,
            completedAt = null,
            error = null
        )
        tasks[taskId] = status

        logger.info(
            "Processing task submitted: taskId={}, galleryId={}, pages={}, processor={}",
            taskId, galleryId, totalPages, processor.id
        )

        eventPublisher.publishEvent(ProcessingEvent.Started(
            taskId = taskId,
            galleryId = galleryId,
            totalPages = totalPages,
            processingType = options.type,
            processorId = processor.id
        ))

        scope.launch {
            semaphore.acquire()
            try {
                executeTask(taskId, galleryId, pages, options, processor)
            } finally {
                semaphore.release()
            }
        }

        return taskId
    }

    /**
     * Request cancellation of a task. The running worker checks the flag
     * between pages; the task finishes as FAILED with a "cancelled" error.
     */
    fun cancelTask(taskId: String): Boolean {
        if (!tasks.containsKey(taskId)) return false
        cancelRequests.add(taskId)
        return true
    }

    /**
     * Query the current status of a processing task.
     *
     * @return task status, or null if taskId is unknown
     */
    fun getTaskStatus(taskId: String): ProcessingTaskStatus? = tasks[taskId]

    /**
     * Get all active tasks (PENDING or PROCESSING).
     */
    fun getActiveTasks(): List<ProcessingTaskStatus> =
        tasks.values.filter { it.state == TaskState.PENDING || it.state == TaskState.PROCESSING }

    /**
     * Get the number of tasks in the queue (pending + processing).
     */
    fun getQueueSize(): Int =
        tasks.values.count { it.state == TaskState.PENDING || it.state == TaskState.PROCESSING }

    /** Total number of tasks that finished in the DONE state (monotonic counter). */
    fun getCompletedTaskCount(): Long = completedTasks.get()

    private suspend fun executeTask(
        taskId: String,
        galleryId: Long,
        pages: IntRange,
        options: ProcessingOptions,
        processor: ImageProcessor
    ) {
        val status = tasks[taskId] ?: return
        val startTime = System.currentTimeMillis()

        updateStatus(taskId) {
            it.copy(state = TaskState.PROCESSING, startedAt = Instant.now())
        }

        val outputDir = Path.of(config.download.cachePath, "enhanced", galleryId.toString())
        Files.createDirectories(outputDir)

        var processed = 0
        var failed = 0
        var firstFailedPage: Int? = null

        for (page in pages) {
            if (taskId in cancelRequests) break

            updateStatus(taskId) { it.copy(currentPage = page) }

            try {
                val inputPath = resolveInputImage(galleryId, page)
                if (inputPath == null || !Files.exists(inputPath)) {
                    logger.warn("Input image not found for gallery={} page={}, skipping", galleryId, page)
                    failed++
                    if (firstFailedPage == null) firstFailedPage = page
                    updateStatus(taskId) {
                        it.copy(processedPages = processed, failedPages = failed)
                    }
                    continue
                }

                // Honor the configured outputPath: write the enhanced image to
                // {cachePath}/enhanced/{galleryId}/{page}.{format} so the image
                // endpoint can serve it via ?enhanced=1.
                val resultPath = processor.process(inputPath, options)
                val outputPath = outputDir.resolve("$page.${options.outputFormat}")
                if (resultPath != outputPath) {
                    Files.deleteIfExists(outputPath)
                    Files.copy(resultPath, outputPath)
                }

                val durationMs = System.currentTimeMillis() - startTime
                logger.debug(
                    "Image processed: taskId={}, galleryId={}, page={}, processor={}, durationMs={}",
                    taskId, galleryId, page, processor.id, durationMs
                )

                processed++
                updateStatus(taskId) {
                    it.copy(processedPages = processed, failedPages = failed)
                }

                eventPublisher.publishEvent(ProcessingEvent.Progress(
                    taskId = taskId,
                    galleryId = galleryId,
                    processedPages = processed,
                    totalPages = pages.count(),
                    currentPage = page
                ))

                eventPublisher.publishEvent(ProcessingEvent.EnhancedReady(
                    taskId = taskId,
                    galleryId = galleryId,
                    page = page,
                    enhancedUrl = "/api/v1/image/$galleryId/$page?enhanced=1",
                    originalUrl = "/api/v1/image/$galleryId/$page",
                    processingType = options.type,
                    fileSize = runCatching { Files.size(outputPath) }.getOrDefault(0L),
                    width = readImageWidth(outputPath),
                    height = readImageHeight(outputPath)
                ))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(
                    "Image processing failed: taskId={}, galleryId={}, page={}",
                    taskId, galleryId, page, e
                )
                failed++
                if (firstFailedPage == null) firstFailedPage = page
                updateStatus(taskId) {
                    it.copy(processedPages = processed, failedPages = failed)
                }
            }
        }

        val elapsedMs = System.currentTimeMillis() - startTime
        val cancelled = taskId in cancelRequests
        val finalState = when {
            cancelled -> TaskState.FAILED
            failed > 0 -> TaskState.FAILED
            else -> TaskState.DONE
        }
        val error = when {
            cancelled -> "Task cancelled"
            failed > 0 -> "$failed of ${pages.count()} pages failed to process"
            else -> null
        }

        updateStatus(taskId) {
            it.copy(
                state = finalState,
                currentPage = -1,
                completedAt = Instant.now(),
                processedPages = processed,
                failedPages = failed,
                error = error
            )
        }

        if (finalState == TaskState.FAILED) {
            eventPublisher.publishEvent(ProcessingEvent.Failed(
                taskId = taskId,
                galleryId = galleryId,
                error = error ?: "Processing failed",
                failedPage = firstFailedPage,
                processedBeforeFailure = processed
            ))
        } else {
            completedTasks.incrementAndGet()
            eventPublisher.publishEvent(ProcessingEvent.Completed(
                taskId = taskId,
                galleryId = galleryId,
                enhancedPages = processed,
                elapsedMs = elapsedMs
            ))
        }

        logger.info(
            "Processing task finished: taskId={}, galleryId={}, state={}, processed={}, failed={}, elapsedMs={}",
            taskId, galleryId, tasks[taskId]?.state, processed, failed, elapsedMs
        )

        scheduleCleanup(taskId)
    }

    /**
     * Evict finished tasks once the map exceeds [maxTasks] (oldest first) and
     * drop finished tasks after [taskTtlMs], keeping the map bounded.
     */
    private fun evictFinishedIfNeeded() {
        if (tasks.size < maxTasks) return
        val finished = tasks.values
            .filter { it.state == TaskState.DONE || it.state == TaskState.FAILED }
            .sortedBy { it.completedAt ?: Instant.EPOCH }
        finished.take((tasks.size - maxTasks).coerceAtLeast(1)).forEach { tasks.remove(it.taskId) }
    }

    private fun scheduleCleanup(taskId: String) {
        scope.launch {
            delay(taskTtlMs)
            tasks.remove(taskId)
        }
    }

    private fun selectProcessor(type: ProcessingType): ImageProcessor? {
        return processors.firstOrNull { it.isAvailable() && type in it.capabilities }
    }

    private fun resolveInputImage(galleryId: Long, page: Int): Path? {
        // Look for cached original image in standard cache locations
        val cacheDir = Path.of(config.download.cachePath, galleryId.toString())
        if (!Files.isDirectory(cacheDir)) return null

        // Try common extensions
        for (ext in listOf("jpg", "jpeg", "png", "webp", "gif")) {
            val candidate = cacheDir.resolve("$page.$ext")
            if (Files.exists(candidate)) return candidate
        }
        return null
    }

    private fun readImageWidth(path: Path): Int = readImageDimension(path).first

    private fun readImageHeight(path: Path): Int = readImageDimension(path).second

    /** Best-effort header-only dimension read; 0 when the format is unsupported (e.g. webp). */
    private fun readImageDimension(path: Path): Pair<Int, Int> {
        return try {
            ImageIO.createImageInputStream(path.toFile()).use { stream ->
                if (stream == null) return Pair(0, 0)
                val readers = ImageIO.getImageReaders(stream)
                if (!readers.hasNext()) return Pair(0, 0)
                val reader = readers.next()
                try {
                    reader.setInput(stream)
                    Pair(reader.getWidth(0), reader.getHeight(0))
                } finally {
                    reader.dispose()
                }
            }
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }

    private fun updateStatus(taskId: String, transform: (ProcessingTaskStatus) -> ProcessingTaskStatus) {
        tasks.computeIfPresent(taskId) { _, status -> transform(status) }
    }

    override fun destroy() {
        scope.cancel()
        runBlocking {
            scope.coroutineContext.job.join()
        }
    }
}

// --- Task state model ---

enum class TaskState {
    PENDING, PROCESSING, DONE, FAILED
}

data class ProcessingTaskStatus(
    val taskId: String,
    val galleryId: Long,
    val state: TaskState,
    val totalPages: Int,
    val processedPages: Int,
    val failedPages: Int,
    val currentPage: Int,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val error: String?
)

// --- Spring application events for WebSocket forwarding ---

sealed class ProcessingEvent {
    data class Started(
        val taskId: String,
        val galleryId: Long,
        val totalPages: Int,
        val processingType: ProcessingType,
        val processorId: String
    ) : ProcessingEvent()

    data class Progress(
        val taskId: String,
        val galleryId: Long,
        val processedPages: Int,
        val totalPages: Int,
        val currentPage: Int
    ) : ProcessingEvent()

    data class Completed(
        val taskId: String,
        val galleryId: Long,
        val enhancedPages: Int,
        val elapsedMs: Long
    ) : ProcessingEvent()

    data class Failed(
        val taskId: String,
        val galleryId: Long,
        val error: String,
        val failedPage: Int?,
        val processedBeforeFailure: Int
    ) : ProcessingEvent()

    /**
     * A single page's enhanced version is ready on disk and can be served via
     * `GET /api/v1/image/{galleryId}/{page}?enhanced=1`.
     * See contracts/websocket-protocol.md §3.3 (image.enhanced.ready).
     */
    data class EnhancedReady(
        val taskId: String,
        val galleryId: Long,
        val page: Int,
        val enhancedUrl: String,
        val originalUrl: String,
        val processingType: ProcessingType,
        val fileSize: Long,
        val width: Int,
        val height: Int
    ) : ProcessingEvent()
}
