package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.UploadCompleteRequest
import com.hippo.anotherviewer.web.dto.UploadInitRequest
import com.hippo.anotherviewer.web.entity.DownloadInfoEntity
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 契约测试 for [DownloadUploadService]（App 推送下载落库与落盘）。
 *
 * - 仓库为内存 fake（与 EhImportServiceTest.RepoFixtures 同款 mock 风格），
 *   下载根目录指向 @TempDir，验证 downloads/<gid>/%04d.<ext> 真实落盘布局。
 * - initUpload：新建行（state=2 + 元数据 + downloadDir 派生）与 existingPages
 *   扫描；非 force 冲突 → success=false 且不写库；force → upsert 保留 id。
 * - storePage：覆盖写、扩展名白名单、page>=1。
 * - completeUpload：state=3 + total/done；无行 → false。
 */
class DownloadUploadServiceTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var repo: DownloadInfoRepository
    private lateinit var store: ConcurrentHashMap<Long, DownloadInfoEntity>
    private lateinit var service: DownloadUploadService

    @BeforeEach
    fun setUp() {
        setUp(uploadEnabled = true)
    }

    private fun setUp(uploadEnabled: Boolean) {
        store = ConcurrentHashMap()
        repo = mock(DownloadInfoRepository::class.java).apply {
            `when`(save(any(DownloadInfoEntity::class.java))).thenAnswer { inv ->
                val e = inv.getArgument<DownloadInfoEntity>(0)
                store[e.gid] = e
                e
            }
            `when`(findByGid(anyLong())).thenAnswer { inv -> store[inv.getArgument<Long>(0)] }
        }
        val config = SiteCoreConfigProperties().apply {
            download.path = tempDir.absolutePath
        }
        val serverConfig = mock(ServerConfigService::class.java).apply {
            `when`(getBoolean(anyString(), anyBoolean())).thenAnswer { inv ->
                inv.getArgument<String>(0) == ServerConfigService.KEY_UPLOAD_ENABLED && uploadEnabled
            }
        }
        service = DownloadUploadService(repo, config, serverConfig)
    }

    private fun downloadDir(gid: Long): File = File(tempDir, "$gid")

    private fun req(
        token: String = "tok123",
        title: String = "Alpha",
        force: Boolean = false,
    ): UploadInitRequest = UploadInitRequest(
        token = token,
        title = title,
        titleJpn = "アルファ",
        thumb = "http://img.ehgt.org/t123.jpg",
        category = 4,
        uploader = "bob",
        rating = 4.5f,
        simpleTags = "female:shion, language:chinese",
        pages = 20,
        label = 0,
        force = force,
    )

    // ── initUpload ───────────────────────────────────────────────

    @Test
    fun `initUpload creates a row with state 2 and derived downloadDir`() {
        val response = service.initUpload(123L, req(), "alice")

        assertTrue(response.success)
        assertTrue(response.existingPages.isEmpty())

        val entity = store.getValue(123L)
        assertEquals("tok123", entity.token)
        assertEquals("Alpha", entity.title)
        assertEquals("アルファ", entity.titleJpn)
        assertEquals("http://img.ehgt.org/t123.jpg", entity.thumb)
        assertEquals(4, entity.category)
        assertEquals("bob", entity.uploader)
        assertEquals(4.5f, entity.rating)
        assertEquals("female:shion, language:chinese", entity.simpleTags)
        assertEquals(20, entity.pages)
        assertEquals(2, entity.state)
        assertEquals("alice", entity.username)
        assertEquals(File(tempDir, "123").absolutePath, entity.downloadDir)
        assertTrue(File(tempDir, "123").isDirectory)
    }

    @Test
    fun `initUpload conflict returns success false and does not write`() {
        store[123L] = DownloadInfoEntity().apply {
            gid = 123L
            token = "old"
            title = "Old"
        }

        val response = service.initUpload(123L, req(), "alice")

        assertFalse(response.success)
        assertEquals("old", store.getValue(123L).token)
        assertEquals("Old", store.getValue(123L).title)
    }

    @Test
    fun `initUpload refuses when upload is disabled`() {
        setUp(uploadEnabled = false)

        val response = service.initUpload(123L, req(), "alice")

        assertFalse(response.success)
        assertTrue(response.message.contains("Upload disabled"))
        assertNull(store[123L])
    }

    @Test
    fun `initUpload with force upserts and keeps the entity identity`() {
        val existing = DownloadInfoEntity().apply {
            id = 99L
            gid = 123L
            token = "old"
            title = "Old"
            state = 3
        }
        store[123L] = existing

        val response = service.initUpload(123L, req(force = true), "alice")

        assertTrue(response.success)
        val entity = store.getValue(123L)
        assertEquals(99L, entity.id)
        assertEquals("tok123", entity.token)
        assertEquals("Alpha", entity.title)
        assertEquals(2, entity.state)
    }

    @Test
    fun `existingPages scans the percent-04d layout sorted`() {
        downloadDir(123L).mkdirs()
        File(downloadDir(123L), "0003.png").writeBytes(byteArrayOf(1))
        File(downloadDir(123L), "0001.jpg").writeBytes(byteArrayOf(1))
        File(downloadDir(123L), "0007.jpeg").writeBytes(byteArrayOf(1))
        File(downloadDir(123L), "not-a-page.txt").writeBytes(byteArrayOf(1))
        File(downloadDir(123L), "0002.jpg").writeBytes(ByteArray(0)) // 空文件不算

        assertEquals(listOf(1, 3, 7), service.existingPages(123L))
    }

    @Test
    fun `initUpload reports existingPages after a previous push`() {
        downloadDir(123L).mkdirs()
        File(downloadDir(123L), "0001.jpg").writeBytes(byteArrayOf(1))

        val response = service.initUpload(123L, req(), "alice")

        assertEquals(listOf(1), response.existingPages)
    }

    // ── storePage ────────────────────────────────────────────────

    @Test
    fun `storePage writes the file preserving the original extension`() {
        service.storePage(123L, 7, "page.PNG", ByteArray(3) { 1 })

        val file = File(downloadDir(123L), "0007.png")
        assertTrue(file.isFile)
        assertTrue(file.readBytes().contentEquals(ByteArray(3) { 1 }))
    }

    @Test
    fun `storePage overwrites an existing page idempotently`() {
        service.storePage(123L, 1, "page.jpg", ByteArray(3) { 1 })
        service.storePage(123L, 1, "page.jpg", ByteArray(5) { 2 })

        val file = File(downloadDir(123L), "0001.jpg")
        assertEquals(5, file.length())
        assertTrue(file.readBytes().contentEquals(ByteArray(5) { 2 }))
    }

    @Test
    fun `storePage rejects page below one`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.storePage(123L, 0, "page.jpg", ByteArray(1))
        }
    }

    @Test
    fun `storePage refuses when upload is disabled`() {
        setUp(uploadEnabled = false)

        assertThrows(IllegalArgumentException::class.java) {
            service.storePage(123L, 1, "page.jpg", ByteArray(1))
        }
        assertFalse(File(downloadDir(123L), "0001.jpg").exists())
    }

    @Test
    fun `storePage rejects an unsupported extension`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.storePage(123L, 1, "page.txt", ByteArray(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.storePage(123L, 1, "page", ByteArray(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.storePage(123L, 1, null, ByteArray(1))
        }
        assertFalse(File(downloadDir(123L), "0001.txt").exists())
    }

    // ── completeUpload ───────────────────────────────────────────

    @Test
    fun `completeUpload marks the row finished with totals`() {
        service.initUpload(123L, req(), "alice")

        assertTrue(service.completeUpload(123L, UploadCompleteRequest(total = 20, done = 20)))

        val entity = store.getValue(123L)
        assertEquals(3, entity.state)
        assertEquals(20, entity.total)
        assertEquals(20, entity.done)
    }

    @Test
    fun `completeUpload returns false when no row exists`() {
        assertFalse(service.completeUpload(123L, UploadCompleteRequest(total = 20, done = 20)))
        assertNull(store[123L])
    }
}
