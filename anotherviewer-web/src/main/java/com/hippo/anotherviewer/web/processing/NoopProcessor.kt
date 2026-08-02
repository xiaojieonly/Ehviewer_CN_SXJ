package com.hippo.anotherviewer.web.processing

import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

/**
 * No-op image processor that copies input to output unchanged.
 *
 * Used as a placeholder until a real processor (e.g., waifu2x) is configured.
 * Ensures the processing pipeline is fully functional end-to-end even without
 * an external AI service.
 *
 * See: docs/webui-roadmap.md Phase 1 §1.4
 */
@Component
class NoopProcessor : ImageProcessor {

    override val id: String = "noop"

    override fun isAvailable(): Boolean = true

    override suspend fun process(input: Path, options: ProcessingOptions): Path {
        // Copy input to output path with the requested format extension
        val outputDir = input.parent ?: Path.of(".")
        val baseName = input.fileName.toString().substringBeforeLast('.')
        val output = outputDir.resolve("$baseName.${options.outputFormat}")

        if (input != output) {
            Files.copy(input, output)
        }
        return output
    }

    override val capabilities: Set<ProcessingType> = ProcessingType.entries.toSet()
}
