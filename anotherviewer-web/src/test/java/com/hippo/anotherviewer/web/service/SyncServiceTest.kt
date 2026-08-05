package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.captureK
import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.ConflictStrategy
import com.hippo.anotherviewer.web.dto.SyncBookmarkDto
import com.hippo.anotherviewer.web.dto.SyncDownloadDto
import com.hippo.anotherviewer.web.dto.SyncDownloadLabelDto
import com.hippo.anotherviewer.web.dto.SyncEhSessionDto
import com.hippo.anotherviewer.web.dto.SyncEntityCollection
import com.hippo.anotherviewer.web.dto.SyncFavoriteDto
import com.hippo.anotherviewer.web.dto.SyncFilterDto
import com.hippo.anotherviewer.web.dto.SyncHistoryDto
import com.hippo.anotherviewer.web.dto.SyncPolicyDto
import com.hippo.anotherviewer.web.dto.SyncPreferencesDto
import com.hippo.anotherviewer.web.dto.SyncPushRequest
import com.hippo.anotherviewer.web.dto.SyncPushResponse
import com.hippo.anotherviewer.web.dto.SyncQuickSearchDto
import com.hippo.anotherviewer.web.entity.BookmarkInfoEntity
import com.hippo.anotherviewer.web.entity.DownloadInfoEntity
import com.hippo.anotherviewer.web.entity.DownloadLabelEntity
import com.hippo.anotherviewer.web.entity.FilterEntity
import com.hippo.anotherviewer.web.entity.HistoryInfoEntity
import com.hippo.anotherviewer.web.entity.LocalFavoriteInfoEntity
import com.hippo.anotherviewer.web.entity.QuickSearchEntity
import com.hippo.anotherviewer.web.entity.ServerConfigEntity
import com.hippo.anotherviewer.web.entity.SyncDeviceEntity
import com.hippo.anotherviewer.web.entity.UserPreferenceEntity
import com.hippo.anotherviewer.web.repository.BookmarkInfoRepository
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import com.hippo.anotherviewer.web.repository.DownloadLabelRepository
import com.hippo.anotherviewer.web.repository.EhSessionRepository
import com.hippo.anotherviewer.web.repository.FilterRepository
import com.hippo.anotherviewer.web.repository.HistoryInfoRepository
import com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository
import com.hippo.anotherviewer.web.repository.QuickSearchRepository
import com.hippo.anotherviewer.web.repository.ServerConfigRepository
import com.hippo.anotherviewer.web.repository.SyncDeviceRepository
import com.hippo.anotherviewer.web.repository.UserPreferenceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * Contract tests for [SyncService] merge semantics, per
 * `contracts/sync-conflict-rules.md`:
 *
 *  - SKEW_TOLERANCE = 5000 ms; timestamps within the window are simultaneous.
 *  - Favorites / Downloads / Filters / QuickSearches / DownloadLabels: union
 *    merge with soft-delete tombstones (a tombstone never kills a live row;
 *    a live push resurrects a tombstone).
 *  - History / Bookmarks: LWW with server-side tombstone rows — a
 *    `deleted: true` push marks the stored row deleted and bumps
 *    `lastModified` (or stores the tombstone if none exists), so incremental
 *    pulls (`since > 0`) propagate deletions; a newer live push resurrects.
 *  - Within-skew tie-breakers: history keeps the later view time, bookmarks
 *    the higher page, filters enabled=true.
 *  - Entities are scoped per user; legacy null-username rows are claimed by
 *    the first user to push.
 *
 * All repositories are in-memory fakes keyed by the natural idempotency key
 * (gid / (mode,text) / name / label / deviceId), so assertions read back the
 * persisted state through the mocked repository interface.
 *
 * v2 注：契约 v2 缺省策略为 device_priority（A）；本套件断言的是 v1.0 语义，
 * 即策略 B（lww）路径，故 setUp 显式把策略钉为 lww——v1 完整回退兜底（契约 §1.4）。
 * A/C 与 policy 端点的行为矩阵见 SyncStrategyMatrixTest。
 */
class SyncServiceTest {

    private lateinit var favoriteRepo: LocalFavoriteInfoRepository
    private lateinit var historyRepo: HistoryInfoRepository
    private lateinit var downloadRepo: DownloadInfoRepository
    private lateinit var bookmarkRepo: BookmarkInfoRepository
    private lateinit var filterRepo: FilterRepository
    private lateinit var quickSearchRepo: QuickSearchRepository
    private lateinit var downloadLabelRepo: DownloadLabelRepository
    private lateinit var deviceRepo: SyncDeviceRepository
    private lateinit var preferenceRepo: UserPreferenceRepository
    private lateinit var preferenceService: UserPreferenceService
    private lateinit var siteSessionManager: SiteSessionManager
    private lateinit var service: SyncService

    @BeforeEach
    fun setUp() {
        favoriteRepo = fakeFavoriteRepo()
        historyRepo = fakeHistoryRepo()
        downloadRepo = fakeDownloadRepo()
        bookmarkRepo = fakeBookmarkRepo()
        filterRepo = fakeFilterRepo()
        quickSearchRepo = fakeQuickSearchRepo()
        downloadLabelRepo = fakeDownloadLabelRepo()
        deviceRepo = fakeDeviceRepo()
        preferenceRepo = fakePreferenceRepo()
        preferenceService = UserPreferenceService(preferenceRepo)
        siteSessionManager = mock(SiteSessionManager::class.java)
        service = SyncService(
            favoriteRepo, historyRepo, downloadRepo, bookmarkRepo, filterRepo,
            quickSearchRepo, downloadLabelRepo, deviceRepo, preferenceRepo, preferenceService,
            fakeServerConfig(),
            mock(EhSessionRepository::class.java),
            siteSessionManager,
        )
        // v1 回归套件钉在策略 B（lww）= v1.0 完整语义（契约 §1.4 回退兜底）。
        service.updatePolicy(SyncPolicyDto(conflictStrategy = ConflictStrategy.LWW))
    }

