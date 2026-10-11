package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.captureK
import com.hippo.anotherviewer.web.eq
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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyInt
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
 * `contracts/sync-conflict-rules.md` v4（服务器打戳 / 到达戳语义）：
 *
 *  - v4 §1.3「到达戳」：push 入口对整批入参重打戳为服务器单调时钟值
 *    （`syncClock.next()`，每请求一个），客户端上送的 lastModified 不落库、
 *    不参与仲裁。单线程内来包的到达戳 >= 任何既有行戳 ⇒ 来包必赢；只有
 *    「后到达的 push」能胜过「先到达的 push」。
 *  - Favorites / Downloads / Filters / QuickSearches / DownloadLabels：union
 *    合并；墓碑行按服务器戳参与 LWW（后到墓碑胜旧活行，后到活行复活旧墓碑）。
 *  - History / Bookmarks: LWW with server-side tombstone rows — a
 *    `deleted: true` push marks the stored row deleted and 由服务器打新戳
 *    (or stores the tombstone if none exists), so incremental
 *    pulls (`since > 0`) propagate deletions; a later live push resurrects.
 *  - B（lww，v4 默认）无 skew 窗口、无实体 tie-break；A/C 同平台回退与
 *    softDeleteLiveWins 的按戳仲裁同样使用到达戳（§5.1）。
 *  - Entities are scoped per user; legacy null-username rows are claimed by
 *    the first user to push.
 *
 * All repositories are in-memory fakes keyed by the natural idempotency key
 * (gid / (mode,text) / name / label / deviceId), so assertions read back the
 * persisted state through the mocked repository interface.
 *
 * v4 注：契约缺省策略为 lww（B）；setUp 显式钉为 lww 以固定基线（§1.4）。
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
    private lateinit var serverCfg: com.hippo.anotherviewer.web.service.ServerConfigService
    private lateinit var syncClock: SyncClock
    private lateinit var eventPublisher: org.springframework.context.ApplicationEventPublisher
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
        preferenceService = UserPreferenceService(preferenceRepo, fakeServerConfig())
        siteSessionManager = mock(SiteSessionManager::class.java)
        serverCfg = fakeServerConfig()
        syncClock = SyncClock()
        eventPublisher = mock(org.springframework.context.ApplicationEventPublisher::class.java)
        service = SyncService(
            favoriteRepo, historyRepo, downloadRepo, bookmarkRepo, filterRepo,
            quickSearchRepo, downloadLabelRepo, deviceRepo, preferenceRepo, preferenceService,
            serverCfg,
            mock(EhSessionRepository::class.java),
            siteSessionManager,
            syncClock,
            eventPublisher,
        )
        // v1 回归套件钉在策略 B（lww）= v4 默认策略（契约 §1.4）。
        service.updatePolicy(SyncPolicyDto(conflictStrategy = ConflictStrategy.LWW))
    }

    /**
     * 客户端域墙钟值（fixture 元数据）。v4 起客户端上送戳不落库、不参与任何仲裁
     * （push 入口以到达戳覆盖），此值仅用于演示「客户戳多大都不改变胜负」。
     */
    private fun nowMs(): Long = System.currentTimeMillis()

    /**
     * v4 打戳断言：落库水位必须来自服务器 SyncClock —— 不等于客户端上送值（客户端值
     * 不落库），且晚于 [clockBefore]（push 前的时钟水位；服务器戳严格单调，实体戳先于
     * push 末尾的 serverTimestamp 发放）。
     */
    private fun assertClockStamped(stored: Long, clientValue: Long, clockBefore: Long) {
        assertNotEquals(clientValue, stored, "客户端上送值 $clientValue 不得落库")
        assertTrue(stored > clockBefore, "落库水位 $stored 必须晚于 push 前时钟水位 $clockBefore")
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
        // MASTER-2026-08-22 P2：provenance 孤儿键清理所需。
        `when`(repo.findByKeyStartingWith(anyString())).thenAnswer { inv ->
            store.values.filter { it.key.startsWith(inv.getArgument<String>(0)) }
        }
        `when`(repo.delete(any(ServerConfigEntity::class.java))).thenAnswer { inv ->
            store.remove(inv.getArgument<ServerConfigEntity>(0).key); null
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
        `when`(repo.findAllByGid(anyLong())).thenAnswer { inv ->
            store[inv.getArgument<Long>(0)]?.let { listOf(it) } ?: emptyList<LocalFavoriteInfoEntity>()
        }
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
        `when`(repo.findAllByGid(anyLong())).thenAnswer { inv ->
            store[inv.getArgument<Long>(0)]?.let { listOf(it) } ?: emptyList<HistoryInfoEntity>()
        }
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
        `when`(repo.findAllByGid(anyLong())).thenAnswer { inv ->
            store[inv.getArgument<Long>(0)]?.let { listOf(it) } ?: emptyList<DownloadInfoEntity>()
        }
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
        `when`(repo.findAllByGid(anyLong())).thenAnswer { inv ->
            store[inv.getArgument<Long>(0)]?.let { listOf(it) } ?: emptyList<BookmarkInfoEntity>()
        }
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
        `when`(repo.findByTypeAndTextIgnoreCaseOrderByLastModifiedDesc(anyInt(), anyString())).thenAnswer { inv ->
            store.values
                .filter { it.type == inv.getArgument<Int>(0) && it.text == inv.getArgument<String>(1) }
                .sortedByDescending { it.lastModified }
        }
        `when`(repo.findAllByUsernameIsNull()).thenAnswer { store.values.filter { it.username == null } }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv -> store.values.filter { it.username == inv.getArgument<String>(0) } }
        `when`(repo.findByUsernameAndLastModifiedGreaterThan(anyString(), anyLong())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.lastModified > inv.getArgument<Long>(1) }
        }
        doAnswer { inv ->
            store.entries.removeIf { it.value === inv.getArgument<FilterEntity>(0) }
        }.`when`(repo).delete(any(FilterEntity::class.java))
        // 模拟历史脏数据：绕过 UNIQUE 语义直接注入重复 (type,text) 行（旧版本无约束时产生）。
        duplicateSeed = { entity ->
            store["${entity.type}:${entity.text}#${System.identityHashCode(entity)}"] = entity
        }
        return repo
    }
    private var duplicateSeed: ((FilterEntity) -> Unit)? = null

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

    private fun hist(gid: Long, time: Long = 0, lastModified: Long = 1_000, deleted: Boolean = false, title: String = "Hist $gid", page: Int = 0) =
        SyncHistoryDto(gid = gid, token = "tok$gid", title = title, time = time, lastModified = lastModified, deleted = deleted, page = page)

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

    private fun seedHistory(gid: Long, lastModified: Long, time: Long = 0, username: String? = "A", title: String = "Hist $gid", page: Int = 0) {
        HistoryInfoEntity().apply {
            this.gid = gid
            this.token = "tok$gid"
            this.title = title
            this.time = time
            this.lastModified = lastModified
            this.page = page
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
        // v4：客户端上送戳（1000）不落库——落库水位 = 服务器 syncClock 戳。
        assertClockStamped(saved.lastModified, 1_000L, 0L)
    }

    @Test
    fun `download tombstone with no local record is stored, not deleted`() {
        val response = push("A", downloads = listOf(dl(2, lastModified = 2_000, deleted = true)))

        val stored = downloadRepo.findByGid(2)!!
        assertTrue(stored.deleted)
        verify(downloadRepo).save(any(DownloadInfoEntity::class.java))
        verify(downloadRepo, never()).delete(any(DownloadInfoEntity::class.java))
        // 墓碑 create（未知键的删除传播载体）也是「生效写入」→ 计 1。
        // 2026-10 保真度修复：此前 create 路径返回 false（漏报）。
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `download tombstone wins over an older alive row (v4 lww stamp arbitration)`() {
        // v4：删除=墓碑按戳参与 LWW——incoming 墓碑（2000）>= 存量活行（1000）→ 墓碑胜。
        seedDownload(gid = 3, lastModified = 1_000, state = 1)
        val response = push("A", downloads = listOf(dl(3, lastModified = 2_000, deleted = true)))

        val stored = downloadRepo.findByGid(3)!!
        assertTrue(stored.deleted)
        assertEquals(1, response.conflicts)
        verify(downloadRepo, never()).delete(any(DownloadInfoEntity::class.java))
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
    fun `download newer incoming applies state fields`() {
        seedDownload(gid = 5, lastModified = 1_000, state = 1, title = "Old")
        val response = push("A", downloads = listOf(dl(5, lastModified = 6_001, state = 3, title = "New")))

        val stored = downloadRepo.findByGid(5)!!
        assertEquals(3, stored.state)
        assertEquals("New", stored.title)
        assertClockStamped(stored.lastModified, 6_001L, 0L)
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `download within overlap now applies the incoming row (v4 lww flip)`() {
        // v4 翻转：旧 skew 语义「窗口内保留存量」被纯 LWW 取代——incoming(3000) >=
        // 存量(1000) 即后到胜（不再要求超出 5s 窗口）。
        seedDownload(gid = 6, lastModified = 1_000, state = 1)
        val response = push("A", downloads = listOf(dl(6, lastModified = 3_000, state = 3)))

        assertEquals(3, downloadRepo.findByGid(6)!!.state)
        assertEquals(1, response.conflicts)
    }

    // ==================== mergeFilter ====================

    @Test
    fun `filter deduplicates on the mode plus text key and the later push wins (v4)`() {
        push("A", filters = listOf(flt(mode = 1, text = "artist:foo", enabled = true, lastModified = 1_000)))
        val firstStamp = filterRepo.findAll().single().lastModified
        // v4 到达戳：同键就地更新（不新增行）；后到包以到达序胜出——客户戳 2_000 只是
        // 传输元数据，不参与仲裁（旧语义「存量是服务器戳 ⇒ 来包被丢弃」废除）。
        push("A", filters = listOf(flt(mode = 1, text = "artist:foo", enabled = false, lastModified = 2_000)))

        val rows = filterRepo.findAll()
        assertEquals(1, rows.size) // 键去重：仍是一行
        assertFalse(rows[0].enabled) // 后到包胜
        assertTrue(rows[0].lastModified > firstStamp, "行水位应推进为后到批的服务器戳")
    }

    @Test
    fun `filter tolerate archived duplicate rows without NonUniqueResultException`() {
        // 2026-08-30 联调事故：历史数据含 (type,text) 重复行时单值查询抛
        // NonUniqueResultException → push 500。list 查询 + firstOrNull 必须容忍。
        val seed = listOf(100L, 200L, 300L).map { lm ->
            FilterEntity().apply {
                this.type = 5
                this.text = "old:dup"
                this.enabled = true
                this.lastModified = lm
                this.username = "A"
            }
        }
        seed.forEach { duplicateSeed!!.invoke(it) }

        // incoming.lastModified 远超存量（> SKEW_TOLERANCE）→ 走 LWW 分支，
        // 即 merge 命中 lastModified=300 的历史行并应用 incoming（enabled=false）。
        val response = push("A", filters = listOf(flt(mode = 5, text = "old:dup", enabled = false, lastModified = 140_000)))

        assertTrue(response.success) // 核心：不再抛 NonUniqueResultException
        // merge 命中 lastModified=300 的历史行：LWW(incoming=140000) 将其 enabled=false，
        // 行水位改由服务器打戳（v4）。
        // 其余重复历史行保持原样；save 可能新增规范化 key 行（fake repo 语义）。
        val merged = filterRepo.findAll().filter { it.text == "old:dup" }
        assertTrue(merged.any { !it.enabled && it.lastModified != 140_000L && it.lastModified > 0 })
        assertTrue(merged.size in 3..4) // 3 注入 + 至多 1 规范化保存
    }

    @Test
    fun `filter tombstone is stored for an unknown key`() {
        val response = push("A", filters = listOf(flt(mode = 9, text = "zzz", deleted = true, lastModified = 2_000)))

        val row = filterRepo.findAll().single()
        assertTrue(row.deleted)
        assertEquals("A", row.username)
        // 墓碑 create 成功 = 生效写入 → 计 1（保真度修复）。
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `filter lww applies the incoming enabled flag (v4 without additive bias)`() {
        seedFilter(mode = 2, text = "bar", lastModified = 1_000, enabled = false)
        val response = push("A", filters = listOf(flt(mode = 2, text = "bar", enabled = true, lastModified = 2_000)))

        assertTrue(filterRepo.findAll().single().enabled)
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `filter additive bias still applies on the a-c same-platform fallback`() {
        // 旧 bias 用例迁移到 A/C 同平台回退：存量 provenance="server"（web 侧）+
        // web 设备推送 → 跨平台序不适用，回退 v1 skew+bias（窗口内 enabled=true 胜）。
        seedFilter(mode = 2, text = "bias", lastModified = 1_000, enabled = false)
        service.updatePolicy(SyncPolicyDto(conflictStrategy = ConflictStrategy.DEVICE_PRIORITY))
        val response = push("A", deviceId = "web-1", filters = listOf(flt(mode = 2, text = "bias", enabled = true, lastModified = 2_000)))

        assertTrue(filterRepo.findAll().single().enabled)
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `filter LWW beyond skew applies incoming state`() {
        seedFilter(mode = 3, text = "baz", lastModified = 1_000, enabled = true)
        push("A", filters = listOf(flt(mode = 3, text = "baz", enabled = false, lastModified = 6_001)))

        val row = filterRepo.findAll().single()
        assertFalse(row.enabled)
        assertClockStamped(row.lastModified, 6_001L, 0L)
    }

    @Test
    fun `filter arrival order decides the winner (client stamps do not arbitrate, v4)`() {
        // v4 翻转：旧语义「存量客户戳更新 ⇒ 保留存量」废除——来包以到达序胜出
        // （到达戳 >= 任何已发出的服务器戳；客户端上送值仅作传输元数据）。
        push("A", filters = listOf(flt(mode = 4, text = "q", enabled = true, lastModified = 6_001)))
        val firstStamp = filterRepo.findAll().single().lastModified
        push("A", filters = listOf(flt(mode = 4, text = "q", enabled = false, lastModified = 1_000)))

        val row = filterRepo.findAll().single()
        assertFalse(row.enabled) // 后到包胜（客户戳 1_000「更旧」不影响胜负）
        assertTrue(row.lastModified > firstStamp, "行水位应推进为后到批的服务器戳")

        // 对照：交换到达序——后到的是 enabled=true，则 true 胜（胜者随到达序反转，与戳值无关）。
        push("A", filters = listOf(flt(mode = 4, text = "q", enabled = true, lastModified = 6_001)))
        assertTrue(filterRepo.findAll().single().enabled)
    }

    // ==================== mergeBookmark ====================

    @Test
    fun `bookmark delete keeps a tombstone row and bumps lastModified`() {
        seedBookmark(gid = 7, lastModified = 1_000, page = 10)
        val response = push("A", bookmarks = listOf(bm(7, page = 10, lastModified = 2_000, deleted = true)))

        val stored = bookmarkRepo.findByGid(7)!!
        assertTrue(stored.deleted)
        // v4：墓碑 bump 走服务器打戳（不再是 maxOf 客户端值）。
        assertClockStamped(stored.lastModified, 2_000L, 0L)
        verify(bookmarkRepo, never()).delete(any(BookmarkInfoEntity::class.java))
        // 存量行墓碑化（bump）= 生效写入 → 计 1（删必须计入 sync.update，
        // 否则 WebUI 书签列表收不到实时移除通知）。
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `bookmark delete of an unknown gid stores a tombstone`() {
        val response = push("A", bookmarks = listOf(bm(12, page = 3, lastModified = 2_000, deleted = true)))

        val stored = bookmarkRepo.findByGid(12)!!
        assertTrue(stored.deleted)
        assertClockStamped(stored.lastModified, 2_000L, 0L)
        // 墓碑 create 成功 = 生效写入 → 计 1（与存量行 bump 同为 true）。
        assertEquals(1, response.conflicts)
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
    fun `bookmark LWW applies incoming fields`() {
        seedBookmark(gid = 8, lastModified = 1_000, page = 5)
        val response = push("A", bookmarks = listOf(bm(8, page = 3, lastModified = 6_001)))

        val stored = bookmarkRepo.findByGid(8)!!
        assertEquals("3", stored.note)
        assertClockStamped(stored.lastModified, 6_001L, 0L)
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `bookmark new incoming wins directly under v4 lww (no page tie-break)`() {
        seedBookmark(gid = 9, lastModified = 1_000, page = 10)
        val response = push("A", bookmarks = listOf(bm(9, page = 50, lastModified = 2_000)))

        assertEquals("50", bookmarkRepo.findByGid(9)!!.note)
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `bookmark v4 lww no longer keeps the higher local page`() {
        // v4 翻转：纯 LWW 下 incoming(2000) >= 存量(1000) 即整行后到胜，
        // 低页也照写（不在窗口内做 page tie-break）。
        seedBookmark(gid = 10, lastModified = 1_000, page = 40)
        val response = push("A", bookmarks = listOf(bm(10, page = 5, lastModified = 2_000)))

        assertEquals("5", bookmarkRepo.findByGid(10)!!.note)
        assertEquals(1, response.conflicts)
    }

    // ==================== mergeQuickSearch ====================

    @Test
    fun `quick search deduplicates on the name key`() {
        push("A", quickSearches = listOf(qs("preset1", lastModified = 1_000)))
        // Same key, clearly newer: the row is updated in place, not duplicated.
        // v4 判据是「服务器戳 vs 客户端戳」，玩具值会被首次写入的服务器戳压过 →
        // 用真实时钟值表达「晚于首次写入」。
        push("A", quickSearches = listOf(qs("preset1", lastModified = nowMs(), keyword = "new")))

        assertEquals(1, quickSearchRepo.findAll().size)
        assertEquals("new", quickSearchRepo.findByName("preset1")!!.keyword)
    }

    @Test
    fun `quick search tombstone is stored for an unknown name`() {
        val response = push("A", quickSearches = listOf(qs("preset2", deleted = true, lastModified = 2_000)))

        assertTrue(quickSearchRepo.findByName("preset2")!!.deleted)
        // 墓碑 create 成功 = 生效写入 → 计 1（保真度修复）。
        assertEquals(1, response.conflicts)
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
    fun `quick search arrival order decides the winner (client stamps do not arbitrate, v4)`() {
        // v4 翻转：单线程内来包以到达序必赢——存量客户戳「更新」（6_001）不再保留存量；
        // 来包自身上送的客户戳（1_000）同样不落库、不参与仲裁。
        seedQuickSearch("preset5", lastModified = 6_001, keyword = "old")
        push("A", quickSearches = listOf(qs("preset5", lastModified = 1_000, keyword = "new")))
        assertEquals("new", quickSearchRepo.findByName("preset5")!!.keyword)

        // 对照：交换到达序——后到的是 "old"，old 以到达序胜出（胜者随到达序反转）。
        push("A", quickSearches = listOf(qs("preset5", lastModified = 6_001, keyword = "old")))
        assertEquals("old", quickSearchRepo.findByName("preset5")!!.keyword)
    }

    // ==================== mergeDownloadLabel ====================

    @Test
    fun `download label deduplicates on the label key and the later tombstone push wins (v4)`() {
        push("A", downloadLabels = listOf(lbl("L1", lastModified = 1_000)))
        // v4 到达戳：同名键就地更新（不新增行）；后到的墓碑以到达序胜出（客户戳 2_000
        // 只是传输元数据，不参与仲裁）——旧语义「活行不为墓碑所动」废除。
        push("A", downloadLabels = listOf(lbl("L1", lastModified = 2_000, deleted = true)))

        val rows = downloadLabelRepo.findAll()
        assertEquals(1, rows.size) // 键去重：仍是一行
        assertTrue(rows[0].deleted) // 后到墓碑胜 = 删除传播
    }

    @Test
    fun `download label newer tombstone wins under v4 lww`() {
        push("A", downloadLabels = listOf(lbl("L1b", lastModified = 1_000)))
        push("A", downloadLabels = listOf(lbl("L1b", lastModified = nowMs(), deleted = true)))

        assertTrue(downloadLabelRepo.findByLabel("L1b")!!.deleted)
    }

    @Test
    fun `download label tombstone is stored for an unknown label`() {
        val response = push("A", downloadLabels = listOf(lbl("L2", deleted = true, lastModified = 2_000)))

        assertTrue(downloadLabelRepo.findByLabel("L2")!!.deleted)
        // 墓碑 create 成功 = 生效写入 → 计 1（保真度修复）。
        assertEquals(1, response.conflicts)
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
        `when`(siteSessionManager.applySyncEhSession(any(SyncEhSessionDto::class.java), anyString())).thenReturn(true)

        val response = push("A", ehSession = listOf(incoming))

        // v4：委托的 DTO 已被 push 入口重打戳——到达戳由服务器生成（每请求一个），
        // 不再是客户端上送值，故 verify 不绑定具体戳值：捕获器断言委托发生 + 戳值非客户端值。
        // （显式类型实参：captor 来自 ArgumentCaptor.forClass 的平台类型推断，
        //  `captureK<T>()` 契约才免掉 Kotlin 非空检查——同 ImageProxyControllerTest 惯例。）
        val captor = ArgumentCaptor.forClass(SyncEhSessionDto::class.java)
        verify(siteSessionManager).applySyncEhSession(captureK<SyncEhSessionDto>(captor), eq("android-test"))
        assertNotEquals(1_000L, captor.value.lastModified, "到达戳由服务器生成，客户端上送值不参与")
        // 覆盖存量行计一次「生效写入」，与其它 merge 的 conflicts 语义一致。
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
    fun `ehSession pull applies the incremental since filter with overlap (v4)`() {
        val session = SyncEhSessionDto(lastModified = 2_000, deleted = false)
        `when`(siteSessionManager.loadSyncEhSession()).thenReturn(session)

        val pulled = service.pull(1_000, "A")

        assertEquals(1, pulled.entities.ehSession.size)

        // v4：增量下界 = since - PULL_OVERLAP_MS —— 水位 500 落在 overlap 窗口内仍返回
        //（吸收并发提交），只有早于下界（0）的才被过滤。
        val withinOverlap = SyncEhSessionDto(lastModified = 500, deleted = false)
        `when`(siteSessionManager.loadSyncEhSession()).thenReturn(withinOverlap)
        assertEquals(1, service.pull(1_000, "A").entities.ehSession.size)

        val belowBound = SyncEhSessionDto(lastModified = 0, deleted = false)
        `when`(siteSessionManager.loadSyncEhSession()).thenReturn(belowBound)
        assertEquals(0, service.pull(1_000, "A").entities.ehSession.size)
    }

    // ==================== favorites / history key semantics ====================

    @Test
    fun `favorite tombstone is stored for an unknown gid`() {
        val response = push("A", favorites = listOf(fav(40, lastModified = 2_000, deleted = true)))

        val stored = favoriteRepo.findByGid(40)!!
        assertTrue(stored.deleted)
        assertEquals("A", stored.username)
        // 墓碑 create 成功 = 生效写入 → 计 1（保真度修复）。
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `favorite tombstone wins over an older alive row (v4 lww stamp arbitration)`() {
        seedFavorite(gid = 41, lastModified = 1_000)
        val response = push("A", favorites = listOf(fav(41, lastModified = 2_000, deleted = true)))

        val stored = favoriteRepo.findByGid(41)!!
        assertTrue(stored.deleted)
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `favorite tombstone wins by arrival over a much newer client-stamped alive row (v4)`() {
        // v4 翻转：存量活行的客户戳即便「更新」（nowMs）也不参与仲裁——来包（墓碑）以到达序
        // 胜出（到达戳 >= 任何既有戳，§1.3）；旧语义「旧墓碑不敌新活行／被丢弃」废除。
        seedFavorite(gid = 46, lastModified = nowMs())
        val response = push("A", favorites = listOf(fav(46, lastModified = 1_000, deleted = true)))

        val stored = favoriteRepo.findByGid(46)!!
        assertTrue(stored.deleted) // 后到墓碑胜 = 删除传播
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `favorite LWW beyond skew applies incoming metadata`() {
        seedFavorite(gid = 42, lastModified = 1_000, title = "Old")
        val response = push("A", favorites = listOf(fav(42, lastModified = 6_001, title = "New")))

        val stored = favoriteRepo.findByGid(42)!!
        assertEquals("New", stored.title)
        assertClockStamped(stored.lastModified, 6_001L, 0L)
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `favorite v4 lww flips within-overlap to incoming wins`() {
        // v4 翻转：窗口内不再保留存量——incoming(3000) >= 存量(1000) 即后到胜。
        seedFavorite(gid = 44, lastModified = 1_000, title = "Old")
        val response = push("A", favorites = listOf(fav(44, lastModified = 3_000, title = "New")))

        assertEquals("New", favoriteRepo.findByGid(44)!!.title)
        assertEquals(1, response.conflicts)
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
        // v4：墓碑 bump 走服务器打戳（不再是 maxOf 客户端值）。
        assertClockStamped(stored.lastModified, 2_000L, 0L)
        verify(historyRepo, never()).delete(any(HistoryInfoEntity::class.java))
        // 存量行墓碑化（bump）= 生效写入 → 计 1（删必须计入 sync.update，
        // 否则 WebUI 历史列表收不到实时移除通知）。
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `history delete of an unknown gid stores a tombstone`() {
        val response = push("A", history = listOf(hist(54, lastModified = 2_000, deleted = true)))

        val stored = historyRepo.findByGid(54)!!
        assertTrue(stored.deleted)
        assertClockStamped(stored.lastModified, 2_000L, 0L)
        assertEquals("A", stored.username)
        // 墓碑 create 成功 = 生效写入 → 计 1（与存量行 bump 同为 true）。
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `re-pushing the same history tombstone stays idempotent`() {
        seedHistory(gid = 59, lastModified = 1_000)
        push("A", history = listOf(hist(59, lastModified = 2_000, deleted = true)))
        val firstStamp = historyRepo.findByGid(59)!!.lastModified
        push("A", history = listOf(hist(59, lastModified = 2_000, deleted = true)))

        val stored = historyRepo.findByGid(59)!!
        assertTrue(stored.deleted)
        // v4：重复墓碑每次 bump 服务器戳（单调递增），行状态幂等（仍为墓碑）。
        assertTrue(stored.lastModified >= firstStamp)
        verify(historyRepo, never()).delete(any(HistoryInfoEntity::class.java))
    }

    @Test
    fun `history tombstone reaches an incremental pull after the bump`() {
        seedHistory(gid = 55, lastModified = 1_000)
        push("A", history = listOf(hist(55, lastModified = 2_000, deleted = true)))

        val pulled = service.pull(1_500, "A", "android-test").entities.history
        assertEquals(listOf(55L), pulled.map { it.gid })
        assertTrue(pulled[0].deleted)
        assertTrue(pulled[0].lastModified > 1_500)
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

        // v4：增量下界 = since - PULL_OVERLAP_MS（钳 0）——since=1 的下界为 0，
        // lastModified=5 的行仍返回（overlap 吸收并发提交），lastModified=0 的行不返回。
        val incremental = service.pull(1, "A", "android-test").entities.history
        assertEquals(listOf(58L), incremental.map { it.gid })
    }

    @Test
    fun `history LWW applies incoming fields`() {
        seedHistory(gid = 51, lastModified = 1_000, time = 100, title = "Old")
        val response = push("A", history = listOf(hist(51, time = 50, lastModified = 6_001, title = "New")))

        val stored = historyRepo.findByGid(51)!!
        assertEquals("New", stored.title)
        assertClockStamped(stored.lastModified, 6_001L, 0L)
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `history v4 lww applies incoming row directly (no view-time tie-break)`() {
        // v4：落库 time 是业务字段（客户端值），行胜与否只看戳——incoming 更晚即整行胜。
        seedHistory(gid = 52, lastModified = 1_000, time = 100)
        val response = push("A", history = listOf(hist(52, time = 200, lastModified = 2_000)))

        val stored = historyRepo.findByGid(52)!!
        assertEquals(200, stored.time)
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `history v4 lww no longer keeps a larger local view time`() {
        // v4 翻转：窗口内不再按 time tie-break 保留存量——incoming 戳更新即整行胜（含更小 time）。
        seedHistory(gid = 53, lastModified = 1_000, time = 300)
        val response = push("A", history = listOf(hist(53, time = 100, lastModified = 2_000)))

        assertEquals(100, historyRepo.findByGid(53)!!.time)
        assertEquals(1, response.conflicts)
    }

    // ==================== S8/S9: history page（阅读进度）同步 ====================

    @Test
    fun `history push carries page into the stored row`() {
        val response = push("A", history = listOf(hist(60, lastModified = 2_000, page = 42)))

        val stored = historyRepo.findByGid(60)!!
        assertEquals(42, stored.page)
        // 未知 gid 的活行 create = 生效写入 → 计 1（2026-10 保真度修复前为 0）。
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `push without page (legacy app) never zeroes the stored progress on row win`() {
        seedHistory(gid = 61, lastModified = 1_000, page = 42)

        // 行胜（LWW beyond skew）：旧端 push 不带 page（默认 0）→ max(42, 0) = 42 不丢。
        push("A", history = listOf(hist(61, time = 50, lastModified = 6_001, title = "New")))

        assertEquals(42, historyRepo.findByGid(61)!!.page)
        assertEquals("New", historyRepo.findByGid(61)!!.title)
    }

    @Test
    fun `incoming lower page loses to the stored progress on row win`() {
        seedHistory(gid = 62, lastModified = 1_000, page = 42)

        // 行胜但 incoming.page 更低 → 取 max，进度不回退（D3 方案 A）。
        push("A", history = listOf(hist(62, time = 50, lastModified = 6_001, page = 3)))

        assertEquals(42, historyRepo.findByGid(62)!!.page)
    }

    @Test
    fun `incoming higher page wins on row win`() {
        seedHistory(gid = 63, lastModified = 1_000, page = 3)

        push("A", history = listOf(hist(63, time = 50, lastModified = 6_001, page = 42)))

        assertEquals(42, historyRepo.findByGid(63)!!.page)
    }

    @Test
    fun `row win applies the incoming row while page keeps the max progress (v4 arrival order)`() {
        seedHistory(gid = 64, lastModified = 6_000, page = 42)
        val clockBefore = syncClock.current()
        push("A", history = listOf(hist(64, time = 50, lastModified = 500, page = 3)))

        // v4 翻转：来包以到达序整行胜（客户戳 500「更旧」不参与仲裁）——旧语义「行负保留存量
        // 水位 6_000」废除，水位同为服务器到达戳；唯一保留的合并细节是 applyHistoryFields 的
        // page = max（D3：进度不回退，也不做行负抬升）。
        val stored = historyRepo.findByGid(64)!!
        assertEquals(50, stored.time) // 整行应用：业务字段落库（time = incoming.time）
        assertEquals(42, stored.page) // page 取 max(42, 3)：进度不回退
        assertClockStamped(stored.lastModified, 500L, clockBefore)
    }

    @Test
    fun `history pull carries the stored page`() {
        seedHistory(gid = 65, lastModified = 2_000, page = 42)

        val pulled = service.pull(0, "A", "android-test").entities.history.single { it.gid == 65L }

        assertEquals(42, pulled.page)
    }

    // ==================== sync.update 计数（生效写入保真度） ====================

    @Test
    fun `create-only push counts every entity as an effective write in conflicts and the sync update event`() {
        // 2026-10 保真度修复：create 成功路径返回 true —— 新建实体必须进入
        // counts/conflicts（此前为 0 / 缺键，WebUI 收不到新建实体的失效通知）。
        val response = push(
            "A",
            favorites = listOf(fav(80)),
            history = listOf(hist(81)),
            downloads = listOf(dl(82)),
            bookmarks = listOf(bm(83)),
            filters = listOf(flt(mode = 1, text = "create-only")),
            quickSearches = listOf(qs("create-only")),
            downloadLabels = listOf(lbl("create-only")),
        )

        // conflicts = counts 派生和：7 类实体各 1 次 create → 7。
        assertEquals(7, response.conflicts)

        val captor = ArgumentCaptor.forClass(SyncUpdateEvent::class.java)
        verify(eventPublisher).publishEvent(captureK(captor))
        assertEquals(
            mapOf(
                "favorites" to 1,
                "history" to 1,
                "downloads" to 1,
                "bookmarks" to 1,
                "filters" to 1,
                "quickSearches" to 1,
                "downloadLabels" to 1,
            ),
            captor.value.counts,
            "create 也是生效写入：新建实体必须出现在失效广播中（仅 >0 的键入 counts）",
        )
    }

    @Test
    fun `create blocked by another user's row stays uncounted`() {
        // 属主隔离优先：他人已占该键时不插不覆盖、不算生效写入（create 返回值的
        // 修复不得把「无写入」误计为生效）——锚定 create 路径修复的边界。
        seedFavorite(gid = 90, lastModified = 1_000, username = "B")
        val response = push("A", favorites = listOf(fav(90)))

        verify(favoriteRepo, never()).save(any(LocalFavoriteInfoEntity::class.java))
        assertEquals(0, response.conflicts)

        val captor = ArgumentCaptor.forClass(SyncUpdateEvent::class.java)
        verify(eventPublisher).publishEvent(captureK(captor))
        assertTrue(captor.value.counts.isEmpty(), "无生效写入 → counts 为空（事件仍发布，由 WebUI 幂等忽略）")
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
    fun `merge tolerates duplicate gid rows across users without exception`() {
        // A7-3（P1-1）：同 gid 多行（分属 A/B）时旧单实体 findByGid 派生查询会抛
        // IncorrectResultSizeDataAccessException，毒化整条 push。List 化后按属主
        // 仲裁，语义保持：B 已占时 A 的新行 push 不插不覆盖；同 gid 双行并存时
        // 仲裁只落在推送者自己的行上（墓碑/活行规则不变）。
        // 1) B 已占 gid：A push 新活行 → 不插、不炸、B 行原样。
        seedDownload(gid = 20, lastModified = 1_000, state = 1, username = "B", title = "B's title")
        val response = push("A", downloads = listOf(dl(20, lastModified = 6_001, state = 9, title = "A's title")))
        val stored = downloadRepo.findByGid(20)!!
        assertEquals("B", stored.username)
        assertEquals(1, stored.state)
        verify(downloadRepo, never()).save(any(DownloadInfoEntity::class.java))
        assertEquals(0, response.conflicts)

        // 2) 同 gid 真实双行（store 按 gid 单行，覆写 findAllByGid 模拟两行并存）：
        //    A push 活行（LWW 行胜）→ 只落在 A 自己的行上，B 的行原样保留。
        val rowB = HistoryInfoEntity().apply {
            gid = 41; token = "tok41"; title = "B"; lastModified = 1_000; username = "B"
        }
        val rowA = HistoryInfoEntity().apply {
            gid = 41; token = "tok41"; title = "A"; lastModified = 1_000; username = "A"
        }
        `when`(historyRepo.findAllByGid(41L)).thenReturn(listOf(rowB, rowA))
        push("A", history = listOf(hist(41, lastModified = 6_001, title = "A v2")))
        assertEquals("B", rowB.title)
        assertEquals(1_000L, rowB.lastModified)
        assertFalse(rowB.deleted)
        assertEquals("A v2", rowA.title)
        // v4：A 行水位由服务器打戳（客户端 6001 不落库）。
        assertNotEquals(6_001L, rowA.lastModified)
        assertTrue(rowA.lastModified > 1_000L)

        // 3) A push 墓碑 → 墓碑仲裁同样只落 A 的行，B 的活行不受牵连。
        push("A", history = listOf(hist(41, lastModified = 9_000, deleted = true)))
        assertTrue(rowA.deleted)
        assertFalse(rowB.deleted)
        assertEquals(1_000L, rowB.lastModified)
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
    fun `push preferences echoes the server stamp back through pull (v4 echo semantics)`() {
        val json = """{"general":{"lang":"zh"}}"""
        push("A", deviceId = "android-pref", preferences = SyncPreferencesDto(preferences = json, lastModified = 12_345L, deviceId = "android-pref"))

        val pulled = service.pull(0, "A", "android-test").entities.preferences!!
        // v4：preferences 的 updatedAt 由服务器打戳（客户端上送值不落库）——pull 回显的
        // 是 push 期间发放的服务器戳，绝不是客户端上送的 12_345。
        assertTrue(pulled.lastModified > 0)
        assertNotEquals(12_345L, pulled.lastModified)
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
        // v4：增量下界 = since - PULL_OVERLAP_MS。
        verify(favoriteRepo).findByUsernameAndLastModifiedGreaterThan("A", 3_000L - SyncService.PULL_OVERLAP_MS)
        verify(favoriteRepo, never()).findByUsername("A")
        verify(favoriteRepo, never()).findAll()
    }

    @Test
    fun `incremental pull filters per user in the query`() {
        seedFavorite(gid = 65, lastModified = 5_000, username = "B")
        clearInvocations(favoriteRepo)

        val pulled = service.pull(1_000, "A", "android-test").entities.favorites

        assertTrue(pulled.isEmpty())
        verify(favoriteRepo).findByUsernameAndLastModifiedGreaterThan("A", 1_000L - SyncService.PULL_OVERLAP_MS)
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

        val bound = 1_500L - SyncService.PULL_OVERLAP_MS
        verify(historyRepo).findByUsernameAndLastModifiedGreaterThan("A", bound)
        verify(downloadRepo).findByUsernameAndLastModifiedGreaterThan("A", bound)
        verify(bookmarkRepo).findByUsernameAndLastModifiedGreaterThan("A", bound)
        verify(filterRepo).findByUsernameAndLastModifiedGreaterThan("A", bound)
        verify(quickSearchRepo).findByUsernameAndLastModifiedGreaterThan("A", bound)
        verify(downloadLabelRepo).findByUsernameAndLastModifiedGreaterThan("A", bound)
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
        assertNotEquals(2_000L, created.lastModified)
        // 下载行引用新建标签的 id（与 DownloadService 的 id 约定一致）
        assertEquals(created.id.toInt(), downloadRepo.findByGid(71)!!.label)
    }

    @Test
    fun `download without a label stays label zero and pulls back null (M-14)`() {
        push("A", downloads = listOf(dl(72, lastModified = 2_000)))

        assertEquals(0, downloadRepo.findByGid(72)!!.label)
        assertNull(service.pull(0, "A", "android-test").entities.downloads.single().label)
    }

    // ── MASTER-2026-08-22 P2/P3 ────────────────────────────────

    @Test
    fun `pruneOrphanProvenance removes keys whose row no longer exists and keeps live ones (P2)`() {
        // 建一行真实过滤（含其 provenance）
        push("A", filters = listOf(flt(mode = 1, text = "p2-live", enabled = true, lastModified = 1_000)))
        // 孤儿键：无任何行对应
        serverCfg.set("sync.prov.dl.deadbeef", "ghost-device")

        service.pruneOrphanProvenance()

        assertEquals("", serverCfg.get("sync.prov.dl.deadbeef"))
        val remaining = serverCfg.findByKeyStartingWith("sync.prov.")
        assertTrue(remaining.isNotEmpty(), "live provenance keys must survive pruning")
        assertTrue(remaining.all { it.key != "sync.prov.dl.deadbeef" })
    }

    @Test
    fun `adoptNullOwnership short-circuits after full adoption (P3)`() {
        seedFilter(mode = 7, text = "adopt-me", lastModified = 1_000, username = null)

        service.pull(0L, "A")
        val scansAfterFirst = scanCount()
        assertTrue(scansAfterFirst > 0)

        // 标志置位后：第二次 pull 不再做 NULL 全表扫描
        service.pull(0L, "A")
        assertEquals(scansAfterFirst, scanCount())
    }

    private fun scanCount(): Int =
        org.mockito.Mockito.mockingDetails(filterRepo).invocations
            .count { it.method.name == "findAllByUsernameIsNull" }

    // ==================== A7-T 同步回归锁 ====================
    //
    // 全库审计「已核实无问题」同步不变量的固化锁。方向互补，已有用例不重复：
    //  - Web 本地写 stamping → 增量 pull 可见（history add / clearHistory 墓碑化）：
    //    WebLocalTombstoneSyncTest 已固化（真实 HistoryService + SyncService 共库）；
    //  - Web addFavorite 可见性 / removeFavorite 墓碑下发：WebFavoriteTombstoneSyncTest
    //    （A7-T 新增集成锁，文件锁内唯一新增测试文件）；
    //  - App push 删除墓碑 → 落墓碑行 + bump → 增量 pull 携带：既有
    //    `history delete keeps a tombstone row and bumps lastModified` +
    //    `history tombstone reaches an incremental pull after the bump` 已固化；
    //  - 本节补齐缺口：A7-3 pull 维度、墓碑分支不碰 page、墓碑复活传播、
    //    精确同水位 tie-break（现状语义逐用例注明源码落点）。

    @Test
    fun `duplicate gid rows across users pull only the owning user's row (A7-3)`() {
        // A7-3 pull 维度（push 维度见 `merge tolerates duplicate gid rows across
        // users without exception`）：同 gid 双行分属 A/B 时，A/B 的 pull 各自只能
        // 取到自己的行——pull 仅把 username 交给 (username, lastModified) 派生查询
        // （SyncService.kt pull select 块），不按 gid 反查。fake store 按 gid 键
        // 无法共存两行，与既有 push 维度用例同法按真实库状态覆写 stub；B 行水位
        // 更新——任何丢失 username 过滤的取数（findAll / findAllByGid 复用）都会把
        // B 行漏进 A 的 pull，断言即失败。
        val rowB = HistoryInfoEntity().apply {
            gid = 90; token = "tok90"; title = "B row"; lastModified = 9_000; username = "B"
        }
        val rowA = HistoryInfoEntity().apply {
            gid = 90; token = "tok90"; title = "A row"; lastModified = 2_000; username = "A"
        }
        `when`(historyRepo.findByUsername("A")).thenReturn(listOf(rowA))
        `when`(historyRepo.findByUsername("B")).thenReturn(listOf(rowB))
        `when`(historyRepo.findByUsernameAndLastModifiedGreaterThan("A", 1_000L - SyncService.PULL_OVERLAP_MS)).thenReturn(listOf(rowA))
        `when`(historyRepo.findByUsernameAndLastModifiedGreaterThan("B", 1_000L - SyncService.PULL_OVERLAP_MS)).thenReturn(listOf(rowB))

        // 全量（since=0）：A 只见 A 行，B 只见 B 行。
        val fullA = service.pull(0, "A", "android-test").entities.history
        assertEquals(listOf("A row"), fullA.map { it.title })
        val fullB = service.pull(0, "B", "android-test").entities.history
        assertEquals(listOf("B row"), fullB.map { it.title })

        // 增量：同一约束，且锁住派生查询选择本身。
        val deltaA = service.pull(1_000, "A", "android-test").entities.history
        assertEquals(listOf("A row"), deltaA.map { it.title })
        verify(historyRepo).findByUsernameAndLastModifiedGreaterThan("A", 1_000L - SyncService.PULL_OVERLAP_MS)
    }

    @Test
    fun `history tombstone bump never touches the stored page`() {
        // audit 固化（D3）：applyHistoryFields 的 `page = maxOf(entity.page, dto.page)`
        // 是 page 的唯一落点（SyncService.kt applyHistoryFields）；mergeHistory 的
        // 墓碑分支只置 deleted=true 并 max bump lastModified，不经过
        // applyHistoryFields——incoming 不携带 page（旧端默认 0）的删除 push
        // 不得把已存进度清零。
        seedHistory(gid = 66, lastModified = 1_000, page = 42)

        push("A", history = listOf(hist(66, lastModified = 2_000, deleted = true)))

        val stored = historyRepo.findByGid(66)!!
        assertTrue(stored.deleted)
        assertClockStamped(stored.lastModified, 2_000L, 0L)
        assertEquals(42, stored.page)
    }

    @Test
    fun `history live push resurrects a tombstone and the resurrection reaches the next incremental pull`() {
        // audit 固化（墓碑复活，deleted→false）：存量墓碑时不走策略序（§3.8 镜像，
        // SyncService.kt `if (!existing.deleted)` 跳过），直接落实体专属 LWW
        // ±skew——beyond skew 的 incoming live 复活墓碑；复活路径经过
        // applyHistoryFields，故 page 同样取 max 不回退；复活是 deleted→false 的
        // 事件，最后一个增量 pull 必须携带 deleted=false 的行。
        HistoryInfoEntity().apply {
            gid = 67; token = "tok67"; title = "Hist 67"; time = 100
            lastModified = 1_000; page = 42; deleted = true; username = "A"
        }.let { historyRepo.save(it) }

        val response = push(
            "A",
            history = listOf(hist(67, time = 50, lastModified = 7_000, deleted = false, title = "Revived", page = 5)),
        )

        val stored = historyRepo.findByGid(67)!!
        assertFalse(stored.deleted)
        assertEquals("Revived", stored.title)
        // 复活走 applyHistoryFields：page = max(42, 5) 不回退。
        assertEquals(42, stored.page)
        assertClockStamped(stored.lastModified, 7_000L, 0L)
        assertEquals(1, response.conflicts)

        val pulled = service.pull(1_500, "A", "android-test").entities.history
        assertEquals(listOf(67L), pulled.map { it.gid })
        assertFalse(pulled.single().deleted)
    }

    @Test
    fun `exact lastModified tie now lets the later arrival win (v4 同戳后到胜)`() {
        // v4 翻转：判据是 `incoming.lastModified >= existing.lastModified`——完全同戳时
        // 后到的包胜（旧语义「相等即既有行胜」只在 A/C 同平台 skew 回退里保留）。
        seedFavorite(gid = 45, lastModified = 1_000, title = "Old")
        push("A", favorites = listOf(fav(45, lastModified = 1_000, title = "New")))
        assertEquals("New", favoriteRepo.findByGid(45)!!.title)

        seedHistory(gid = 68, lastModified = 1_000, time = 100)
        push("A", history = listOf(hist(68, time = 100, lastModified = 1_000, title = "New")))
        assertEquals("New", historyRepo.findByGid(68)!!.title)
    }
}
