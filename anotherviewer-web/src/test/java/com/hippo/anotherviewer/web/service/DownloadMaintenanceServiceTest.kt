package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.MaintenanceKind
import com.hippo.anotherviewer.web.entity.DownloadInfoEntity
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File

class DownloadMaintenanceServiceTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var repository: DownloadInfoRepository
    private lateinit var service: DownloadMaintenanceService
    private lateinit var root: File

    @BeforeEach
    fun setUp() {
        root = File(tempDir, "downloads").apply { mkdirs() }
        val config = SiteCoreConfigProperties().apply { download.path = root.absolutePath }
        repository = mock(DownloadInfoRepository::class.java)
        service = DownloadMaintenanceService(repository, config)
    }

    // ── fixtures ────────────────────────────────────────────────

    /** 有内容目录的 FINISHED 行（默认 `<root>/<gid>` 目录 + 两个非空页面）。 */
    private fun finishedRow(id: Long, gid: Long, dir: File? = null): DownloadInfoEntity =
        DownloadInfoEntity().apply {
            this.id = id
            this.gid = gid
            this.token = "t$gid"
            this.title = "Title $gid"
            state = 3
            total = 2
            done = 2
            downloadDir = dir?.absolutePath
        }

    private fun withContent(dir: File, pages: Int = 2, zeroByte: Boolean = false): File =
        dir.apply {
            mkdirs()
            repeat(pages) { i ->
                val f = File(this, "%04d.jpg".format(i + 1))
                f.writeBytes(if (zeroByte) ByteArray(0) else byteArrayOf(1, 2, 3))
            }
        }

    // ── 冗余文件判定 ────────────────────────────────────────────

    @Test
    fun `disk-only directory (no row) is redundant while referenced one is kept`() {
        // 2026-08-31（用户裁决）：行能匹配到目录→不冗余；匹配不到（磁盘-only）→冗余。
        `when`(repository.findAll()).thenReturn(listOf(finishedRow(1, 999)))
        withContent(File(root, "999")) // 行引用 → 保留
        withContent(File(root, "999-Disk Only Title")) // 无行引用时才算冗余？同gid另一目录=副本

        val preview = service.preview()

        // 999 行引用（保留）；999-Disk Only Title 是与该行同 gid 的第二副本 → 冗余。
        // 预览响应路径截断为前 10 字符（防风控傻快方案）。
        assertEquals(listOf("999-Disk O"), preview.redundantFiles.map { it.path })
    }

    @Test
    fun `preview paths are truncated to 10 chars while clean still deletes full entries`() {
        // >10 字符的条目在预览里只见前缀；clean 内部重扫用完整路径，删除不受影响。
        `when`(repository.findAll()).thenReturn(emptyList())
        val junk = withContent(File(root, "a-very-long-directory-name"))

        assertEquals(listOf("a-very-lon"), service.preview().redundantFiles.map { it.path })

        val result = service.clean(MaintenanceKind.REDUNDANT_FILES)
        assertEquals(1, result.removedFiles)
        assertFalse(junk.exists())
    }

    @Test
    fun `non-layout or empty directory is flagged redundant`() {
        `when`(repository.findAll()).thenReturn(emptyList())
        // 非 gid 前缀目录（杂项）→ 冗余；gid 布局但磁盘-only（无行）→ 冗余。
        File(root, "misc-notes").mkdirs()
        File(root, "misc-notes/readme.txt").writeBytes(byteArrayOf(1))
        withContent(File(root, "888"))

        val preview = service.preview()

        assertEquals(listOf("888", "misc-notes"), preview.redundantFiles.map { it.path })
    }

    @Test
    fun `directory referenced by gid or downloadDir is kept while stray files are redundant`() {
        val rows = listOf(
            finishedRow(1, 100),                                  // default dir <root>/100
            finishedRow(2, 200, dir = File(root, "elsewhere-200")) // custom downloadDir
        )
        `when`(repository.findAll()).thenReturn(rows)
        withContent(File(root, "100"))
        withContent(File(root, "777")) // 磁盘-only 无行 → redundant（无行引用）
        File(root, "stray.tmp").writeBytes(ByteArray(5)) // loose file → always redundant

        val preview = service.preview()

        assertEquals(listOf("777", "stray.tmp"), preview.redundantFiles.map { it.path })
    }

    // ── 无效下载判定 ────────────────────────────────────────────

    @Test
    fun `finished row without content dir is invalid as content_dir_missing`() {
        `when`(repository.findAll()).thenReturn(listOf(finishedRow(1, 300)))

        val preview = service.preview()

        val issue = preview.invalidDownloads.single()
        assertEquals(1, issue.id)
        assertEquals("content_dir_missing", issue.reason)
    }

    @Test
    fun `finished row whose pages are all zero-byte is invalid as no_usable_page_files`() {
        `when`(repository.findAll()).thenReturn(listOf(finishedRow(1, 400)))
        withContent(File(root, "400"), zeroByte = true)

        assertEquals("no_usable_page_files", service.preview().invalidDownloads.single().reason)
    }

    @Test
    fun `active and failed rows and healthy finished rows are never invalid`() {
        val paused = finishedRow(1, 500).apply { state = 0 }
        val downloading = finishedRow(2, 501).apply { state = 2 }
        val failed = finishedRow(3, 502).apply { state = 4 } // content missing but retryable
        `when`(repository.findAll()).thenReturn(listOf(paused, downloading, failed))

        assertTrue(service.preview().invalidDownloads.isEmpty())
    }

    // ── 跨机器迁移行（旧主机绝对路径） ─────────────────────────

    @Test
    fun `row migrated from another host resolves to root-gid and is not invalid when content exists`() {
        // 从 macOS 迁移来的行：downloadDir 指向旧主机的绝对路径，本机不存在。
        val migrated = finishedRow(1, 700, dir = File("/Users/bob/AnotherViewer/data/downloads/700"))
        withContent(File(root, "700"))
        `when`(repository.findAll()).thenReturn(listOf(migrated))

        val preview = service.preview()

        assertTrue(preview.invalidDownloads.isEmpty())
        // 且该行在冗余扫描中继续引用 <root>/<gid>，不会把内容目录误报为冗余。
        assertTrue(preview.redundantFiles.isEmpty())
    }

    @Test
    fun `migrated row without local content is still flagged via resolved default dir`() {
        val migrated = finishedRow(2, 800, dir = File("/Users/bob/AnotherViewer/data/downloads/800"))
        `when`(repository.findAll()).thenReturn(listOf(migrated))

        assertEquals("content_dir_missing", service.preview().invalidDownloads.single().reason)
    }

    // ── 执行清理（第二段） ──────────────────────────────────────

    @Test
    fun `clean REDUNDANT_FILES deletes only currently unreferenced entries and reports freed bytes`() {
        val rows = listOf(finishedRow(1, 100))
        withContent(File(root, "100"))
        // 冗余=非本应用布局：0 字节页文件目录（布局有效但内容无效）。
        val junk = withContent(File(root, "888"), zeroByte = true)
        `when`(repository.findAll()).thenReturn(rows)

        val result = service.clean(MaintenanceKind.REDUNDANT_FILES)

        assertEquals(MaintenanceKind.REDUNDANT_FILES, result.kind)
        assertEquals(1, result.removedFiles)
        assertFalse(junk.exists())
        assertEquals(0L, result.freedBytes) // 0字节页文件目录：删除文件数计1、释放0字节
        assertTrue(File(root, "100").isDirectory)
    }

    @Test
    fun `clean INVALID_DOWNLOADS removes row plus its leftover dir`() {
        val dir = withContent(File(root, "600"), zeroByte = true)
        `when`(repository.findAll()).thenReturn(listOf(finishedRow(9, 600)))

        val result = service.clean(MaintenanceKind.INVALID_DOWNLOADS)

        assertEquals(1, result.removedDownloads)
        org.mockito.Mockito.verify(repository).deleteById(9L)
        assertFalse(dir.exists())
        assertTrue(result.freedBytes == 0L) // 全是 0 字节文件，释放字节数为 0
    }

    @Test
    fun `clean INVALID_DOWNLOADS never deletes a directory outside the downloads root`() {
        val outsideRoot = withContent(File(tempDir, "outside"), zeroByte = true)
        `when`(repository.findAll()).thenReturn(listOf(finishedRow(7, 700, dir = outsideRoot)))

        val result = service.clean(MaintenanceKind.INVALID_DOWNLOADS)

        assertEquals(1, result.removedDownloads)
        org.mockito.Mockito.verify(repository).deleteById(7L)
        assertTrue(outsideRoot.exists()) // 根外内容只保留不删
    }

    @Test
    fun `re-scan before clean skips entries that became valid after preview`() {
        // 预览时行未落库 → 目录冗余；执行前行已保存（findAll 返回引用）→ 不删。
        val row = finishedRow(1, 800)
        withContent(File(root, "misc-800")) // 非 gid 前缀=杂项，两段均判冗余的对照
        withContent(File(root, "800"))
        `when`(repository.findAll()).thenReturn(emptyList()).thenReturn(listOf(row))

        // 第一段（无行）：800 磁盘-only=冗余（匹配不到），misc-800 杂项=冗余。
        assertEquals(listOf("800", "misc-800"), service.preview().redundantFiles.map { it.path })
        val result = service.clean(MaintenanceKind.REDUNDANT_FILES) // 第二段重扫

        assertEquals(1, result.removedFiles)
        assertTrue(File(root, "800").isDirectory)
        assertFalse(File(root, "misc-800").exists())
    }

    @Test
    fun `blank or missing downloads path yields empty scan results`() {
        val config = SiteCoreConfigProperties().apply { download.path = "" }
        val svc = DownloadMaintenanceService(repository, config)
        `when`(repository.findAll()).thenReturn(emptyList())

        val preview = svc.preview()

        assertTrue(preview.redundantFiles.isEmpty())
        assertTrue(preview.invalidDownloads.isEmpty())
    }
}
