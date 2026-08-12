package com.hippo.anotherviewer.web.processing

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * NoopProcessor 输出格式白名单与路径越界防护：outputFormat 会进入输出路径，
 * 白名单外（含分隔符/../、非图片扩展名）必须拒绝。
 */
class NoopProcessorTest {

    @TempDir
    lateinit var tempDir: Path

    private val processor = NoopProcessor()

    @Test
    fun `copies input with whitelisted format and normalizes case`() = runBlocking {
        val input = Files.write(tempDir.resolve("0001.jpg"), byteArrayOf(1, 2, 3))

        val output = processor.process(
            input,
            ProcessingOptions(type = ProcessingType.UPSCALE_2X, outputFormat = "PNG")
        )

        assertEquals(tempDir.resolve("0001.png"), output)
        assertTrue(Files.readAllBytes(output).contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `rejects outputFormat containing path separators`() {
        val input = Files.write(tempDir.resolve("0001.jpg"), byteArrayOf(1))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                processor.process(input, ProcessingOptions(ProcessingType.UPSCALE_2X, "../evil"))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                processor.process(input, ProcessingOptions(ProcessingType.UPSCALE_2X, "a/b"))
            }
        }
    }

    @Test
    fun `rejects non-image outputFormat`() {
        val input = Files.write(tempDir.resolve("0001.jpg"), byteArrayOf(1))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                processor.process(input, ProcessingOptions(ProcessingType.UPSCALE_2X, "txt"))
            }
        }
    }
}
