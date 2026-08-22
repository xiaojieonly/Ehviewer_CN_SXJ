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

    override val id: String = PROCESSOR_ID

    override fun isAvailable(): Boolean = true

    override suspend fun process(input: Path, options: ProcessingOptions): Path {
        // 输出格式白名单：outputFormat 会进入输出路径与扩展名，拒绝白名单外的
        // 值（含路径分隔符/../ 等）既防路径穿越，也防写出无法识别的伪扩展名。
        val format = options.outputFormat.lowercase()
        require(format in SUPPORTED_FORMATS) { "unsupported output format: ${options.outputFormat}" }
        // Copy input to output path with the requested format extension
        val outputDir = input.parent ?: Path.of(".")
        val baseName = input.fileName.toString().substringBeforeLast('.')
        val output = outputDir.resolve("$baseName.$format").normalize()
        require(output.parent == outputDir.normalize()) { "output path escapes input directory" }

        if (input != output) {
            Files.copy(input, output)
        }
        return output
    }

    override val capabilities: Set<ProcessingType> = ProcessingType.entries.toSet()

    companion object {
        const val PROCESSOR_ID = "noop"
        val SUPPORTED_FORMATS = setOf("png", "jpg", "jpeg", "webp")
    }
}
