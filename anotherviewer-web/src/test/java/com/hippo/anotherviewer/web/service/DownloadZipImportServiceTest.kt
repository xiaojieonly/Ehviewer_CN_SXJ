package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.entity.DownloadInfoEntity
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.mock.web.MockMultipartFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 契约测试 for [DownloadZipImportService]（Android 缓存 zip 批量导入）。
 *
 * - 仓库为内存 fake（与 DownloadUploadServiceTest 同款 mock 风格），下载根目录
 *   指向 @TempDir 下的 downloads/，验证 downloads/<gid>/%04d.<ext> 真实落盘布局。
 * - 覆盖：目录名 gid 解析、8 位页码归一化 4 位、.ehviewer/.thumb 元数据跳过、
 *   path traversal 拒绝、DB 行 total>0 完成化（state=3）与 total=0 不动。
 */
class DownloadZipImportServiceTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var repo: DownloadInfoRepository
    private lateinit var store: ConcurrentHashMap<Long, DownloadInfoEntity>
    private lateinit var service: DownloadZipImportService

    private val root: File get() = File(tempDir, "downloads")

    @BeforeEach
    fun setUp() {
        store = ConcurrentHashMap()
        repo = mock(DownloadInfoRepository::class.java).apply {
            `when`(save(any(DownloadInfoEntity::class.java))).thenAnswer { inv ->
                val e = inv.getArgument<DownloadInfoEntity>(0)
                store[e.gid] = e
                e
            }
            `when`(findByGid(anyLong())).thenAnswer { inv -> store[inv.getArgument<Long>(0)] }
            `when`(countByState(3)).thenAnswer { store.values.count { it.state == 3 }.toLong() }
        }
        val config = SiteCoreConfigProperties().apply {
            download.path = root.absolutePath
        }
        service = DownloadZipImportService(repo, config, DownloadDirIndex(config))
    }

    private fun downloadDir(gid: Long): File = File(root, "$gid")

    private fun row(
        gid: Long,
        total: Int = 0,
        state: Int = 0,
        id: Long = gid,
        error: String? = null,
    ): DownloadInfoEntity = DownloadInfoEntity().apply {
        this.id = id
        this.gid = gid
        this.total = total
        this.state = state
        this.done = 0
        this.error = error
    }

    private fun multipart(bytes: ByteArray, contentType: String = "application/zip"): MockMultipartFile =
        MockMultipartFile("file", "android-cache.zip", contentType, bytes)

    /** 手写 zip 字节：ZipOutputStream 真 zip（entry 名 → 字节）。 */
    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()

    // ── 基本导入 + 完成化 ─────────────────────────────────────────

    @Test
    fun `imports android cache dir and completes a matching db row`() {
        store[1014380L] = row(1014380L, total = 3, state = 0, error = "old")

        val result = service.importZip(multipart(
            zipOf(
                "1014380-(C84) [Behind Moon (Q)] DRII.../00000001.jpg" to ByteArray(8) { 1 },
                "1014380-(C84) [Behind Moon (Q)] DRII.../00000002.jpg" to ByteArray(8) { 2 },
                "1014380-(C84) [Behind Moon (Q)] DRII.../00000003.jpg" to ByteArray(8) { 3 },
                "1014380-(C84) [Behind Moon (Q)] DRII.../.ehviewer" to ByteArray(4) { 4 },
                "1014380-(C84) [Behind Moon (Q)] DRII.../.thumb" to ByteArray(4) { 5 },
                "1014380-(C84) [Behind Moon (Q)] DRII.../not-a-page.txt" to ByteArray(2),
            )
        ))

        assertEquals(1, result.imported)
        assertEquals(0, result.skipped)
        assertEquals(1, result.verified)
        assertEquals(1, result.completedTotal)

        assertTrue(File(downloadDir(1014380L), "00000001.jpg").isFile)
        assertTrue(File(downloadDir(1014380L), "00000002.jpg").isFile)
        assertTrue(File(downloadDir(1014380L), "00000003.jpg").isFile)
        assertFalse(File(downloadDir(1014380L), ".ehviewer").exists())
        assertFalse(File(downloadDir(1014380L),".thumb").exists())
        assertFalse(File(downloadDir(1014380L), "not-a-page.txt").exists())

        val entity = store.getValue(1014380L)
        assertEquals(3, entity.state)
        assertEquals(3, entity.done)
        assertNull(entity.error)
    }

    @Test
    fun `skips a directory without a numeric gid prefix`() {
        val result = service.importZip(multipart(
            zipOf(
                "Some Gallery Title/00000001.jpg" to ByteArray(8) { 1 },
            )
        ))

        assertEquals(0, result.imported)
        assertEquals(1, result.skipped)
        assertEquals(0, result.verified)
        assertFalse(File(root, "Some Gallery Title").exists())
        assertTrue(root.listFiles().isNullOrEmpty())
    }

    @Test
    fun `preserves 8-digit android file names (storage interop)`() {
        val result = service.importZip(multipart(
            zipOf(
                "1014380-(C84)/00000001.jpg" to ByteArray(8) { 7 },
            )
        ))

        assertEquals(1, result.imported)
        val target = File(downloadDir(1014380L), "00000001.jpg")
        assertTrue(target.isFile)
        assertEquals("00000001.jpg", target.name)
        assertNotNull(target.parentFile?.listFiles()?.firstOrNull())
        assertEquals(1, target.parentFile!!.listFiles()!!.size)
    }

    @Test
    fun `skips a gid directory containing no page files`() {
        val result = service.importZip(multipart(
            zipOf(
                "7788990-meta/readme.txt" to ByteArray(4) { 1 },
            )
        ))

        assertEquals(0, result.imported)
        assertEquals(1, result.skipped)
        assertFalse(downloadDir(7788990L).exists())
    }

    @Test
    fun `mixed zip counts good and bad directories separately`() {
        store[1014380L] = row(1014380L, total = 1)
        val result = service.importZip(multipart(
            zipOf(
                "1014380-(C84)/00000001.jpg" to ByteArray(8) { 1 },
                "Bare Title/00000001.jpg" to ByteArray(8) { 2 },
                "7788990-meta/readme.txt" to ByteArray(4) { 3 },
            )
        ))

        assertEquals(1, result.imported)
        assertEquals(2, result.skipped)
        assertEquals(1, result.verified)
        assertEquals(1, result.completedTotal)
    }

    // ── path traversal ───────────────────────────────────────────

    @Test
    fun `path traversal entry never escapes the download root`() {
        val result = service.importZip(multipart(
            zipOf(
                "../evil.txt" to ByteArray(8) { 1 },
                "1014380-(C84) x/../00000001.jpg" to ByteArray(8) { 2 },
                "1014380-(C84) x/00000002.jpg" to ByteArray(8) { 3 },
            )
        ))

        // ".." 无 gid 前缀 → skipped；合法目录照常导入且只写入 <root>/<gid>/ 之内。
        assertEquals(1, result.imported)
        assertEquals(1, result.skipped)
        assertEquals(0, result.verified)

        // 越界文件不存在：root 之外（tempDir 直接子级）与 root 内均无 evil.txt。
        assertFalse(File(tempDir, "evil.txt").exists())
        assertFalse(File(root, "evil.txt").exists())
        assertTrue(root.walkTopDown().none { it.name == "evil.txt" })
        // 合法页写进了 gid 目录
        assertTrue(File(downloadDir(1014380L), "00000002.jpg").isFile)
    }

    // ── 完成化边界 ────────────────────────────────────────────────

    @Test
    fun `does not complete a row whose total is still zero`() {
        store[1014380L] = row(1014380L, total = 0, state = 0, error = "pending")

        val result = service.importZip(multipart(
            zipOf(
                "1014380-(C84)/00000001.jpg" to ByteArray(8) { 1 },
                "1014380-(C84)/00000002.jpg" to ByteArray(8) { 2 },
            )
        ))

        assertEquals(1, result.imported)
        assertEquals(0, result.verified)
        assertEquals(0, result.completedTotal)

        // total 未决：state 保持原值，留给 restart-all 时机检索页数再校验。
        val entity = store.getValue(1014380L)
        assertEquals(0, entity.state)
        assertEquals(0, entity.done)
        assertEquals("pending", entity.error)
    }

    @Test
    fun `does not verify when the row is missing`() {
        val result = service.importZip(multipart(
            zipOf(
                "1014380-(C84)/00000001.jpg" to ByteArray(8) { 1 },
            )
        ))

        assertEquals(1, result.imported)
        assertEquals(0, result.verified)
        assertEquals(0, result.completedTotal)
        assertTrue(downloadDir(1014380L).exists())
    }

    // ── 输入校验 ──────────────────────────────────────────────────

    @Test
    fun `rejects an empty file`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.importZip(multipart(ByteArray(0)))
        }
        assertTrue(ex.message!!.contains("为空"))
    }

    @Test
    fun `rejects a non-zip content type`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.importZip(multipart(ByteArray(8) { 1 }, contentType = "text/plain"))
        }
        assertTrue(ex.message!!.contains("不支持的文件类型"))
    }

    @Test
    fun `empty zip yields zero counts`() {
        val result = service.importZip(multipart(zipOf()))
        assertEquals(0, result.imported)
        assertEquals(0, result.skipped)
        assertEquals(0, result.verified)
        assertEquals(0, result.completedTotal)
    }
}