    /** Real [ServerConfigService] over an in-memory [ServerConfigRepository] fake. */
    private fun fakeServerConfig(): ServerConfigService {
        val repo = mock(ServerConfigRepository::class.java)
        val store = ConcurrentHashMap<String, ServerConfigEntity>()
        `when`(repo.findById(anyString())).thenAnswer { inv ->
            Optional.ofNullable(store[inv.getArgument<String>(0)])
        }
        `when`(repo.save(any(ServerConfigEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<ServerConfigEntity>(0)
            store[e.key] = e
            e
        }
        `when`(repo.existsById(anyString())).thenAnswer { inv ->
            store.containsKey(inv.getArgument<String>(0))
        }
        return ServerConfigService(repo, EncryptionService(), SiteCoreConfigProperties())
    }

    // ---- In-memory repository fakes ----

    private fun fakeFavoriteRepo(): LocalFavoriteInfoRepository {
        val repo = mock(LocalFavoriteInfoRepository::class.java)
        val store = ConcurrentHashMap<Long, LocalFavoriteInfoEntity>()
        `when`(repo.save(any(LocalFavoriteInfoEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<LocalFavoriteInfoEntity>(0)
            store[e.gid] = e
            e
        }
        `when`(repo.findByGid(anyLong())).thenAnswer { inv -> store[inv.getArgument<Long>(0)] }
        `when`(repo.findAllByUsernameIsNull()).thenAnswer { store.values.filter { it.username == null } }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv -> store.values.filter { it.username == inv.getArgument<String>(0) } }
        `when`(repo.findByUsernameAndLastModifiedGreaterThan(anyString(), anyLong())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.lastModified > inv.getArgument<Long>(1) }
        }
        doAnswer { inv ->
            store.entries.removeIf { it.value === inv.getArgument<LocalFavoriteInfoEntity>(0) }
        }.`when`(repo).delete(any(LocalFavoriteInfoEntity::class.java))
        return repo
    }

    private fun fakeHistoryRepo(): HistoryInfoRepository {
        val repo = mock(HistoryInfoRepository::class.java)
        val store = ConcurrentHashMap<Long, HistoryInfoEntity>()
        `when`(repo.save(any(HistoryInfoEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<HistoryInfoEntity>(0)
            store[e.gid] = e
            e
        }
        `when`(repo.findByGid(anyLong())).thenAnswer { inv -> store[inv.getArgument<Long>(0)] }
        `when`(repo.findAllByUsernameIsNull()).thenAnswer { store.values.filter { it.username == null } }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv -> store.values.filter { it.username == inv.getArgument<String>(0) } }
        `when`(repo.findByUsernameAndLastModifiedGreaterThan(anyString(), anyLong())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.lastModified > inv.getArgument<Long>(1) }
        }
        doAnswer { inv ->
            store.entries.removeIf { it.value === inv.getArgument<HistoryInfoEntity>(0) }
        }.`when`(repo).delete(any(HistoryInfoEntity::class.java))
        return repo
    }

    private fun fakeDownloadRepo(): DownloadInfoRepository {
        val repo = mock(DownloadInfoRepository::class.java)
        val store = ConcurrentHashMap<Long, DownloadInfoEntity>()
        `when`(repo.save(any(DownloadInfoEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<DownloadInfoEntity>(0)
            store[e.gid] = e
            e
        }
        `when`(repo.findByGid(anyLong())).thenAnswer { inv -> store[inv.getArgument<Long>(0)] }
        `when`(repo.findAllByUsernameIsNull()).thenAnswer { store.values.filter { it.username == null } }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv -> store.values.filter { it.username == inv.getArgument<String>(0) } }
        `when`(repo.findByUsernameAndLastModifiedGreaterThan(anyString(), anyLong())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.lastModified > inv.getArgument<Long>(1) }
        }
        doAnswer { inv ->
            store.entries.removeIf { it.value === inv.getArgument<DownloadInfoEntity>(0) }
        }.`when`(repo).delete(any(DownloadInfoEntity::class.java))
        return repo
    }

    private fun fakeBookmarkRepo(): BookmarkInfoRepository {
        val repo = mock(BookmarkInfoRepository::class.java)
        val store = ConcurrentHashMap<Long, BookmarkInfoEntity>()
        `when`(repo.save(any(BookmarkInfoEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<BookmarkInfoEntity>(0)
            store[e.gid] = e
            e
        }
        `when`(repo.findByGid(anyLong())).thenAnswer { inv -> store[inv.getArgument<Long>(0)] }
        `when`(repo.findAllByUsernameIsNull()).thenAnswer { store.values.filter { it.username == null } }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv -> store.values.filter { it.username == inv.getArgument<String>(0) } }
        `when`(repo.findByUsernameAndLastModifiedGreaterThan(anyString(), anyLong())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.lastModified > inv.getArgument<Long>(1) }
        }
        doAnswer { inv ->
            store.entries.removeIf { it.value === inv.getArgument<BookmarkInfoEntity>(0) }
        }.`when`(repo).delete(any(BookmarkInfoEntity::class.java))
        return repo
    }

    private fun fakeFilterRepo(): FilterRepository {
        val repo = mock(FilterRepository::class.java)
        val store = ConcurrentHashMap<String, FilterEntity>()
        `when`(repo.save(any(FilterEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<FilterEntity>(0)
            store["${e.type}:${e.text}"] = e
            e
        }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        `when`(repo.findAllByUsernameIsNull()).thenAnswer { store.values.filter { it.username == null } }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv -> store.values.filter { it.username == inv.getArgument<String>(0) } }
        `when`(repo.findByUsernameAndLastModifiedGreaterThan(anyString(), anyLong())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.lastModified > inv.getArgument<Long>(1) }
        }
        doAnswer { inv ->
            store.entries.removeIf { it.value === inv.getArgument<FilterEntity>(0) }
        }.`when`(repo).delete(any(FilterEntity::class.java))
        return repo
    }

    private fun fakeQuickSearchRepo(): QuickSearchRepository {
        val repo = mock(QuickSearchRepository::class.java)
        val store = ConcurrentHashMap<String, QuickSearchEntity>()
        `when`(repo.save(any(QuickSearchEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<QuickSearchEntity>(0)
            store[e.name] = e
            e
        }
        `when`(repo.findByName(anyString())).thenAnswer { inv -> store[inv.getArgument<String>(0)] }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        `when`(repo.findAllByUsernameIsNull()).thenAnswer { store.values.filter { it.username == null } }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv -> store.values.filter { it.username == inv.getArgument<String>(0) } }
        `when`(repo.findByUsernameAndLastModifiedGreaterThan(anyString(), anyLong())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.lastModified > inv.getArgument<Long>(1) }
        }
        return repo
    }

    private fun fakeDownloadLabelRepo(): DownloadLabelRepository {
        val repo = mock(DownloadLabelRepository::class.java)
        val store = ConcurrentHashMap<String, DownloadLabelEntity>()
        val idCounter = java.util.concurrent.atomic.AtomicLong(1)
        `when`(repo.save(any(DownloadLabelEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<DownloadLabelEntity>(0)
            if (e.id == 0L) e.id = idCounter.getAndIncrement()
            store[e.label] = e
            e
        }
        `when`(repo.findByLabel(anyString())).thenAnswer { inv -> store[inv.getArgument<String>(0)] }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        `when`(repo.findAllByUsernameIsNull()).thenAnswer { store.values.filter { it.username == null } }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv -> store.values.filter { it.username == inv.getArgument<String>(0) } }
        `when`(repo.findByUsernameAndLastModifiedGreaterThan(anyString(), anyLong())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.lastModified > inv.getArgument<Long>(1) }
        }
        return repo
    }

    private fun fakePreferenceRepo(): UserPreferenceRepository {
        val repo = mock(UserPreferenceRepository::class.java)
        val store = ConcurrentHashMap<String, UserPreferenceEntity>()
        `when`(repo.save(any(UserPreferenceEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<UserPreferenceEntity>(0)
            store[e.username] = e
            e
        }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv -> store[inv.getArgument<String>(0)] }
        return repo
    }

    private fun fakeDeviceRepo(): SyncDeviceRepository {
        val repo = mock(SyncDeviceRepository::class.java)
        val store = ConcurrentHashMap<String, SyncDeviceEntity>()
        `when`(repo.save(any(SyncDeviceEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<SyncDeviceEntity>(0)
            store[e.deviceId] = e
            e
        }
        `when`(repo.findByDeviceId(anyString())).thenAnswer { inv -> store[inv.getArgument<String>(0)] }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        return repo
    }

    // ---- DTO builders ----

    private fun fav(gid: Long, lastModified: Long = 1_000, deleted: Boolean = false, title: String = "Fav $gid") =
        SyncFavoriteDto(gid = gid, token = "tok$gid", title = title, lastModified = lastModified, deleted = deleted)

    private fun hist(gid: Long, time: Long = 0, lastModified: Long = 1_000, deleted: Boolean = false, title: String = "Hist $gid") =
        SyncHistoryDto(gid = gid, token = "tok$gid", title = title, time = time, lastModified = lastModified, deleted = deleted)

    private fun dl(gid: Long, lastModified: Long = 1_000, deleted: Boolean = false, state: Int = 0, finished: Int = 0, label: String? = null, title: String = "Dl $gid") =
        SyncDownloadDto(gid = gid, token = "tok$gid", title = title, state = state, finished = finished, label = label, lastModified = lastModified, deleted = deleted)

    private fun bm(gid: Long, page: Int = 0, lastModified: Long = 1_000, deleted: Boolean = false) =
        SyncBookmarkDto(gid = gid, token = "tok$gid", page = page, lastModified = lastModified, deleted = deleted)

    private fun flt(mode: Int, text: String, enabled: Boolean = true, lastModified: Long = 1_000, deleted: Boolean = false) =
        SyncFilterDto(mode = mode, text = text, enabled = enabled, lastModified = lastModified, deleted = deleted)

    private fun qs(name: String, lastModified: Long = 1_000, deleted: Boolean = false, keyword: String? = null) =
        SyncQuickSearchDto(name = name, keyword = keyword, lastModified = lastModified, deleted = deleted)

    private fun lbl(label: String, lastModified: Long = 1_000, deleted: Boolean = false, time: Long = 0) =
        SyncDownloadLabelDto(label = label, time = time, lastModified = lastModified, deleted = deleted)

    // ---- Seed helpers (pre-existing server state) ----

    private fun seedFavorite(gid: Long, lastModified: Long, deleted: Boolean = false, username: String? = "A", title: String = "Fav $gid") {
        LocalFavoriteInfoEntity().apply {
            this.gid = gid
            this.token = "tok$gid"
            this.title = title
            this.lastModified = lastModified
            this.deleted = deleted
            this.username = username
        }.let { favoriteRepo.save(it) }
    }

    private fun seedHistory(gid: Long, lastModified: Long, time: Long = 0, username: String? = "A", title: String = "Hist $gid") {
        HistoryInfoEntity().apply {
            this.gid = gid
            this.token = "tok$gid"
            this.title = title
            this.time = time
            this.lastModified = lastModified
            this.username = username
        }.let { historyRepo.save(it) }
    }

    private fun seedDownload(gid: Long, lastModified: Long, deleted: Boolean = false, state: Int = 0, username: String? = "A", title: String = "Dl $gid") {
        DownloadInfoEntity().apply {
            this.gid = gid
            this.token = "tok$gid"
            this.title = title
            this.state = state
            this.lastModified = lastModified
            this.deleted = deleted
            this.username = username
        }.let { downloadRepo.save(it) }
    }

    private fun seedBookmark(gid: Long, lastModified: Long, page: Int = 0, username: String? = "A") {
        BookmarkInfoEntity().apply {
            this.gid = gid
            this.token = "tok$gid"
            this.note = page.toString()
            this.lastModified = lastModified
            this.username = username
        }.let { bookmarkRepo.save(it) }
    }

    private fun seedFilter(mode: Int, text: String, lastModified: Long, enabled: Boolean = true, username: String? = "A") {
        FilterEntity().apply {
            this.type = mode
            this.text = text
            this.enabled = enabled
            this.lastModified = lastModified
            this.username = username
        }.let { filterRepo.save(it) }
    }

    private fun seedQuickSearch(name: String, lastModified: Long, deleted: Boolean = false, keyword: String? = null, username: String? = "A") {
        QuickSearchEntity().apply {
            this.name = name
            this.keyword = keyword
            this.lastModified = lastModified
            this.deleted = deleted
            this.username = username
        }.let { quickSearchRepo.save(it) }
    }

    private fun seedLabel(label: String, lastModified: Long, deleted: Boolean = false, time: Long = 0, username: String? = "A") {
        DownloadLabelEntity().apply {
            this.label = label
            this.time = time
            this.lastModified = lastModified
            this.deleted = deleted
            this.username = username
        }.let { downloadLabelRepo.save(it) }
    }

    // ---- Push helper: clears recorded invocations first so verify(never) sees only this push ----

    private fun push(
        username: String,
        deviceId: String = "android-test",
        favorites: List<SyncFavoriteDto> = emptyList(),
        history: List<SyncHistoryDto> = emptyList(),
        downloads: List<SyncDownloadDto> = emptyList(),
        bookmarks: List<SyncBookmarkDto> = emptyList(),
        filters: List<SyncFilterDto> = emptyList(),
        quickSearches: List<SyncQuickSearchDto> = emptyList(),
        downloadLabels: List<SyncDownloadLabelDto> = emptyList(),
        ehSession: List<SyncEhSessionDto> = emptyList(),
        preferences: SyncPreferencesDto? = null,
    ): SyncPushResponse {
        clearInvocations(
            favoriteRepo, historyRepo, downloadRepo, bookmarkRepo, filterRepo,
            quickSearchRepo, downloadLabelRepo, deviceRepo, preferenceRepo,
        )
        return service.push(
            SyncPushRequest(
                entities = SyncEntityCollection(
                    favorites = favorites,
                    history = history,
                    downloads = downloads,
                    bookmarks = bookmarks,
                    filters = filters,
                    quickSearches = quickSearches,
                    downloadLabels = downloadLabels,
                    ehSession = ehSession,
                    preferences = preferences,
                ),
                deviceId = deviceId,
                timestamp = System.currentTimeMillis(),
            ),
            username,
        )
    }

    // ==================== mergeDownload ====================

    @Test
    fun `download push of a new gid stores the entity under the pushing user`() {
        push("A", downloads = listOf(dl(1, lastModified = 1_000, state = 2)))

        val captor = ArgumentCaptor.forClass(DownloadInfoEntity::class.java)
        verify(downloadRepo).save(captureK(captor))
        val saved = captor.value
        assertEquals(1L, saved.gid)
        assertEquals("A", saved.username)
        assertEquals(2, saved.state)
        assertFalse(saved.deleted)
        assertEquals(1_000L, saved.lastModified)
    }

    @Test
    fun `download tombstone with no local record is stored, not deleted`() {
        val response = push("A", downloads = listOf(dl(2, lastModified = 2_000, deleted = true)))

        val stored = downloadRepo.findByGid(2)!!
        assertTrue(stored.deleted)
        verify(downloadRepo).save(any(DownloadInfoEntity::class.java))
        verify(downloadRepo, never()).delete(any(DownloadInfoEntity::class.java))
        assertEquals(0, response.conflicts)
    }

    @Test
    fun `download union keeps an alive row when incoming is a tombstone`() {
        seedDownload(gid = 3, lastModified = 1_000, state = 1)
        val response = push("A", downloads = listOf(dl(3, lastModified = 2_000, deleted = true)))

        val stored = downloadRepo.findByGid(3)!!
        assertFalse(stored.deleted)
        assertEquals(1, stored.state)
        verify(downloadRepo, never()).save(any(DownloadInfoEntity::class.java))
        assertEquals(0, response.conflicts)
    }

    @Test
    fun `download resurrects a tombstoned row`() {
        seedDownload(gid = 4, lastModified = 1_000, deleted = true)
        val response = push("A", downloads = listOf(dl(4, lastModified = 2_000, deleted = false, state = 2)))

        val stored = downloadRepo.findByGid(4)!!
        assertFalse(stored.deleted)
        assertEquals(2, stored.state)
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `download LWW beyond skew applies incoming state fields`() {
        seedDownload(gid = 5, lastModified = 1_000, state = 1, title = "Old")
        val response = push("A", downloads = listOf(dl(5, lastModified = 6_001, state = 3, title = "New")))

        val stored = downloadRepo.findByGid(5)!!
        assertEquals(3, stored.state)
        assertEquals("New", stored.title)
        assertEquals(6_001L, stored.lastModified)
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `download within skew keeps the existing record`() {
        seedDownload(gid = 6, lastModified = 1_000, state = 1)
        val response = push("A", downloads = listOf(dl(6, lastModified = 3_000, state = 3)))

        assertEquals(1, downloadRepo.findByGid(6)!!.state)
        verify(downloadRepo, never()).save(any(DownloadInfoEntity::class.java))
        assertEquals(0, response.conflicts)
    }

    // ==================== mergeFilter ====================

    @Test
    fun `filter deduplicates on the mode plus text key`() {
        push("A", filters = listOf(flt(mode = 1, text = "artist:foo", enabled = true, lastModified = 1_000)))
        push("A", filters = listOf(flt(mode = 1, text = "artist:foo", enabled = false, lastModified = 2_000)))

        val rows = filterRepo.findAll()
        assertEquals(1, rows.size)
        // Within skew the additive bias keeps the enabled version.
        assertTrue(rows[0].enabled)
        assertEquals(1_000L, rows[0].lastModified)
    }

    @Test
    fun `filter tombstone is stored for an unknown key`() {
        push("A", filters = listOf(flt(mode = 9, text = "zzz", deleted = true, lastModified = 2_000)))

        val row = filterRepo.findAll().single()
        assertTrue(row.deleted)
        assertEquals("A", row.username)
    }

    @Test
    fun `filter within skew prefers enabled true (additive bias)`() {
        seedFilter(mode = 2, text = "bar", lastModified = 1_000, enabled = false)
        val response = push("A", filters = listOf(flt(mode = 2, text = "bar", enabled = true, lastModified = 2_000)))

        assertTrue(filterRepo.findAll().single().enabled)
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `filter LWW beyond skew applies incoming state`() {
        seedFilter(mode = 3, text = "baz", lastModified = 1_000, enabled = true)
        push("A", filters = listOf(flt(mode = 3, text = "baz", enabled = false, lastModified = 6_001)))

        val row = filterRepo.findAll().single()
        assertFalse(row.enabled)
        assertEquals(6_001L, row.lastModified)
    }

    @Test
    fun `filter keeps the local version when it is much newer`() {
        seedFilter(mode = 4, text = "q", lastModified = 6_001, enabled = true)
        push("A", filters = listOf(flt(mode = 4, text = "q", enabled = false, lastModified = 1_000)))

        assertTrue(filterRepo.findAll().single().enabled)
        verify(filterRepo, never()).save(any(FilterEntity::class.java))
    }

    // ==================== mergeBookmark ====================

    @Test
    fun `bookmark delete keeps a tombstone row and bumps lastModified`() {
        seedBookmark(gid = 7, lastModified = 1_000, page = 10)
        val response = push("A", bookmarks = listOf(bm(7, page = 10, lastModified = 2_000, deleted = true)))

        val stored = bookmarkRepo.findByGid(7)!!
        assertTrue(stored.deleted)
        assertEquals(2_000L, stored.lastModified)
        verify(bookmarkRepo, never()).delete(any(BookmarkInfoEntity::class.java))
        assertEquals(0, response.conflicts)
    }

    @Test
    fun `bookmark delete of an unknown gid stores a tombstone`() {
        push("A", bookmarks = listOf(bm(12, page = 3, lastModified = 2_000, deleted = true)))

        val stored = bookmarkRepo.findByGid(12)!!
        assertTrue(stored.deleted)
        assertEquals(2_000L, stored.lastModified)
    }

    @Test
    fun `bookmark tombstone reaches an incremental pull after the bump`() {
        seedBookmark(gid = 11, lastModified = 1_000, page = 10)
        push("A", bookmarks = listOf(bm(11, page = 10, lastModified = 2_000, deleted = true)))

        val pulled = service.pull(1_500, "A", "android-test").entities.bookmarks
        assertEquals(listOf(11L), pulled.map { it.gid })
        assertTrue(pulled[0].deleted)
    }

    @Test
    fun `bookmark LWW beyond skew applies incoming fields`() {
        seedBookmark(gid = 8, lastModified = 1_000, page = 5)
        val response = push("A", bookmarks = listOf(bm(8, page = 3, lastModified = 6_001)))

        val stored = bookmarkRepo.findByGid(8)!!
        assertEquals("3", stored.note)
        assertEquals(6_001L, stored.lastModified)
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `bookmark within skew prefers the higher page`() {
        seedBookmark(gid = 9, lastModified = 1_000, page = 10)
        val response = push("A", bookmarks = listOf(bm(9, page = 50, lastModified = 2_000)))

        assertEquals("50", bookmarkRepo.findByGid(9)!!.note)
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `bookmark within skew keeps the higher local page`() {
        seedBookmark(gid = 10, lastModified = 1_000, page = 40)
        push("A", bookmarks = listOf(bm(10, page = 5, lastModified = 2_000)))

        assertEquals("40", bookmarkRepo.findByGid(10)!!.note)
        verify(bookmarkRepo, never()).save(any(BookmarkInfoEntity::class.java))
    }

    // ==================== mergeQuickSearch ====================

    @Test
    fun `quick search deduplicates on the name key`() {
        push("A", quickSearches = listOf(qs("preset1", lastModified = 1_000)))
        // Same key, clearly newer: the row is updated in place, not duplicated.
        push("A", quickSearches = listOf(qs("preset1", lastModified = 6_001, keyword = "new")))

        assertEquals(1, quickSearchRepo.findAll().size)
        assertEquals("new", quickSearchRepo.findByName("preset1")!!.keyword)
    }

    @Test
    fun `quick search tombstone is stored for an unknown name`() {
        push("A", quickSearches = listOf(qs("preset2", deleted = true, lastModified = 2_000)))

        assertTrue(quickSearchRepo.findByName("preset2")!!.deleted)
    }

    @Test
    fun `quick search LWW beyond skew applies incoming fields`() {
        seedQuickSearch("preset3", lastModified = 1_000, keyword = "old")
        push("A", quickSearches = listOf(qs("preset3", lastModified = 6_001, keyword = "new")))

        assertEquals("new", quickSearchRepo.findByName("preset3")!!.keyword)
    }

    @Test
    fun `quick search resurrects a tombstoned preset`() {
        seedQuickSearch("preset4", lastModified = 1_000, deleted = true)
        val response = push("A", quickSearches = listOf(qs("preset4", deleted = false, lastModified = 2_000)))

        assertFalse(quickSearchRepo.findByName("preset4")!!.deleted)
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `quick search keeps the local version when it is much newer`() {
        seedQuickSearch("preset5", lastModified = 6_001, keyword = "old")
        push("A", quickSearches = listOf(qs("preset5", lastModified = 1_000, keyword = "new")))

        assertEquals("old", quickSearchRepo.findByName("preset5")!!.keyword)
        verify(quickSearchRepo, never()).save(any(QuickSearchEntity::class.java))
    }

    // ==================== mergeDownloadLabel ====================

    @Test
    fun `download label deduplicates on the label key and survives tombstone pushes`() {
        push("A", downloadLabels = listOf(lbl("L1", lastModified = 1_000)))
        push("A", downloadLabels = listOf(lbl("L1", lastModified = 2_000, deleted = true)))

        val rows = downloadLabelRepo.findAll()
        assertEquals(1, rows.size)
        // Union: one side alive keeps the label alive.
        assertFalse(rows[0].deleted)
    }

    @Test
    fun `download label tombstone is stored for an unknown label`() {
        push("A", downloadLabels = listOf(lbl("L2", deleted = true, lastModified = 2_000)))

        assertTrue(downloadLabelRepo.findByLabel("L2")!!.deleted)
    }

    @Test
    fun `download label LWW beyond skew applies incoming fields`() {
        seedLabel("L3", lastModified = 1_000, time = 111)
        push("A", downloadLabels = listOf(lbl("L3", lastModified = 6_001, time = 999)))

        assertEquals(999, downloadLabelRepo.findByLabel("L3")!!.time)
    }

    @Test
    fun `download label resurrects a tombstoned label`() {
        seedLabel("L4", lastModified = 1_000, deleted = true)
        val response = push("A", downloadLabels = listOf(lbl("L4", deleted = false, lastModified = 2_000)))

        assertFalse(downloadLabelRepo.findByLabel("L4")!!.deleted)
        assertEquals(1, response.conflicts)
    }

    // ==================== ehSession（ADR-0004）====================

    @Test
    fun `ehSession push delegates to the session manager merge`() {
        val incoming = SyncEhSessionDto(
            cookies = listOf(
                com.hippo.anotherviewer.web.dto.SyncEhSessionCookieDto(
                    name = "ipb_member_id", value = "111", domain = "e-hentai.org", path = "/",
                    expiresAt = 0,
                ),
            ),
            lastModified = 1_000,
            deviceId = "android-test",
            deleted = false,
        )
        `when`(siteSessionManager.applySyncEhSession(incoming, "android-test")).thenReturn(true)

        val response = push("A", ehSession = listOf(incoming))

        verify(siteSessionManager).applySyncEhSession(incoming, "android-test")
        // 覆盖存量行计一次冲突，与其它 merge 的 conflicts 语义一致。
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `ehSession is absent from the collection when the session manager has none`() {
        `when`(siteSessionManager.loadSyncEhSession()).thenReturn(null)

        val pulled = service.pull(0, "A")

        assertEquals(0, pulled.entities.ehSession.size)
    }

    @Test
    fun `ehSession pull includes the persisted session for a full pull`() {
        val session = SyncEhSessionDto(
            cookies = listOf(
                com.hippo.anotherviewer.web.dto.SyncEhSessionCookieDto(
                    name = "ipb_pass_hash", value = "aaa", domain = "exhentai.org", path = "/",
                    expiresAt = 0,
                ),
            ),
            lastModified = 2_000,
            deviceId = "server",
            deleted = false,
        )
        `when`(siteSessionManager.loadSyncEhSession()).thenReturn(session)

        val pulled = service.pull(0, "A")

        assertEquals(1, pulled.entities.ehSession.size)
        assertEquals(2_000L, pulled.entities.ehSession[0].lastModified)
    }

    @Test
    fun `ehSession pull applies the incremental since filter`() {
        val session = SyncEhSessionDto(lastModified = 2_000, deleted = false)
        `when`(siteSessionManager.loadSyncEhSession()).thenReturn(session)

        val pulled = service.pull(1_000, "A")

        assertEquals(1, pulled.entities.ehSession.size)

        val stale = SyncEhSessionDto(lastModified = 500, deleted = false)
        `when`(siteSessionManager.loadSyncEhSession()).thenReturn(stale)
        val pulledStale = service.pull(1_000, "A")
        assertEquals(0, pulledStale.entities.ehSession.size)
    }

    // ==================== favorites / history key semantics ====================

    @Test
    fun `favorite tombstone is stored for an unknown gid`() {
        push("A", favorites = listOf(fav(40, lastModified = 2_000, deleted = true)))

        val stored = favoriteRepo.findByGid(40)!!
        assertTrue(stored.deleted)
        assertEquals("A", stored.username)
    }

    @Test
    fun `favorite union keeps an alive row when incoming is a tombstone`() {
        seedFavorite(gid = 41, lastModified = 1_000)
        val response = push("A", favorites = listOf(fav(41, lastModified = 2_000, deleted = true)))

        val stored = favoriteRepo.findByGid(41)!!
        assertFalse(stored.deleted)
        verify(favoriteRepo, never()).save(any(LocalFavoriteInfoEntity::class.java))
        assertEquals(0, response.conflicts)
    }

    @Test
    fun `favorite LWW beyond skew applies incoming metadata`() {
        seedFavorite(gid = 42, lastModified = 1_000, title = "Old")
        val response = push("A", favorites = listOf(fav(42, lastModified = 6_001, title = "New")))

        val stored = favoriteRepo.findByGid(42)!!
        assertEquals("New", stored.title)
        assertEquals(6_001L, stored.lastModified)
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `favorite within skew keeps the existing record`() {
        seedFavorite(gid = 44, lastModified = 1_000, title = "Old")
        push("A", favorites = listOf(fav(44, lastModified = 3_000, title = "New")))

        val stored = favoriteRepo.findByGid(44)!!
        assertEquals("Old", stored.title)
        verify(favoriteRepo, never()).save(any(LocalFavoriteInfoEntity::class.java))
    }

    @Test
    fun `favorite resurrects a tombstoned row`() {
        seedFavorite(gid = 43, lastModified = 1_000, deleted = true)
        val response = push("A", favorites = listOf(fav(43, lastModified = 2_000, deleted = false)))

        assertFalse(favoriteRepo.findByGid(43)!!.deleted)
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `history delete keeps a tombstone row and bumps lastModified`() {
        seedHistory(gid = 50, lastModified = 1_000)
        val response = push("A", history = listOf(hist(50, lastModified = 2_000, deleted = true)))

        val stored = historyRepo.findByGid(50)!!
        assertTrue(stored.deleted)
        assertEquals(2_000L, stored.lastModified)
        verify(historyRepo, never()).delete(any(HistoryInfoEntity::class.java))
        assertEquals(0, response.conflicts)
    }

    @Test
    fun `history delete of an unknown gid stores a tombstone`() {
        push("A", history = listOf(hist(54, lastModified = 2_000, deleted = true)))

        val stored = historyRepo.findByGid(54)!!
        assertTrue(stored.deleted)
        assertEquals(2_000L, stored.lastModified)
        assertEquals("A", stored.username)
    }

    @Test
    fun `re-pushing the same history tombstone stays idempotent`() {
        seedHistory(gid = 59, lastModified = 1_000)
        push("A", history = listOf(hist(59, lastModified = 2_000, deleted = true)))
        push("A", history = listOf(hist(59, lastModified = 2_000, deleted = true)))

        val stored = historyRepo.findByGid(59)!!
        assertTrue(stored.deleted)
        assertEquals(2_000L, stored.lastModified)
        verify(historyRepo, never()).delete(any(HistoryInfoEntity::class.java))
    }

    @Test
    fun `history tombstone reaches an incremental pull after the bump`() {
        seedHistory(gid = 55, lastModified = 1_000)
        push("A", history = listOf(hist(55, lastModified = 2_000, deleted = true)))

        val pulled = service.pull(1_500, "A", "android-test").entities.history
        assertEquals(listOf(55L), pulled.map { it.gid })
        assertTrue(pulled[0].deleted)
        assertEquals(2_000L, pulled[0].lastModified)
    }

    @Test
    fun `full pull returns history tombstones with deleted flag`() {
        seedHistory(gid = 56, lastModified = 1_000)
        push("A", history = listOf(hist(56, lastModified = 2_000, deleted = true)))

        val pulled = service.pull(0, "A", "android-test").entities.history
        assertEquals(listOf(56L), pulled.map { it.gid })
        assertTrue(pulled.single().deleted)
    }

    @Test
    fun `full pull includes records with lastModified zero`() {
        seedHistory(gid = 57, lastModified = 0)
        seedHistory(gid = 58, lastModified = 5)

        val all = service.pull(0, "A", "android-test").entities.history
        assertEquals(setOf(57L, 58L), all.map { it.gid }.toSet())

        val incremental = service.pull(1, "A", "android-test").entities.history
        assertEquals(listOf(58L), incremental.map { it.gid })
    }

    @Test
    fun `history LWW beyond skew applies incoming fields`() {
        seedHistory(gid = 51, lastModified = 1_000, time = 100, title = "Old")
        val response = push("A", history = listOf(hist(51, time = 50, lastModified = 6_001, title = "New")))

        val stored = historyRepo.findByGid(51)!!
        assertEquals("New", stored.title)
        assertEquals(6_001L, stored.lastModified)
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `history within skew prefers the later view time`() {
        seedHistory(gid = 52, lastModified = 1_000, time = 100)
        val response = push("A", history = listOf(hist(52, time = 200, lastModified = 2_000)))

        val stored = historyRepo.findByGid(52)!!
        assertEquals(200, stored.time)
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `history within skew keeps the later local view time`() {
        seedHistory(gid = 53, lastModified = 1_000, time = 300)
        push("A", history = listOf(hist(53, time = 100, lastModified = 2_000)))

        assertEquals(300, historyRepo.findByGid(53)!!.time)
        verify(historyRepo, never()).save(any(HistoryInfoEntity::class.java))
    }

    // ==================== per-user isolation & ownership ====================

    @Test
    fun `user A cannot overwrite user B's existing row`() {
        seedDownload(gid = 20, lastModified = 1_000, state = 1, username = "B", title = "B's title")
        val response = push("A", downloads = listOf(dl(20, lastModified = 6_001, state = 9, title = "A's title")))

        val stored = downloadRepo.findByGid(20)!!
        assertEquals("B", stored.username)
        assertEquals(1, stored.state)
        assertEquals("B's title", stored.title)
        verify(downloadRepo, never()).save(any(DownloadInfoEntity::class.java))
        assertEquals(0, response.conflicts)
    }

    @Test
    fun `legacy null-username rows are claimed by the first pushing user`() {
        LocalFavoriteInfoEntity().apply {
            gid = 30
            token = "tok30"
            username = null
            lastModified = 0
        }.let { favoriteRepo.save(it) }
        HistoryInfoEntity().apply {
            gid = 31
            token = "tok31"
            username = null
            lastModified = 5
        }.let { historyRepo.save(it) }
        FilterEntity().apply {
            type = 5
            text = "legacy"
            username = null
        }.let { filterRepo.save(it) }

        push("A")

        val claimedFav = favoriteRepo.findByGid(30)!!
        assertEquals("A", claimedFav.username)
        // Clamped to a minimal non-zero value so the first pull (since=0) delivers it.
        assertEquals(1L, claimedFav.lastModified)
        val claimedHist = historyRepo.findByGid(31)!!
        assertEquals("A", claimedHist.username)
        assertEquals(5L, claimedHist.lastModified)
        val claimedFilter = filterRepo.findAll().single()
        assertEquals("A", claimedFilter.username)
    }

    // ==================== device tracking & preferences ====================

    @Test
    fun `push registers the device with platform derived from deviceId`() {
        push("A", deviceId = "android-uuid-123")

        val device = deviceRepo.findByDeviceId("android-uuid-123")!!
        assertEquals("android", device.platform)
        assertEquals("A", device.username)
        assertTrue(device.lastSyncTimestamp > 0)
        assertTrue(device.lastSeen > 0)
    }

    @Test
    fun `push updates an existing device's sync timestamp`() {
        SyncDeviceEntity().apply {
            deviceId = "android-existing"
            username = "A"
        }.let { deviceRepo.save(it) }
        clearInvocations(deviceRepo)

        push("A", deviceId = "android-existing")

        val device = deviceRepo.findByDeviceId("android-existing")!!
        assertEquals("A", device.username)
        assertTrue(device.lastSyncTimestamp > 0)
        assertTrue(device.lastSeen > 0)
    }

    @Test
    fun `push preferences round-trips lastModified through pull (E2E-8)`() {
        val json = """{"general":{"lang":"zh"}}"""
        val lastModified = 12_345L
        push("A", deviceId = "android-pref", preferences = SyncPreferencesDto(preferences = json, lastModified = lastModified, deviceId = "android-pref"))

        val pulled = service.pull(0, "A", "android-test").entities.preferences!!
        // E2E-8: 服务器保留客户端 lastModified，不回环重打戳
        assertEquals(lastModified, pulled.lastModified)
        assertTrue(pulled.preferences.contains("\"lang\":\"zh\""))
    }

    // ==================== H-3: pull 走派生查询，不再全表扫描 ====================

    @Test
    fun `incremental pull queries by username and lastModified and never full-scans`() {
        seedFavorite(gid = 60, lastModified = 1_000)
        seedFavorite(gid = 61, lastModified = 5_000)
        seedFavorite(gid = 62, lastModified = 9_000)
        clearInvocations(favoriteRepo)

        val pulled = service.pull(3_000, "A", "android-test").entities.favorites

        assertEquals(setOf(61L, 62L), pulled.map { it.gid }.toSet())
        verify(favoriteRepo).findByUsernameAndLastModifiedGreaterThan("A", 3_000L)
        verify(favoriteRepo, never()).findByUsername("A")
        verify(favoriteRepo, never()).findAll()
    }

    @Test
    fun `incremental pull filters per user in the query`() {
        seedFavorite(gid = 65, lastModified = 5_000, username = "B")
        clearInvocations(favoriteRepo)

        val pulled = service.pull(1_000, "A", "android-test").entities.favorites

        assertTrue(pulled.isEmpty())
        verify(favoriteRepo).findByUsernameAndLastModifiedGreaterThan("A", 1_000L)
    }

    @Test
    fun `full pull queries by username and includes zero-lastModified rows (since=0)`() {
        seedFavorite(gid = 63, lastModified = 0)
        seedFavorite(gid = 64, lastModified = 5)
        clearInvocations(favoriteRepo)

        val pulled = service.pull(0, "A", "android-test").entities.favorites

        assertEquals(setOf(63L, 64L), pulled.map { it.gid }.toSet())
        verify(favoriteRepo).findByUsername("A")
        verify(favoriteRepo, never()).findByUsernameAndLastModifiedGreaterThan(anyString(), anyLong())
        verify(favoriteRepo, never()).findAll()
    }

    @Test
    fun `incremental pull runs the derived query on every entity type`() {
        seedHistory(gid = 70, lastModified = 2_000)
        seedDownload(gid = 71, lastModified = 2_000)
        seedBookmark(gid = 72, lastModified = 2_000)
        seedFilter(mode = 1, text = "x", lastModified = 2_000)
        seedQuickSearch("p", lastModified = 2_000)
        seedLabel("L", lastModified = 2_000)
        clearInvocations(
            historyRepo, downloadRepo, bookmarkRepo, filterRepo, quickSearchRepo, downloadLabelRepo,
        )

        service.pull(1_500, "A", "android-test")

        verify(historyRepo).findByUsernameAndLastModifiedGreaterThan("A", 1_500L)
        verify(downloadRepo).findByUsernameAndLastModifiedGreaterThan("A", 1_500L)
        verify(bookmarkRepo).findByUsernameAndLastModifiedGreaterThan("A", 1_500L)
        verify(filterRepo).findByUsernameAndLastModifiedGreaterThan("A", 1_500L)
        verify(quickSearchRepo).findByUsernameAndLastModifiedGreaterThan("A", 1_500L)
        verify(downloadLabelRepo).findByUsernameAndLastModifiedGreaterThan("A", 1_500L)
    }

    // ==================== M-14: download label name <-> id 映射 ====================

    @Test
    fun `download label name maps to the label id on push and back on pull (M-14)`() {
        // 服务端已有标签行（如上一轮 sync 建好的 MyLabel=id7）
        DownloadLabelEntity().apply {
            id = 7
            label = "MyLabel"
            time = 111
            lastModified = 1_000
            username = "A"
        }.let { downloadLabelRepo.save(it) }
        clearInvocations(downloadRepo, downloadLabelRepo)

        push("A", downloads = listOf(dl(70, lastModified = 2_000, label = "MyLabel")))

        // push 落库为标签 id
        assertEquals(7, downloadRepo.findByGid(70)!!.label)
        // pull 回环为标签名字符串
        assertEquals("MyLabel", service.pull(0, "A", "android-test").entities.downloads.single().label)
    }

    @Test
    fun `download with unknown label name auto-creates the label row (M-14)`() {
        push("A", downloads = listOf(dl(71, lastModified = 2_000, label = "NewLabel")))

        val captor = ArgumentCaptor.forClass(DownloadLabelEntity::class.java)
        verify(downloadLabelRepo).save(captureK(captor))
        val created = captor.value
        assertEquals("NewLabel", created.label)
        assertEquals(2_000L, created.lastModified)
        // 下载行引用新建标签的 id（与 DownloadService 的 id 约定一致）
        assertEquals(created.id.toInt(), downloadRepo.findByGid(71)!!.label)
    }

    @Test
    fun `download without a label stays label zero and pulls back null (M-14)`() {
        push("A", downloads = listOf(dl(72, lastModified = 2_000)))

        assertEquals(0, downloadRepo.findByGid(72)!!.label)
        assertNull(service.pull(0, "A", "android-test").entities.downloads.single().label)
    }
}
