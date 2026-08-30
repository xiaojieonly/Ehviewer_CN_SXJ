package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Pins [DownloadDirIndex]: one listFiles per gid-dir at load, `%04d` 1-based
 * page mapping for 0-based [findPage] queries, extension priority
 * (jpg/jpeg/png/gif/webp), mtime-driven refresh, and invalidation.
 */
class DownloadDirIndexTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var config: SiteCoreConfigProperties
    private lateinit var index: DownloadDirIndex

    @BeforeEach
    fun setUp() {
        config = SiteCoreConfigProperties()
        config.download.path = tempDir.absolutePath
        index = DownloadDirIndex(config)
    }

    private fun gidDir(gid: Long): File = File(tempDir, gid.toString()).apply { mkdirs() }

    private fun pushFile(gid: Long, name: String): File =
        File(gidDir(gid), name).apply { writeBytes(byteArrayOf(1, 2, 3)) }

    @Test
    fun `loadAll indexes numeric gid directories only`() {
        pushFile(123L, "0001.jpg")
        pushFile(123L, "0002.webp")
        File(tempDir, "not-a-gid").mkdirs()
        File(tempDir, "other.txt").writeBytes(byteArrayOf(1))

        index.loadAll()

        assertEquals(2, index.pageCount(123L))
        assertEquals(0, index.pageCount(999L))
    }

    @Test
    fun `findPage maps 0-based api page to the 1-based file name`() {
        pushFile(77L, "0001.jpg")
        pushFile(77L, "0002.png")
        index.loadAll()

        val first = index.findPage(77L, 0)
        assertNotNull(first)
        assertEquals("0001.jpg", first!!.fileName())
        assertEquals("jpg", first.ext)
        assertEquals(3L, first.size)

        val second = index.findPage(77L, 1)
        assertNotNull(second)
        assertEquals("0002.png", second!!.fileName())

        assertNull(index.findPage(77L, 2), "out-of-range 0-based page")
        assertNull(index.findPage(77L, -1), "negative pages are never valid")
    }

    @Test
    fun `extension priority keeps jpg over webp for the same page`() {
        pushFile(88L, "0001.webp")
        pushFile(88L, "0001.jpg")
        index.loadAll()

        val ref = index.findPage(88L, 0)
        assertNotNull(ref)
        assertEquals("jpg", ref!!.ext)
        assertEquals("0001.jpg", ref.fileName())
    }

    @Test
    fun `non-image and non-page files are ignored`() {
        pushFile(55L, "0001.txt")
        pushFile(55L, "cover.jpg")
        pushFile(55L, "notes.md")
        index.loadAll()

        assertEquals(0, index.pageCount(55L), "only %04d.<image-ext> files are pages")
    }

    @Test
    fun `long page numbers (5+ digits) are indexed`() {
        pushFile(56L, "12345.jpg")
        index.loadAll()

        assertEquals(1, index.pageCount(56L))
        assertNotNull(index.findPage(56L, 12344))
    }

    @Test
    fun `dir mtime change triggers a refresh without invalidate`() {
        val dir = gidDir(9L)
        pushFile(9L, "0001.jpg")
        index.loadAll()
        assertEquals(1, index.pageCount(9L))

        // 跨目录 mtime 失效：目录被重新写入（mtime 变化）后，索引在下一次
        // 访问时按需重扫，看到新增文件。
        pushFile(9L, "0002.gif")
        dir.setLastModified(dir.lastModified() + 10_000)

        assertEquals(2, index.pageCount(9L))
        assertNotNull(index.findPage(9L, 1))
    }

    @Test
    fun `invalidate drops stale content after files are removed`() {
        val dir = gidDir(9L)
        pushFile(9L, "0001.jpg")
        pushFile(9L, "0002.png")
        index.loadAll()
        assertEquals(2, index.pageCount(9L))

        File(dir, "0002.png").delete()
        dir.setLastModified(dir.lastModified() + 10_000)
        index.invalidate(9L)

        assertEquals(1, index.pageCount(9L))
        assertNull(index.findPage(9L, 1))
    }

    @Test
    fun `invalidate on a deleted directory yields zero`() {
        val dir = gidDir(3L)
        pushFile(3L, "0001.jpg")
        index.loadAll()
        assertEquals(1, index.pageCount(3L))

        dir.deleteRecursively()
        index.invalidate(3L)

        assertEquals(0, index.pageCount(3L))
    }

    @Test
    fun `Lazy scan picks up directories created after startup`() {
        // 启动后才推送的目录：无需 loadAll 重跑，首次访问按需建立索引。
        pushFile(42L, "0001.jpg")
        assertEquals(1, index.pageCount(42L))
        assertNotNull(index.findPage(42L, 0))
    }

    @Test
    fun `missing downloads root is a no-op`() {
        config.download.path = File(tempDir, "does-not-exist").absolutePath
        index = DownloadDirIndex(config)
        index.loadAll()
        assertEquals(0, index.pageCount(1L))
    }
}
