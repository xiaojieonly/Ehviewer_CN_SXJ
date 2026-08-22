package com.hippo.anotherviewer.web.processing

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.nio.file.Path

/**
 * MASTER-2026-08-22 P5：processorAvailable 的契约语义（observability.md §4.3
 * 「非占位处理器已连接」）——与是否有活跃任务无关。
 */
class ProcessorAvailabilityTest {

    private fun service(processors: List<ImageProcessor>) = ImageProcessingService(
        processors = processors,
        eventPublisher = ApplicationEventPublisher { },
        galleryLookup = null,
        config = SiteCoreConfigProperties(),
        concurrency = 1,
        taskTtlMs = 600_000,
        maxTasks = 100,
    )

    @Test
    fun `noop-only pipeline reports no non-noop processor available`() {
        val svc = service(listOf(NoopProcessor()))
        assertFalse(svc.nonNoopProcessorAvailable())
        assertNull(svc.availableNonNoopProcessorId())
    }

    @Test
    fun `available non-noop processor is reported with its id`() {
        val fake = object : ImageProcessor {
            override val id = "fake-waifu2x"
            override fun isAvailable() = true
            override suspend fun process(input: Path, options: ProcessingOptions): Path = input
            override val capabilities: Set<ProcessingType> = setOf(ProcessingType.UPSCALE_2X)
        }
        val svc = service(listOf(NoopProcessor(), fake))
        assertTrue(svc.nonNoopProcessorAvailable())
        assertEquals("fake-waifu2x", svc.availableNonNoopProcessorId())
    }

    @Test
    fun `unavailable non-noop processor does not count as available`() {
        val offline = object : ImageProcessor {
            override val id = "offline"
            override fun isAvailable() = false
            override suspend fun process(input: Path, options: ProcessingOptions): Path = input
            override val capabilities: Set<ProcessingType> = setOf(ProcessingType.UPSCALE_4X)
        }
        val svc = service(listOf(offline))
        assertFalse(svc.nonNoopProcessorAvailable())
    }
}
