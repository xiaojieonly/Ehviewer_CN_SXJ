package com.hippo.anotherviewer.web.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

    // ── 2026-08-30：Android 对齐目录命名 `{gid}-{title}` ──

    @Test
    fun `dirName builds gid-title and falls back to plain gid when title blank`() {
        assertEquals("123-Some Title", DownloadDirs.dirName(123L, "Some Title"))
        assertEquals("123", DownloadDirs.dirName(123L, ""))
        assertEquals("123", DownloadDirs.dirName(123L, null))
    }

    @Test
    fun `dirName sanitizes filesystem-special chars like Android sanitizeFilename`() {
        // 与 Android sanitizeFilename（\ / : * ? " < > | + control 移除）对齐——
        // 方括号/括弧和空格保留，超长/空白标题回落到纯 gid。
        assertEquals(
            "1014380-(C84) [Behind Moon (Q)] DRII Ep.3 Hermes no-komodo",
            DownloadDirs.dirName(
                1014380L,
                "(C84) [Behind Moon (Q)] DRII: Ep.3 \"Hermes\" <no-komodo> \\ | ? *",
            ),
        )
    }

    @Test
    fun `parseGid reads legacy plain and gid-title directory names`() {
        assertEquals(123L, DownloadDirs.parseGid("123"))
        assertEquals(123L, DownloadDirs.parseGid("123-Some Title"))
        assertEquals(123L, DownloadDirs.parseGid("123-(C84) 标题"))
        assertNull(DownloadDirs.parseGid("-123"))
        assertNull(DownloadDirs.parseGid("not-a-number"))
    }

    @Test
    fun `isOursDir accepts both layouts and rejects others`() {
        assertTrue(DownloadDirs.isOursDir("123"))
        assertTrue(DownloadDirs.isOursDir("123-title"))
        assertTrue(!DownloadDirs.isOursDir("title-only"))
        assertTrue(!DownloadDirs.isOursDir(""))
    }

    @Test
    fun `resolve with a title falls back to gid-title dir`() {
        val resolved = DownloadDirs.resolve(root.absolutePath, 7L, "/some/old/nas/dir", "Alpha")
        assertEquals(File(root, "7-Alpha").path, resolved.path)
    }

    // ── 既有目录发现（2026-10-11）：downloadDir 为空/失效时找回 gid-title 目录 ──

    @Test
    fun `findExisting prefers exact gid dir then gid-title`() {
        root.mkdirs()
        File(root, "123-Titled Dir").mkdirs()
        // 只有标题目录：命中 gid-*。
        assertEquals(File(root, "123-Titled Dir").path, DownloadDirs.findExisting(root.absolutePath, 123L)?.path)
        // 精确 <gid> 存在时优先于标题目录（legacy 判定口径）。
        File(root, "123").mkdirs()
        assertEquals(File(root, "123").path, DownloadDirs.findExisting(root.absolutePath, 123L)?.path)
        // 都不存在 → null（新下载走既定 resolve 路径）。
        assertNull(DownloadDirs.findExisting(root.absolutePath, 999L))
    }

    @Test
    fun `findExisting ignores files and unrelated names`() {
        root.mkdirs()
        File(root, "456").writeText("not a dir")
        File(root, "456-extra.txt").writeText("file with prefix")
        assertNull(DownloadDirs.findExisting(root.absolutePath, 456L))
    }

    @Test
    fun `listOurDirs maps gid to dir and skips foreign names`() {
        root.mkdirs()
        File(root, "1").mkdirs()
        File(root, "2-With Title").mkdirs()
        File(root, "module-info").mkdirs()
        File(root, "readme.txt").writeText("x")
        val dirs = DownloadDirs.listOurDirs(root.absolutePath)
        assertEquals(setOf(1L, 2L), dirs.keys)
        assertEquals(File(root, "2-With Title").path, dirs[2L]?.path)
    }
}
