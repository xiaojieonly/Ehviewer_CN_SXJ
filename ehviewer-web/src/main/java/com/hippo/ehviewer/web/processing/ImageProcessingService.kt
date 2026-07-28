package com.hippo.ehviewer.web.processing

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
    @Value("\${ehviewer.processing.concurrency:1}") private val concurrency: Int,
    @Value("\${ehviewer.cache.image-path:./data/cache}") private val cachePath: String
) : DisposableBean {
    private val logger = LoggerFactory.getLogger(ImageProcessingService::class.java)

    private val tasks = ConcurrentHashMap<String, ProcessingTaskStatus>()
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("image-processing")
    )
    private val semaphore = kotlinx.coroutines.sync.Semaphore(concurrency)

    /**
     * Submit a gallery for image enhancement processing.
     *
     * @param galleryId the gallery to process
     * @param pages range of page indices to process
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

        val outputDir = Path.of(cachePath, "enhanced", galleryId.toString())
        Files.createDirectories(outputDir)

        var processed = 0
        var failed = 0

        for (page in pages) {
            updateStatus(taskId) { it.copy(currentPage = page) }

            try {
                val inputPath = resolveInputImage(galleryId, page)
                if (inputPath == null || !Files.exists(inputPath)) {
                    logger.warn("Input image not found for gallery={} page={}, skipping", galleryId, page)
                    failed++
                    updateStatus(taskId) {
                        it.copy(processedPages = processed, failedPages = failed)
                    }
                    continue
                }

                val outputPath = outputDir.resolve("$page.${options.outputFormat}")
                val startTimeMs = System.currentTimeMillis()

                processor.process(inputPath, options)

                val durationMs = System.currentTimeMillis() - startTimeMs
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(
                    "Image processing failed: taskId={}, galleryId={}, page={}",
                    taskId, galleryId, page, e
                )
                failed++
                updateStatus(taskId) {
                    it.copy(processedPages = processed, failedPages = failed)
                }
            }
        }

        val elapsedMs = System.currentTimeMillis() - startTime
        val finalState = if (failed == 0) TaskState.DONE else TaskState.DONE

        updateStatus(taskId) {
            it.copy(
                state = finalState,
                currentPage = -1,
                completedAt = Instant.now(),
                processedPages = processed,
                failedPages = failed
            )
        }

        if (failed > 0 && processed == 0) {
            updateStatus(taskId) {
                it.copy(state = TaskState.FAILED, error = "All pages failed to process")
            }
            eventPublisher.publishEvent(ProcessingEvent.Failed(
                taskId = taskId,
                galleryId = galleryId,
                error = "All pages failed to process",
                failedPage = pages.first,
                processedBeforeFailure = 0
            ))
        } else {
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
    }

    private fun selectProcessor(type: ProcessingType): ImageProcessor? {
        return processors.firstOrNull { it.isAvailable() && type in it.capabilities }
    }

    private fun resolveInputImage(galleryId: Long, page: Int): Path? {
        // Look for cached original image in standard cache locations
        val cacheDir = Path.of(cachePath, galleryId.toString())
        if (!Files.isDirectory(cacheDir)) return null

        // Try common extensions
        for (ext in listOf("jpg", "jpeg", "png", "webp", "gif")) {
            val candidate = cacheDir.resolve("$page.$ext")
            if (Files.exists(candidate)) return candidate
        }
        return null
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
}
