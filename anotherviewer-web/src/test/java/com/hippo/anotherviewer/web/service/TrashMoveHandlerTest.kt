package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * v4 回收站搬运器（WP3）：下载墓碑事件的内容目录移动/恢复/清除。
 *
 * 关键语义：
 *  - trash 根 = {dataDir}/trash（**不在** downloads 根内——防维护扫描把回收站当
 *    冗余清掉，见 [TrashMoveHandler] 类注释）；
 *  - 源目录不存在 / 位于 downloads 根之外 → 不搬移（返回/跳过，绝不动根外内容）；
 *  - 回收站内重名目录追加 -1/-2 后缀，restore 按候选名找回；
 *  - restore 目标已存在 → 跳过文件移动（只复位行，由调用方完成）。
 */
class TrashMoveHandlerTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var config: SiteCoreConfigProperties
    private lateinit var handler: TrashMoveHandler
    private lateinit var downloadsRoot: File

    @BeforeEach
    fun setUp() {
        downloadsRoot = File(tempDir, "downloads").apply { mkdirs() }
        config = SiteCoreConfigProperties().apply {
            dataDir = tempDir.absolutePath
            download.path = downloadsRoot.absolutePath
        }
        handler = TrashMoveHandler(config)
    }

    private fun galleryDir(name: String, pages: Int = 2): File =
        File(downloadsRoot, name).apply {
            mkdirs()
            repeat(pages) { i -> File(this, "%08d.jpg".format(i + 1)).writeBytes(byteArrayOf(1, 2, 3)) }
        }

    @Test
    fun `tombstoned dir is moved into the trash root after commit`() {
        val dir = galleryDir("600-Title")

        handler.onDownloadTombstoned(DownloadTombstonedEvent(600L, dir.absolutePath))

        assertFalse(dir.exists(), "源目录必须移走（不是就地删除）")
        val trashed = File(File(tempDir, "trash"), "600-Title")
        assertTrue(trashed.isDirectory, "回收站保留可恢复的目录树")
        assertEquals(2, trashed.listFiles()!!.size)
    }

    @Test
    fun `missing source dir is skipped without creating a trash entry`() {
        handler.onDownloadTombstoned(DownloadTombstonedEvent(601L, File(downloadsRoot, "601-Gone").absolutePath))

        assertFalse(File(tempDir, "trash").exists(), "无源目录时不产生垃圾条目")
    }

    @Test
    fun `duplicate names inside the trash get a -1 suffix and restore finds them`() {
        // 第一份已回收，新目录同名再回收 → 追加 -1 后缀，不覆盖既有回收站内容。
        handler.onDownloadTombstoned(DownloadTombstonedEvent(602L, galleryDir("602-Title").absolutePath))
        val second = galleryDir("602-Title")
        File(second, "extra.txt").writeBytes(byteArrayOf(9))
        handler.onDownloadTombstoned(DownloadTombstonedEvent(602L, second.absolutePath))

        val trash = File(tempDir, "trash")
        assertTrue(File(trash, "602-Title").isDirectory)
        assertTrue(File(trash, "602-Title-1").isDirectory)

        // restore：目标不存在 → 从候选（原名或 -N）移回。
        assertTrue(handler.restoreFromTrash(602L, File(downloadsRoot, "602-Title").absolutePath))
        assertTrue(File(downloadsRoot, "602-Title").isDirectory)
    }

    @Test
    fun `restore skips the file move when the target dir already exists`() {
        handler.onDownloadTombstoned(DownloadTombstonedEvent(603L, galleryDir("603-Title").absolutePath))
        val target = galleryDir("603-Title") // 目标已存在（磁盘上已有内容，不覆盖）

        assertFalse(handler.restoreFromTrash(603L, target.absolutePath), "目标已存在 → 跳过文件移动")
        assertEquals(2, target.listFiles()!!.size, "既有目标目录不被回收站副本覆盖")
    }

    @Test
    fun `dir outside the downloads root is never touched`() {
        val outside = File(tempDir, "elsewhere").apply { mkdirs() }
        File(outside, "0001.jpg").writeBytes(byteArrayOf(1))

        handler.onDownloadTombstoned(DownloadTombstonedEvent(604L, outside.absolutePath))

        assertTrue(outside.isDirectory, "downloads 根之外的内容只跳过不移动")
        assertEquals(1, outside.listFiles()!!.size)
    }

    @Test
    fun `purgeTrashDir removes the candidates while orphan purge uses mtime cutoff`() {
        handler.onDownloadTombstoned(DownloadTombstonedEvent(605L, galleryDir("605-Title").absolutePath))
        val trash = File(tempDir, "trash")
        // 孤儿目录：无对应墓碑行的残留（老 mtime 模拟超期）。
        val orphan = File(trash, "999-Orphan").apply { mkdirs() }
        File(orphan, "0001.jpg").writeBytes(byteArrayOf(1))
        orphan.setLastModified(System.currentTimeMillis() - 8L * 86_400_000L)

        assertTrue(handler.purgeTrashDir(605L, File(downloadsRoot, "605-Title").absolutePath))
        assertFalse(File(trash, "605-Title").exists())

        val cutoff = System.currentTimeMillis() - 7L * 86_400_000L
        assertEquals(1, handler.purgeOrphanTrash(cutoff))
        assertFalse(orphan.exists())
    }

    @Test
    fun `blank downloads path makes the handler a no-op`() {
        val noPath = SiteCoreConfigProperties().apply {
            dataDir = tempDir.absolutePath
            download.path = ""
        }
        val dir = galleryDir("606-Title")

        TrashMoveHandler(noPath).onDownloadTombstoned(DownloadTombstonedEvent(606L, dir.absolutePath))

        assertTrue(dir.isDirectory)
    }
}
