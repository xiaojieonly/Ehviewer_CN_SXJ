package com.hippo.anotherviewer.web.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DownloadDirsTest {

    @TempDir
    lateinit var tempDir: File

    private val root: File get() = File(tempDir, "downloads")

    // ── 跨机器迁移：外来绝对路径回落 <root>/<gid> ──

    @Test
    fun `absolute path from another host falls back to root-gid`() {
        val resolved = DownloadDirs.resolve(root.absolutePath, 3513582L, "/Users/bob/AnotherViewer/./data/downloads/3513582")
        assertEquals(File(root, "3513582").path, resolved.path)
    }

    @Test
    fun `any foreign absolute prefix is ignored`() {
        val resolved = DownloadDirs.resolve(root.absolutePath, 1L, "/some/other/nas/share/1")
        assertEquals(File(root, "1").path, resolved.path)
    }

    // ── 本机正常情形：根内绝对路径原样保留 ──

    @Test
    fun `absolute path inside the current root is kept`() {
        root.mkdirs()
        val inside = File(root, "42").apply { mkdirs() }
        val resolved = DownloadDirs.resolve(root.absolutePath, 42L, inside.absolutePath)
        assertTrue(resolved.canonicalPath == inside.canonicalPath)
    }

    // ── 兼容旧相对路径与缺省 ──

    @Test
    fun `relative stored path keeps resolving against working directory`() {
        val resolved = DownloadDirs.resolve(root.absolutePath, 7L, "./data/downloads/7")
        assertEquals("./data/downloads/7", resolved.path)
    }

    @Test
    fun `null or blank stored path falls back to root-gid`() {
        assertEquals(File(root, "9").path, DownloadDirs.resolve(root.absolutePath, 9L, null).path)
        assertEquals(File(root, "9").path, DownloadDirs.resolve(root.absolutePath, 9L, "").path)
        assertEquals(File(root, "9").path, DownloadDirs.resolve(root.absolutePath, 9L, "   ").path)
    }
}
