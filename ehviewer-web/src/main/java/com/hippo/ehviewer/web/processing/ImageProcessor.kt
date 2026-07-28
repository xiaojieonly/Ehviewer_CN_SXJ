package com.hippo.ehviewer.web.processing

import java.nio.file.Path

/**
 * Abstract interface for image processing implementations.
 *
 * Implementations may include waifu2x (via HTTP), local upscalers, or no-op
 * placeholders. The pipeline scheduler ([ImageProcessingService]) dispatches
 * work to available processors based on capabilities.
 *
 * See: docs/webui-roadmap.md Phase 1 §1.3
 */
interface ImageProcessor {
    /** Processor unique identifier (e.g., "waifu2x", "noop") */
    val id: String

    /** Whether this processor is available (checks external service connectivity) */
    fun isAvailable(): Boolean

    /** Process a single image, returning the path to the processed file */
    suspend fun process(input: Path, options: ProcessingOptions): Path

    /** Set of processing types this processor supports */
    val capabilities: Set<ProcessingType>
}

enum class ProcessingType {
    /** 2x upscale */
    UPSCALE_2X,
    /** 4x upscale */
    UPSCALE_4X,
    /** Denoise only */
    DENOISE,
    /** Denoise + upscale */
    DENOISE_UPSCALE
}

data class ProcessingOptions(
    val type: ProcessingType,
    val outputFormat: String = "png",
    val quality: Int = 90
)
