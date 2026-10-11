package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.captureK
import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.RecyclePurgeRequest
import com.hippo.anotherviewer.web.dto.RecycleRestoreItem
import com.hippo.anotherviewer.web.dto.RecycleRestoreRequest
import com.hippo.anotherviewer.web.entity.BookmarkInfoEntity
import com.hippo.anotherviewer.web.entity.DownloadInfoEntity
import com.hippo.anotherviewer.web.entity.DownloadLabelEntity
import com.hippo.anotherviewer.web.entity.EhSessionEntity
import com.hippo.anotherviewer.web.entity.FilterEntity
import com.hippo.anotherviewer.web.entity.HistoryInfoEntity
import com.hippo.anotherviewer.web.entity.LocalFavoriteInfoEntity
import com.hippo.anotherviewer.web.entity.QuickSearchEntity
import com.hippo.anotherviewer.web.repository.BookmarkInfoRepository
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import com.hippo.anotherviewer.web.repository.DownloadLabelRepository
import com.hippo.anotherviewer.web.repository.EhSessionRepository
import com.hippo.anotherviewer.web.repository.FilterRepository
import com.hippo.anotherviewer.web.repository.HistoryInfoRepository
import com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository
import com.hippo.anotherviewer.web.repository.QuickSearchRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * v4 回收站（WP3）服务语义回归锁：
 *
 *  - list：分页/字段/剩余天数；ehSession 不在可列实体集（服务器级单例，不进回收站）；
 *  - restore：deleted=false + 服务器新戳 + 属主补落 + 来源记 "server"，且行被增量
 *    pull 的派生查询重新取到（删除的可见性反转）；
 *  - download restore：trash 目录移回；目标已存在 → 只复位行；
 *  - purge：只清到期不清未到期、清 trash 目录、静默清 ehSession 到期墓碑；
 *    指定项立即清除；聚合计数发 SyncUpdateEvent(deviceId="server")。
 */
class RecycleServiceTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var favoriteRepo: LocalFavoriteInfoRepository
    private lateinit var historyRepo: HistoryInfoRepository
    private lateinit var downloadRepo: DownloadInfoRepository
    private lateinit var bookmarkRepo: BookmarkInfoRepository
    private lateinit var filterRepo: FilterRepository
    private lateinit var quickSearchRepo: QuickSearchRepository
    private lateinit var downloadLabelRepo: DownloadLabelRepository
    private lateinit var ehSessionRepo: EhSessionRepository
    private lateinit var clock: SyncClock
    private lateinit var syncService: SyncService
    private lateinit var trash: TrashMoveHandler
    private lateinit var eventPublisher: org.springframework.context.ApplicationEventPublisher
    private lateinit var service: RecycleService
    private lateinit var config: SiteCoreConfigProperties

    @BeforeEach
    fun setUp() {
        favoriteRepo = fakeFavoriteRepo()
        historyRepo = fakeHistoryRepo()
        downloadRepo = fakeDownloadRepo()
        bookmarkRepo = fakeBookmarkRepo()
        filterRepo = fakeFilterRepo()
        quickSearchRepo = fakeQuickSearchRepo()
        downloadLabelRepo = fakeDownloadLabelRepo()
        ehSessionRepo = fakeEhSessionRepo()
        clock = SyncClock()
        syncService = mock(SyncService::class.java)
        config = SiteCoreConfigProperties().apply {
            dataDir = tempDir.absolutePath
            download.path = File(tempDir, "downloads").apply { mkdirs() }.absolutePath
        }
        trash = TrashMoveHandler(config)
        eventPublisher = mock(org.springframework.context.ApplicationEventPublisher::class.java)
        service = RecycleService(
            favoriteRepo, historyRepo, downloadRepo, bookmarkRepo, filterRepo,
            quickSearchRepo, downloadLabelRepo, ehSessionRepo,
            clock, syncService, trash, eventPublisher, retentionDays = 7,
        )
    }

    // ── list ────────────────────────────────────────────────────

    @Test
    fun `list pages tombstones newest first with per-entity keys titles and remaining days`() {
        seedFavorite(gid = 1, deleted = true, lastModified = clock.nowPlus(-2L * DAY))
        seedFavorite(gid = 2, deleted = true, lastModified = clock.nowPlus(-1L * DAY))
        seedFavorite(gid = 3, deleted = false, lastModified = clock.nowPlus(-1L * DAY)) // 活行不进回收站
        seedBookmarkTombstone(gid = 4, category = 5, lastModified = clock.nowPlus(-3L * DAY))
        seedFilterTombstone(type = 1, text = "artist:foo", lastModified = clock.nowPlus(-1L))
        seedQuickSearchTombstone(name = "preset", lastModified = clock.nowPlus(-1L))
        seedLabelTombstone(label = "L1", lastModified = clock.nowPlus(-1L))
        seedHistoryTombstone(gid = 5, title = "Hist 5", lastModified = clock.nowPlus(-1L))
        seedDownloadTombstone(gid = 6, title = "Dl 6", lastModified = clock.nowPlus(-1L))

        val page = service.list("A", "favorites", page = 0, pageSize = 1)
        assertEquals(2, page.total)
        assertEquals(1, page.items.size)
        assertEquals("2", page.items[0].key, "按 lastModified 倒序")
        assertEquals("Fav 2", page.items[0].title)
        assertEquals(6L, page.items[0].remainingDays, "1 天前删除 → 剩 6 天")

        val older = service.list("A", "favorites", page = 1, pageSize = 1).items.single()
        assertEquals("1", older.key)
        assertEquals("Fav 1", older.title)
        assertEquals(5L, older.remainingDays, "2 天前删除 → 剩 5 天")

        // 各实体的 key/title 编码。
        assertEquals(listOf("4-5"), service.list("A", "bookmarks", 0, 20).items.map { it.title })
        assertEquals(listOf("1:artist:foo"), service.list("A", "filters", 0, 20).items.map { it.key })
        assertEquals(listOf("preset"), service.list("A", "quickSearches", 0, 20).items.map { it.key })
        assertEquals(listOf("L1"), service.list("A", "downloadLabels", 0, 20).items.map { it.key })
        assertEquals(listOf("5"), service.list("A", "history", 0, 20).items.map { it.key })
        assertEquals("Dl 6", service.list("A", "downloads", 0, 20).items.single().title)
        // 只列当前用户的墓碑。
        assertTrue(service.list("B", "favorites", 0, 20).items.isEmpty())
    }

    @Test
    fun `list clamps pageSize and rejects unknown entities including ehSession`() {
        seedFavorite(gid = 1, deleted = true, lastModified = clock.nowPlus(-1L))

        assertEquals(200, service.list("A", "favorites", 0, 9999).pageSize)
        assertEquals(1, service.list("A", "favorites", 0, 0).pageSize)

        // ehSession 是服务器级单例会话：墓碑不进回收站（无 list 入口）→ 400 语义。
        assertThrows(IllegalArgumentException::class.java) { service.list("A", "ehSession", 0, 20) }
        assertThrows(IllegalArgumentException::class.java) { service.list("A", null, 0, 20) }
    }

    // ── restore ─────────────────────────────────────────────────

    @Test
    fun `restore flips the tombstone back and the row becomes visible to incremental pulls`() {
        seedFavorite(gid = 7, deleted = true, lastModified = 1_000, username = null)
        val before = clock.current()

        val response = service.restore("A", RecycleRestoreRequest(listOf(RecycleRestoreItem("favorites", "7"))))

        assertEquals(1, response.restored)
        assertEquals(0, response.failed)
        val row = favoriteRepo.findByGid(7)!!
        assertFalse(row.deleted)
        assertEquals("A", row.username, "属主补落")
        assertTrue(row.lastModified > before, "服务器新戳")
        // 行级来源记 "server"（服务器自身动作）。
        verify(syncService).stampServerProvenance("A", SyncService.TAG_FAVORITE, "7")
        // 增量 pull 的派生查询能重新取到该行（复活是可见事件）。
        assertTrue(
            favoriteRepo.findByUsernameAndLastModifiedGreaterThan("A", before).any { it.gid == 7L },
            "restore 后行必须被增量 pull 取到",
        )
        // 聚合计数发 sync.update。
        val captor = ArgumentCaptor.forClass(SyncUpdateEvent::class.java)
        verify(eventPublisher).publishEvent(captureK(captor))
        assertEquals("server", captor.value.deviceId)
        assertEquals(1, captor.value.counts["favorites"])
    }

    @Test
    fun `restore counts failures for unknown or non-tombstoned items`() {
        seedFavorite(gid = 8, deleted = false, lastModified = 1_000)

        val response = service.restore(
            "A",
            RecycleRestoreRequest(
                listOf(
                    RecycleRestoreItem("favorites", "8"),     // 活行（不是墓碑）
                    RecycleRestoreItem("favorites", "999"),   // 无行
                    RecycleRestoreItem("bogus", "1"),         // 非法实体
                    RecycleRestoreItem("favorites", "not-a-gid"),
                )
            ),
        )

        assertEquals(0, response.restored)
        assertEquals(4, response.failed)
        verify(eventPublisher, never()).publishEvent(any<Any>())
    }

    @Test
    fun `restore of a download moves its content dir back from the trash`() {
        val dir = File(File(tempDir, "downloads"), "9-Del Title").apply {
            mkdirs()
            File(this, "00000001.jpg").writeBytes(byteArrayOf(1, 2, 3))
        }
        seedDownloadTombstone(gid = 9, title = "Del Title", lastModified = 1_000, downloadDir = dir.absolutePath)
        trash.onDownloadTombstoned(DownloadTombstonedEvent(9L, dir.absolutePath))
        assertFalse(dir.exists())

        val response = service.restore("A", RecycleRestoreRequest(listOf(RecycleRestoreItem("downloads", "9"))))

        assertEquals(1, response.restored)
        assertFalse(downloadRepo.findByGid(9)!!.deleted)
        assertTrue(dir.isDirectory, "内容目录从回收站移回")
        assertTrue(File(dir, "00000001.jpg").isFile)
    }

    @Test
    fun `restore of a download with an existing target dir only resets the row`() {
        val dir = File(File(tempDir, "downloads"), "10-Del Title").apply { mkdirs() }
        seedDownloadTombstone(gid = 10, title = "Del Title", lastModified = 1_000, downloadDir = dir.absolutePath)
        // 回收站里有同名副本，但目标目录已存在（例如行删除后重新下载过）。
        trash.onDownloadTombstoned(DownloadTombstonedEvent(10L, dir.absolutePath))
        dir.mkdirs() // 行删除后重新下载过：目标目录重建（回收站副本仍在）
        File(dir, "00000001.jpg").writeBytes(byteArrayOf(1))
        val trashed = File(File(tempDir, "trash"), "10-Del Title")
        assertTrue(trashed.isDirectory)

        val response = service.restore("A", RecycleRestoreRequest(listOf(RecycleRestoreItem("downloads", "10"))))

        assertEquals(1, response.restored)
        assertFalse(downloadRepo.findByGid(10)!!.deleted)
        assertTrue(File(dir, "00000001.jpg").isFile, "既有目标内容保留")
        assertTrue(trashed.isDirectory, "目标已存在 → 跳过文件移动（回收站副本留在原处）")
    }

    // ── purge ───────────────────────────────────────────────────

    @Test
    fun `purge without items clears only expired tombstones and their trash dirs`() {
        val expired = clock.nowPlus(-8L * DAY)
        val fresh = clock.nowPlus(-1L * DAY)
        seedFavorite(gid = 1, deleted = true, lastModified = expired)
        seedFavorite(gid = 2, deleted = true, lastModified = fresh)
        seedHistoryTombstone(gid = 3, title = "old", lastModified = expired)
        val dir = File(File(tempDir, "downloads"), "4-Dl").apply { mkdirs() }
        seedDownloadTombstone(gid = 4, title = "Dl", lastModified = expired, downloadDir = dir.absolutePath)
        trash.onDownloadTombstoned(DownloadTombstonedEvent(4L, dir.absolutePath))
        val trashed = File(File(tempDir, "trash"), "4-Dl")
        assertTrue(trashed.isDirectory)
        // ehSession 到期墓碑：静默物理清除（不进回收站）。
        seedEhSessionTombstone(lastModified = expired)

        val response = service.purge("A", RecyclePurgeRequest(items = null))

        assertEquals(3, response.purged)
        assertEquals(null, favoriteRepo.findByGid(1), "到期墓碑物理删除")
        assertNotNull(favoriteRepo.findByGid(2), "未到期墓碑保留")
        assertTrue(favoriteRepo.findByGid(2)!!.deleted, "未到期墓碑仍是墓碑（不复活、不清除）")
        assertEquals(null, historyRepo.findByGid(3))
        assertEquals(null, downloadRepo.findByGid(4))
        assertFalse(trashed.exists(), "download 的 trash 目录一并清除")
        assertTrue(ehSessionRepo.findByUsername("server") == null, "ehSession 到期墓碑静默清除")
        val captor = ArgumentCaptor.forClass(SyncUpdateEvent::class.java)
        verify(eventPublisher).publishEvent(captureK(captor))
        assertEquals("server", captor.value.deviceId)
        assertEquals(1, captor.value.counts["favorites"])
        assertEquals(1, captor.value.counts["downloads"])
        assertEquals(1, captor.value.counts["ehSession"])
    }

    @Test
    fun `purge with explicit items deletes immediately regardless of age`() {
        seedQuickSearchTombstone(name = "fresh", lastModified = clock.nowPlus(-1L))
        seedLabelTombstone(label = "L9", lastModified = clock.nowPlus(-1L))

        val response = service.purge(
            "A",
            RecyclePurgeRequest(items = listOf(RecycleRestoreItem("quickSearches", "fresh"), RecycleRestoreItem("downloadLabels", "L9"))),
        )

        assertEquals(2, response.purged)
        assertEquals(null, quickSearchRepo.findByName("fresh"))
        assertEquals(null, downloadLabelRepo.findByLabel("L9"))
    }

    @Test
    fun `purge with an empty item list behaves like the scheduled sweep`() {
        seedFavorite(gid = 11, deleted = true, lastModified = clock.nowPlus(-8L * DAY))

        assertEquals(1, service.purge("A", RecyclePurgeRequest(items = emptyList())).purged)
        assertEquals(null, favoriteRepo.findByGid(11))
    }

    // ── fixtures ────────────────────────────────────────────────

    private companion object {
        const val DAY = 86_400_000L
    }

    /** SyncClock 没有可设时间；用「现在 - offset」表达历史水位（语义 = offset 毫秒前删除）。 */
    private fun SyncClock.nowPlus(offsetMs: Long): Long = System.currentTimeMillis() + offsetMs

    private fun seedFavorite(gid: Long, deleted: Boolean, lastModified: Long, username: String? = "A") {
        favoriteRepo.save(
            LocalFavoriteInfoEntity().apply {
                this.gid = gid; token = "tok$gid"; title = "Fav $gid"
                this.deleted = deleted; this.lastModified = lastModified; this.username = username
            }
        )
    }

    private fun seedHistoryTombstone(gid: Long, title: String, lastModified: Long) {
        historyRepo.save(
            HistoryInfoEntity().apply {
                this.gid = gid; token = "tok$gid"; this.title = title
                deleted = true; this.lastModified = lastModified; username = "A"
            }
        )
    }

    private fun seedBookmarkTombstone(gid: Long, category: Int, lastModified: Long) {
        bookmarkRepo.save(
            BookmarkInfoEntity().apply {
                this.gid = gid; token = "tok$gid"; this.category = category
                deleted = true; this.lastModified = lastModified; username = "A"
            }
        )
    }

    private fun seedFilterTombstone(type: Int, text: String, lastModified: Long) {
        filterRepo.save(
            FilterEntity().apply {
                this.type = type; this.text = text
                deleted = true; this.lastModified = lastModified; username = "A"
            }
        )
    }

    private fun seedQuickSearchTombstone(name: String, lastModified: Long) {
        quickSearchRepo.save(
            QuickSearchEntity().apply {
                this.name = name; deleted = true; this.lastModified = lastModified; username = "A"
            }
        )
    }

    private fun seedLabelTombstone(label: String, lastModified: Long) {
        downloadLabelRepo.save(
            DownloadLabelEntity().apply {
                this.label = label; deleted = true; this.lastModified = lastModified; username = "A"
            }
        )
    }

    private fun seedDownloadTombstone(gid: Long, title: String, lastModified: Long, downloadDir: String? = null) {
        downloadRepo.save(
            DownloadInfoEntity().apply {
                this.gid = gid; token = "tok$gid"; this.title = title
                deleted = true; this.lastModified = lastModified; username = "A"
                this.downloadDir = downloadDir
            }
        )
    }

    private fun seedEhSessionTombstone(lastModified: Long) {
        ehSessionRepo.save(
            EhSessionEntity().apply {
                username = "server"; cookies = ""; deleted = true; this.lastModified = lastModified
            }
        )
    }

    // ── in-memory repository fakes ──────────────────────────────

    private fun fakeFavoriteRepo(): LocalFavoriteInfoRepository {
        val repo = mock(LocalFavoriteInfoRepository::class.java)
        val store = ConcurrentHashMap<Long, LocalFavoriteInfoEntity>()
        `when`(repo.save(any(LocalFavoriteInfoEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<LocalFavoriteInfoEntity>(0); store[e.gid] = e; e
        }
        `when`(repo.findByGid(anyLong())).thenAnswer { inv -> store[inv.getArgument<Long>(0)] }
        `when`(repo.findAllByGid(anyLong())).thenAnswer { inv ->
            store[inv.getArgument<Long>(0)]?.let { listOf(it) } ?: emptyList<LocalFavoriteInfoEntity>()
        }
        `when`(repo.findByUsernameAndDeletedTrueOrderById(anyString())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.deleted }.sortedBy { it.id }
        }
        `when`(repo.findByDeletedTrueAndLastModifiedBefore(anyLong())).thenAnswer { inv ->
            store.values.filter { it.deleted && it.lastModified < inv.getArgument<Long>(0) }
        }
        `when`(repo.findByUsernameAndLastModifiedGreaterThan(anyString(), anyLong())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.lastModified > inv.getArgument<Long>(1) }
        }
        org.mockito.Mockito.doAnswer { inv -> store.remove(inv.getArgument<LocalFavoriteInfoEntity>(0).gid) }
            .`when`(repo).delete(any(LocalFavoriteInfoEntity::class.java))
        org.mockito.Mockito.doAnswer { inv ->
            inv.getArgument<List<LocalFavoriteInfoEntity>>(0).forEach { store.remove(it.gid) }
        }.`when`(repo).deleteAll(any<List<LocalFavoriteInfoEntity>>())
        return repo
    }

    private fun fakeHistoryRepo(): HistoryInfoRepository {
        val repo = mock(HistoryInfoRepository::class.java)
        val store = ConcurrentHashMap<Long, HistoryInfoEntity>()
        `when`(repo.save(any(HistoryInfoEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<HistoryInfoEntity>(0); store[e.gid] = e; e
        }
        `when`(repo.findByGid(anyLong())).thenAnswer { inv -> store[inv.getArgument<Long>(0)] }
        `when`(repo.findAllByGid(anyLong())).thenAnswer { inv ->
            store[inv.getArgument<Long>(0)]?.let { listOf(it) } ?: emptyList<HistoryInfoEntity>()
        }
        `when`(repo.findByUsernameAndDeletedTrueOrderById(anyString())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.deleted }.sortedBy { it.id }
        }
        `when`(repo.findByDeletedTrueAndLastModifiedBefore(anyLong())).thenAnswer { inv ->
            store.values.filter { it.deleted && it.lastModified < inv.getArgument<Long>(0) }
        }
        org.mockito.Mockito.doAnswer { inv ->
            inv.getArgument<List<HistoryInfoEntity>>(0).forEach { store.remove(it.gid) }
        }.`when`(repo).deleteAll(any<List<HistoryInfoEntity>>())
        return repo
    }

    private fun fakeDownloadRepo(): DownloadInfoRepository {
        val repo = mock(DownloadInfoRepository::class.java)
        val store = ConcurrentHashMap<Long, DownloadInfoEntity>()
        `when`(repo.save(any(DownloadInfoEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<DownloadInfoEntity>(0); store[e.gid] = e; e
        }
        `when`(repo.findByGid(anyLong())).thenAnswer { inv -> store[inv.getArgument<Long>(0)] }
        `when`(repo.findAllByGid(anyLong())).thenAnswer { inv ->
            store[inv.getArgument<Long>(0)]?.let { listOf(it) } ?: emptyList<DownloadInfoEntity>()
        }
        `when`(repo.findByUsernameAndDeletedTrueOrderById(anyString())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.deleted }.sortedBy { it.id }
        }
        `when`(repo.findByDeletedTrueAndLastModifiedBefore(anyLong())).thenAnswer { inv ->
            store.values.filter { it.deleted && it.lastModified < inv.getArgument<Long>(0) }
        }
        org.mockito.Mockito.doAnswer { inv -> store.remove(inv.getArgument<DownloadInfoEntity>(0).gid) }
            .`when`(repo).delete(any(DownloadInfoEntity::class.java))
        org.mockito.Mockito.doAnswer { inv ->
            inv.getArgument<List<DownloadInfoEntity>>(0).forEach { store.remove(it.gid) }
        }.`when`(repo).deleteAll(any<List<DownloadInfoEntity>>())
        return repo
    }

    private fun fakeBookmarkRepo(): BookmarkInfoRepository {
        val repo = mock(BookmarkInfoRepository::class.java)
        val store = ConcurrentHashMap<Long, BookmarkInfoEntity>()
        `when`(repo.save(any(BookmarkInfoEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<BookmarkInfoEntity>(0); store[e.gid] = e; e
        }
        `when`(repo.findByUsernameAndDeletedTrueOrderById(anyString())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.deleted }.sortedBy { it.id }
        }
        `when`(repo.findByDeletedTrueAndLastModifiedBefore(anyLong())).thenAnswer { inv ->
            store.values.filter { it.deleted && it.lastModified < inv.getArgument<Long>(0) }
        }
        return repo
    }

    private fun fakeFilterRepo(): FilterRepository {
        val repo = mock(FilterRepository::class.java)
        val store = ConcurrentHashMap<String, FilterEntity>()
        val idCounter = AtomicLong(1)
        `when`(repo.save(any(FilterEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<FilterEntity>(0)
            if (e.id == 0L) e.id = idCounter.getAndIncrement()
            store["${e.type}:${e.text}"] = e
            e
        }
        `when`(repo.findByTypeAndTextIgnoreCaseOrderByLastModifiedDesc(org.mockito.ArgumentMatchers.anyInt(), anyString())).thenAnswer { inv ->
            store.values
                .filter { it.type == inv.getArgument<Int>(0) && it.text == inv.getArgument<String>(1) }
                .sortedByDescending { it.lastModified }
        }
        `when`(repo.findByUsernameAndDeletedTrueOrderById(anyString())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.deleted }.sortedBy { it.id }
        }
        `when`(repo.findByDeletedTrueAndLastModifiedBefore(anyLong())).thenAnswer { inv ->
            store.values.filter { it.deleted && it.lastModified < inv.getArgument<Long>(0) }
        }
        return repo
    }

    private fun fakeQuickSearchRepo(): QuickSearchRepository {
        val repo = mock(QuickSearchRepository::class.java)
        val store = ConcurrentHashMap<String, QuickSearchEntity>()
        `when`(repo.save(any(QuickSearchEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<QuickSearchEntity>(0); store[e.name] = e; e
        }
        `when`(repo.findByName(anyString())).thenAnswer { inv -> store[inv.getArgument<String>(0)] }
        `when`(repo.findByUsernameAndDeletedTrueOrderById(anyString())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.deleted }.sortedBy { it.id }
        }
        `when`(repo.findByDeletedTrueAndLastModifiedBefore(anyLong())).thenAnswer { inv ->
            store.values.filter { it.deleted && it.lastModified < inv.getArgument<Long>(0) }
        }
        org.mockito.Mockito.doAnswer { inv -> store.remove(inv.getArgument<QuickSearchEntity>(0).name) }
            .`when`(repo).delete(any(QuickSearchEntity::class.java))
        return repo
    }

    private fun fakeDownloadLabelRepo(): DownloadLabelRepository {
        val repo = mock(DownloadLabelRepository::class.java)
        val store = ConcurrentHashMap<String, DownloadLabelEntity>()
        `when`(repo.save(any(DownloadLabelEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<DownloadLabelEntity>(0); store[e.label] = e; e
        }
        `when`(repo.findByLabel(anyString())).thenAnswer { inv -> store[inv.getArgument<String>(0)] }
        `when`(repo.findByUsernameAndDeletedTrueOrderById(anyString())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.deleted }.sortedBy { it.id }
        }
        `when`(repo.findByDeletedTrueAndLastModifiedBefore(anyLong())).thenAnswer { inv ->
            store.values.filter { it.deleted && it.lastModified < inv.getArgument<Long>(0) }
        }
        org.mockito.Mockito.doAnswer { inv -> store.remove(inv.getArgument<DownloadLabelEntity>(0).label) }
            .`when`(repo).delete(any(DownloadLabelEntity::class.java))
        return repo
    }

    private fun fakeEhSessionRepo(): EhSessionRepository {
        val repo = mock(EhSessionRepository::class.java)
        val store = ConcurrentHashMap<String, EhSessionEntity>()
        `when`(repo.save(any(EhSessionEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<EhSessionEntity>(0); store[e.username] = e; e
        }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv -> store[inv.getArgument<String>(0)] }
        `when`(repo.findByDeletedTrueAndLastModifiedBefore(anyLong())).thenAnswer { inv ->
            store.values.filter { it.deleted && it.lastModified < inv.getArgument<Long>(0) }
        }
        org.mockito.Mockito.doAnswer { inv ->
            inv.getArgument<List<EhSessionEntity>>(0).forEach { store.remove(it.username) }
        }.`when`(repo).deleteAll(any<List<EhSessionEntity>>())
        return repo
    }
}
