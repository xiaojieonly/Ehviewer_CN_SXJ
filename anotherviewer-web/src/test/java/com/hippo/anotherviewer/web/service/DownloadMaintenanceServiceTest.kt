package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.captureK
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
    private lateinit var config: SiteCoreConfigProperties
    private lateinit var publisher: org.springframework.context.ApplicationEventPublisher

    @BeforeEach
    fun setUp() {
        root = File(tempDir, "downloads").apply { mkdirs() }
        // v4（WP3）：回收站在 {dataDir}/trash —— dataDir 指 @TempDir 隔离。
        config = SiteCoreConfigProperties().apply {
            dataDir = tempDir.absolutePath
            download.path = root.absolutePath
        }
        repository = mock(DownloadInfoRepository::class.java)
        publisher = mock(org.springframework.context.ApplicationEventPublisher::class.java)
        service = DownloadMaintenanceService(repository, config, stubProvider("test-user"), publisher, SyncClock())
    }

    /** A7-1: 纯 Mockito 单测不碰 SecurityContext——注入固定用户的 Provider stub。 */
    private fun stubProvider(name: String): com.hippo.anotherviewer.web.config.CurrentUsernameProvider =
        mock(com.hippo.anotherviewer.web.config.CurrentUsernameProvider::class.java)
            .apply { `when`(currentUsername()).thenReturn(name) }

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
        `when`(repository.findAllByDeletedFalseOrderById()).thenReturn(listOf(finishedRow(1, 999)))
        withContent(File(root, "999")) // 行引用 → 保留
        withContent(File(root, "999-Disk Only Title")) // 无行引用时才算冗余？同gid另一目录=副本

        val preview = service.preview()

        // 999 行引用（保留）；999-Disk Only Title 是与该行同 gid 的第二副本 → 冗余。
        assertEquals(listOf("999-Disk Only Title"), preview.redundantFiles.map { it.path })
    }

    @Test
    fun `non-layout or empty directory is flagged redundant`() {
        `when`(repository.findAllByDeletedFalseOrderById()).thenReturn(emptyList())
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
        `when`(repository.findAllByDeletedFalseOrderById()).thenReturn(rows)
        withContent(File(root, "100"))
        withContent(File(root, "777")) // 磁盘-only 无行 → redundant（无行引用）
        File(root, "stray.tmp").writeBytes(ByteArray(5)) // loose file → always redundant

        val preview = service.preview()

        assertEquals(listOf("777", "stray.tmp"), preview.redundantFiles.map { it.path })
    }

    // ── 无效下载判定 ────────────────────────────────────────────

    @Test
    fun `finished row without content dir is invalid as content_dir_missing`() {
        `when`(repository.findAllByDeletedFalseOrderById()).thenReturn(listOf(finishedRow(1, 300)))

        val preview = service.preview()

        val issue = preview.invalidDownloads.single()
        assertEquals(1, issue.id)
        assertEquals("content_dir_missing", issue.reason)
    }

    @Test
    fun `finished row whose pages are all zero-byte is invalid as no_usable_page_files`() {
        `when`(repository.findAllByDeletedFalseOrderById()).thenReturn(listOf(finishedRow(1, 400)))
        withContent(File(root, "400"), zeroByte = true)

        assertEquals("no_usable_page_files", service.preview().invalidDownloads.single().reason)
    }

    @Test
    fun `active and failed rows and healthy finished rows are never invalid`() {
        val paused = finishedRow(1, 500).apply { state = 0 }
        val downloading = finishedRow(2, 501).apply { state = 2 }
        val failed = finishedRow(3, 502).apply { state = 4 } // content missing but retryable
        `when`(repository.findAllByDeletedFalseOrderById()).thenReturn(listOf(paused, downloading, failed))

        assertTrue(service.preview().invalidDownloads.isEmpty())
    }

    // ── 跨机器迁移行（旧主机绝对路径） ─────────────────────────

    @Test
    fun `row migrated from another host resolves to root-gid and is not invalid when content exists`() {
        // 从 macOS 迁移来的行：downloadDir 指向旧主机的绝对路径，本机不存在。
        val migrated = finishedRow(1, 700, dir = File("/Users/bob/AnotherViewer/data/downloads/700"))
        withContent(File(root, "700"))
        `when`(repository.findAllByDeletedFalseOrderById()).thenReturn(listOf(migrated))

        val preview = service.preview()

        assertTrue(preview.invalidDownloads.isEmpty())
        // 且该行在冗余扫描中继续引用 <root>/<gid>，不会把内容目录误报为冗余。
        assertTrue(preview.redundantFiles.isEmpty())
    }

    @Test
    fun `migrated row without local content is still flagged via resolved default dir`() {
        val migrated = finishedRow(2, 800, dir = File("/Users/bob/AnotherViewer/data/downloads/800"))
        `when`(repository.findAllByDeletedFalseOrderById()).thenReturn(listOf(migrated))

        assertEquals("content_dir_missing", service.preview().invalidDownloads.single().reason)
    }

    // ── 执行清理（第二段） ──────────────────────────────────────

    @Test
    fun `clean REDUNDANT_FILES deletes only currently unreferenced entries and reports freed bytes`() {
        val rows = listOf(finishedRow(1, 100))
        withContent(File(root, "100"))
        // 冗余=非本应用布局：0 字节页文件目录（布局有效但内容无效）。
        val junk = withContent(File(root, "888"), zeroByte = true)
        `when`(repository.findAllByDeletedFalseOrderById()).thenReturn(rows)

        val result = service.clean(MaintenanceKind.REDUNDANT_FILES)

        assertEquals(MaintenanceKind.REDUNDANT_FILES, result.kind)
        assertEquals(1, result.removedFiles)
        assertFalse(junk.exists())
        assertEquals(0L, result.freedBytes) // 0字节页文件目录：删除文件数计1、释放0字节
        assertTrue(File(root, "100").isDirectory)
    }

    @Test
    fun `clean INVALID_DOWNLOADS tombstones the row and hands its dir to the trash handler (v4)`() {
        val dir = withContent(File(root, "600"), zeroByte = true)
        val row = finishedRow(9, 600)
        `when`(repository.findAllByDeletedFalseOrderById()).thenReturn(listOf(row))
        `when`(repository.save(org.mockito.ArgumentMatchers.any(DownloadInfoEntity::class.java)))
            .thenAnswer { it.getArgument(0) }

        val result = service.clean(MaintenanceKind.INVALID_DOWNLOADS)

        assertEquals(1, result.removedDownloads)
        // A7-2：行墓碑化（deleted=true + 属主/水位落），不再物理删行。
        org.mockito.Mockito.verify(repository).save(
            org.mockito.ArgumentMatchers.argThat { r: DownloadInfoEntity ->
                r.id == 9L && r.deleted && r.username == "test-user" && r.lastModified > 0
            }
        )
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).deleteById(org.mockito.ArgumentMatchers.anyLong())
        // v4（WP3）：内容目录交回收站（事件在提交后移动）；freedBytes 对移动语义记 0
        // （回收站保留期内并不释放空间），目录此时仍原地存在。
        assertTrue(result.freedBytes == 0L)
        val captor = org.mockito.ArgumentCaptor.forClass(DownloadTombstonedEvent::class.java)
        org.mockito.Mockito.verify(publisher).publishEvent(captureK(captor))
        assertEquals(600L, captor.value.gid)

        TrashMoveHandler(config).onDownloadTombstoned(captor.value)
        assertFalse(dir.exists())
        assertTrue(File(File(tempDir, "trash"), "600").isDirectory)
    }

    @Test
    fun `clean INVALID_DOWNLOADS never deletes a directory outside the downloads root`() {
        val outsideRoot = withContent(File(tempDir, "outside"), zeroByte = true)
        val row = finishedRow(7, 700, dir = outsideRoot)
        `when`(repository.findAllByDeletedFalseOrderById()).thenReturn(listOf(row))
        `when`(repository.save(org.mockito.ArgumentMatchers.any(DownloadInfoEntity::class.java)))
            .thenAnswer { it.getArgument(0) }

        val result = service.clean(MaintenanceKind.INVALID_DOWNLOADS)

        assertEquals(1, result.removedDownloads)
        org.mockito.Mockito.verify(repository).save(
            org.mockito.ArgumentMatchers.argThat { r: DownloadInfoEntity -> r.id == 7L && r.deleted }
        )
        assertTrue(outsideRoot.exists()) // 根外内容只保留不删
        // v4：根外目录不发墓碑事件（绝不搬移/删除 downloads 根之外的文件系统内容）。
        org.mockito.Mockito.verify(publisher, org.mockito.Mockito.never())
            .publishEvent(org.mockito.ArgumentMatchers.any(DownloadTombstonedEvent::class.java))
    }

    @Test
    fun `trash dir under dataDir is outside the downloads root and never scanned (v4)`() {
        // v4（WP3）设计前提：回收站放 {dataDir}/trash 而不是 downloads 根内——
        // scanRedundant 把根下无行引用的直接子目录一律判冗余，trash 若在根内会被
        // 下一次维护扫描当冗余清掉。本用例固化「trash 不在根内、不进 scans」。
        val trash = File(tempDir, "trash").apply { mkdirs() }
        withContent(File(trash, "600-Title"))
        `when`(repository.findAllByDeletedFalseOrderById()).thenReturn(emptyList())

        val preview = service.preview()

        assertTrue(preview.redundantFiles.isEmpty(), "trash 不在 downloads 根下，扫描不到")
        assertTrue(preview.invalidDownloads.isEmpty())
        assertTrue(trash.isDirectory)
    }

    @Test
    fun `tombstoned rows are invisible to the scan and their dirs become redundant`() {
        // A7-2：墓碑行不再保护其内容目录——删除传播后目录交由冗余清理回收；
        // 行本身不进无效下载扫描。
        val tombstone = finishedRow(5, 500).apply { deleted = true }
        withContent(File(root, "500"))
        `when`(repository.findAllByDeletedFalseOrderById()).thenReturn(emptyList())

        val preview = service.preview()

        assertTrue(preview.invalidDownloads.isEmpty())
        assertEquals(listOf("500"), preview.redundantFiles.map { it.path })
    }

    @Test
    fun `re-scan before clean skips entries that became valid after preview`() {
        // 预览时行未落库 → 目录冗余；执行前行已保存（findAll 返回引用）→ 不删。
        val row = finishedRow(1, 800)
        withContent(File(root, "misc-800")) // 非 gid 前缀=杂项，两段均判冗余的对照
        withContent(File(root, "800"))
        `when`(repository.findAllByDeletedFalseOrderById()).thenReturn(emptyList()).thenReturn(listOf(row))

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
        val svc = DownloadMaintenanceService(repository, config, stubProvider("test-user"))
        `when`(repository.findAllByDeletedFalseOrderById()).thenReturn(emptyList())

        val preview = svc.preview()

        assertTrue(preview.redundantFiles.isEmpty())
        assertTrue(preview.invalidDownloads.isEmpty())
    }
}
