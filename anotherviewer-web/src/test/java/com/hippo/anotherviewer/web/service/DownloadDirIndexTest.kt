package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Pins [DownloadDirIndex]: one listFiles per gid-dir at load, `%04d` 1-based
 * page mapping for 0-based [findPage] queries, extension priority
 * (jpg/jpeg/png/gif/webp), mtime-driven refresh, and invalidation.
 *
 * Refresh interval semantics (default 0 here = legacy "check on every
 * request"): index hits self-validate against the cached directory with zero
 * root traversal, per-gid healing (rename/delete) bypasses the TTL, and the
 * root-listing fingerprint check is throttled by the injected interval —
 * covered by the dedicated TTL tests below.
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
        assertEquals("0001.jpg", first!!.fileName)
        assertEquals("jpg", first.ext)
        assertEquals(3L, first.size)

        val second = index.findPage(77L, 1)
        assertNotNull(second)
        assertEquals("0002.png", second!!.fileName)

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
        assertEquals("0001.jpg", ref.fileName)
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

    // ---- 索引命中路径（entry.dir 自验，零根列目录）----

    @Test
    fun `index hit keeps findPage and dirFor consistent with the on-disk layout`() {
        // {gid}-{title} 命名 + 同页多扩展：索引建立后所有查询走命中路径
        // （interval > 0 完全跳过 ensureFresh 的指纹检查），返回值与磁盘一致。
        val titled = File(tempDir, "77-Cool Title").apply { mkdirs() }
        File(titled, "0001.webp").writeBytes(byteArrayOf(1, 2, 3))
        File(titled, "0001.jpg").writeBytes(byteArrayOf(1, 2, 3))
        File(titled, "0002.png").writeBytes(byteArrayOf(1, 2, 3))
        index = DownloadDirIndex(config, 60_000)
        index.loadAll()

        assertEquals(2, index.pageCount(77L))
        val first = index.findPage(77L, 0)
        assertNotNull(first)
        assertEquals("0001.jpg", first!!.fileName) // 扩展优先级在命中路径上依旧成立
        assertEquals("jpg", first.ext)
        assertEquals(3L, first.size)
        assertEquals("0002.png", index.findPage(77L, 1)!!.fileName)

        // dirFor 索引直读：返回缓存目录句柄；未知 gid 保持 null 语义。
        assertEquals(titled.absolutePath, index.dirFor(77L)!!.absolutePath)
        assertNull(index.dirFor(999L))

        // 二次访问（纯索引命中）结果一致。
        assertEquals(first, index.findPage(77L, 0))
        assertEquals(titled.absolutePath, index.dirFor(77L)!!.absolutePath)
    }

    // ---- rename 自愈：entry.dir 失效 → findDir 重找 → 重扫 ----

    @Test
    fun `renamed gid directory self-heals on the next lookup`() {
        val dir = gidDir(9L)
        pushFile(9L, "0001.jpg")
        // interval > 0：指纹检查被节流跳过，自愈只能来自 entry.dir 自验失败。
        index = DownloadDirIndex(config, 60_000)
        index.loadAll()
        assertEquals(1, index.pageCount(9L))

        val renamed = File(tempDir, "9-renamed")
        assertTrue(dir.renameTo(renamed), "arrange: rename the gid directory")

        assertEquals(1, index.pageCount(9L))
        assertEquals("0001.jpg", index.findPage(9L, 0)!!.fileName)
        assertEquals(renamed.absolutePath, index.dirFor(9L)!!.absolutePath)
    }

    // ---- TTL 节流：间隔 ≤0 每次都检；>0 到点才做指纹 listFiles ----

    @Test
    fun `ttl zero keeps externally created directories immediately visible`() {
        pushFile(1L, "0001.jpg")
        index.loadAll()
        assertEquals(1, index.pageCount(1L))

        // 间隔 0 = 每次都检：外部新建目录（复制缓存进来）即刻可见，无需重启。
        pushFile(2L, "0001.png")
        assertEquals(1, index.pageCount(2L))
        assertNotNull(index.findPage(2L, 0))
    }

    @Test
    fun `ttl throttle skips the root fingerprint check until the interval elapses`() {
        // 金丝雀：把根目录设为不可读（POSIX r 位；子树 stat 走 x 位不受影响）。
        // 指纹检查一旦运行，listFiles 失败 → 指纹差异 → refresh 清空索引；而
        // 命中路径只 stat 缓存的 dir，完全不碰根列表。以此区分“检查被节流
        // 跳过”与“检查已运行”（注意：假设测试进程非 root）。
        val dir = File(tempDir, "1-titled").apply { mkdirs() }
        File(dir, "0001.jpg").writeBytes(byteArrayOf(1, 2, 3))
        index = DownloadDirIndex(config, 100)
        index.loadAll()
        assertEquals(1, index.pageCount(1L))

        assertTrue(tempDir.setReadable(false), "arrange: hide the root listing")
        try {
            // 间隔内：ensureFresh 直接返回，索引命中照常服务。
            assertEquals(1, index.pageCount(1L))

            // 到点后的下一次请求：指纹检查运行 → refresh 清空 → 占位 {gid}
            // 目录不存在（目录名是 1-titled）→ pageCount 归零。
            Thread.sleep(150)
            assertEquals(0, index.pageCount(1L))
        } finally {
            assertTrue(tempDir.setReadable(true), "cleanup: restore root listing")
        }

        // 恢复可读后按需重扫（miss 路径不受 TTL 节流），自愈回 1 页。
        assertEquals(1, index.pageCount(1L))
    }

    // ---- invalidate：生命周期事件立即重扫，不依赖指纹检查 ----

    @Test
    fun `invalidate forces a rescan even within the ttl window`() {
        gidDir(9L)
        pushFile(9L, "0001.jpg")
        // interval > 0：ensureFresh 被节流跳过，重扫只能由 invalidate 触发。
        index = DownloadDirIndex(config, 60_000)
        index.loadAll()
        assertEquals(1, index.pageCount(9L))

        pushFile(9L, "0002.gif") // 下载生命周期写入的新页
        index.invalidate(9L)

        assertEquals(2, index.pageCount(9L))
        assertEquals("0002.gif", index.findPage(9L, 1)!!.fileName)
    }
}
