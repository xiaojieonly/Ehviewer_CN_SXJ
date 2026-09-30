package com.hippo.ehviewer.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiverZipExtractTest {

    @Test
    fun unzipLongDirectoryKeepsZipOrderAndSkipsNonImages() {
        val root = Files.createTempDirectory("archiver-unzip").toFile()
        val zipFile = File(root, "archive.zip")
        val longDir = "画".repeat(120)
        assertTrue(longDir.toByteArray(StandardCharsets.UTF_8).size > 255)
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            zip.putNextEntry(ZipEntry("$longDir/"))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("$longDir/0002.jpg"))
            zip.write("second".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("$longDir/note.txt"))
            zip.write("skip".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("$longDir/../escaped.png"))
            zip.write("escape".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("$longDir/0001.PNG"))
            zip.write("first".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }

        val dest = File(root, "archiver_1")
        File(dest, "stale.txt").apply {
            parentFile?.mkdirs()
            writeText("stale")
        }

        assertTrue(ArchiverDownloadCompleter.unzipArchive(zipFile, dest))
        assertEquals("second", File(dest, "00000001.jpg").readText())
        assertEquals("first", File(dest, "00000002.png").readText())
        assertFalse(File(dest, "stale.txt").exists())
        assertEquals(2, dest.listFiles()?.size)
        root.deleteRecursively()
    }

    @Test
    fun unzipFailureDeletesTempDir() {
        val root = Files.createTempDirectory("archiver-unzip-fail").toFile()
        val zipFile = File(root, "broken.zip")
        zipFile.writeText("not a zip")
        val dest = File(root, "archiver_2")

        // 本地单元测试里 android.util.Log 会抛出未模拟异常，目录应已先被删除。
        try {
            assertFalse(ArchiverDownloadCompleter.unzipArchive(zipFile, dest))
        } catch (e: RuntimeException) {
            assertTrue(e.message.orEmpty().contains("not mocked"))
        }
        assertFalse(dest.exists())
        root.deleteRecursively()
    }
}
