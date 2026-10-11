package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.captureK
import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.ConflictStrategy
import com.hippo.anotherviewer.web.dto.SyncEntityCollection
import com.hippo.anotherviewer.web.dto.SyncFavoriteDto
import com.hippo.anotherviewer.web.dto.SyncHistoryDto
import com.hippo.anotherviewer.web.dto.SyncPolicyDto
import com.hippo.anotherviewer.web.dto.SyncPreferencesDto
import com.hippo.anotherviewer.web.dto.SyncPushRequest
import com.hippo.anotherviewer.web.entity.DownloadInfoEntity
import com.hippo.anotherviewer.web.entity.HistoryInfoEntity
import com.hippo.anotherviewer.web.entity.LocalFavoriteInfoEntity
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * v4 服务器统一打戳 + sync.update 事件（WP2）回归锁：
 *
 *  - push 同批多行落库戳严格递增且全部来自 SyncClock（客户端上送值不落库）；
 *  - 同戳后到胜（判据 `incoming >= existing`）；
 *  - pull 增量 overlap：行 lastModified == since - 500 仍返回（吸收并发提交）；
 *  - push 事务内聚合 8 类计数并发布 SyncUpdateEvent（含 preferences）。
 */
class SyncServerStampingTest {

    private lateinit var favoriteRepo: LocalFavoriteInfoRepository
    private lateinit var historyRepo: HistoryInfoRepository
    private lateinit var downloadRepo: DownloadInfoRepository
    private lateinit var preferenceRepo: UserPreferenceRepository
    private lateinit var eventPublisher: org.springframework.context.ApplicationEventPublisher
    private lateinit var clock: SyncClock
    private lateinit var service: SyncService

    @BeforeEach
    fun setUp() {
        favoriteRepo = fakeFavoriteRepo()
        historyRepo = fakeHistoryRepo()
        downloadRepo = fakeDownloadRepo()
        preferenceRepo = fakePreferenceRepo()
        eventPublisher = mock(org.springframework.context.ApplicationEventPublisher::class.java)
        clock = SyncClock()
        val serverConfig = fakeServerConfig()
        service = SyncService(
            favoriteRepo, historyRepo, downloadRepo,
            mock(BookmarkInfoRepository::class.java),
            mock(FilterRepository::class.java),
            mock(QuickSearchRepository::class.java),
            mock(DownloadLabelRepository::class.java),
            fakeDeviceRepo(), preferenceRepo,
            UserPreferenceService(preferenceRepo, serverConfig, clock),
            serverConfig,
            mock(EhSessionRepository::class.java),
            mock(SiteSessionManager::class.java),
            clock,
            eventPublisher,
        )
        service.updatePolicy(SyncPolicyDto(conflictStrategy = ConflictStrategy.LWW))
    }

    @Test
    fun `push stamps every row with a strictly increasing server clock value`() {
        val pushed = service.push(
            request(
                favorites = listOf(fav(1, 1_000), fav(2, 1_000)),
                history = listOf(hist(3, 1_000)),
            ),
            "A",
        )

        val stamps = listOf(
            favoriteRepo.findByGid(1)!!.lastModified,
            favoriteRepo.findByGid(2)!!.lastModified,
            historyRepo.findByGid(3)!!.lastModified,
        )
        // 同批多行严格递增（同毫秒内 `last+1` 兜底）——相等戳会让 LWW 与增量 pull 失去区分度。
        assertEquals(stamps.sorted(), stamps, "同批多行的服务器戳必须严格递增")
        assertEquals(stamps.size, stamps.distinct().size, "不得同戳")
        assertTrue(stamps.all { it > 0 }, "服务器戳 > 0")
        assertFalse(stamps.any { it == 1_000L }, "客户端上送值不落库")
        // push 的 serverTimestamp 与 updateDevice 用同一 clock.next() 值 → 响应戳 == 时钟水位。
        assertEquals(pushed.serverTimestamp, clock.current())
        assertTrue(stamps.all { it <= pushed.serverTimestamp }, "实体戳先于 push 末尾发放的 serverTimestamp")
    }

    @Test
    fun `request arriving with a stamp equal to the stored one still wins (later arrival wins on tie)`() {
        service.push(request(favorites = listOf(fav(10, 1_000, title = "first"))), "A")
        val storedStamp = favoriteRepo.findByGid(10)!!.lastModified

        // 同戳后到胜：incoming.lastModified == existing.lastModified → 来包生效。
        val response = service.push(request(favorites = listOf(fav(10, storedStamp, title = "second"))), "A")

        assertEquals("second", favoriteRepo.findByGid(10)!!.title)
        assertEquals(1, response.conflicts)
    }

    @Test
    fun `pull returns rows inside the overlap window (row stamp == since - 500)`() {
        seedFavorite(gid = 20, lastModified = 9_500) // == since - 500：落在 overlap 窗口内
        seedFavorite(gid = 21, lastModified = 8_500) // < since - 1000：窗口外
        org.mockito.Mockito.clearInvocations(favoriteRepo)

        val pulled = service.pull(10_000, "A", "android-x").entities.favorites

        assertEquals(setOf(20L), pulled.map { it.gid }.toSet())
        // 派生查询拿到的是 overlap 调整后的下界（10_000 - PULL_OVERLAP_MS）。
        verify(favoriteRepo).findByUsernameAndLastModifiedGreaterThan("A", 10_000L - SyncService.PULL_OVERLAP_MS)
    }

    @Test
    fun `push publishes a sync update event with per-entity counts`() {
        seedFavorite(gid = 1, lastModified = 1_000)
        seedFavorite(gid = 2, lastModified = 1_000)

        val pushed = service.push(
            request(
                favorites = listOf(
                    fav(1, 6_000, title = "win"), // 覆盖存量 → 计 1
                    fav(2, 6_000, title = "win"), // 覆盖存量 → 计 1
                    fav(99, 1_000, title = "new"), // 新插 → 不计（无冲突）
                ),
                history = listOf(hist(3, 1_000)), // 新插 → 不计
                preferences = SyncPreferencesDto(preferences = """{"general":{"lang":"zh"}}""", lastModified = 1L, deviceId = "android-x"),
            ),
            "A",
        )

        val captor = ArgumentCaptor.forClass(SyncUpdateEvent::class.java)
        verify(eventPublisher).publishEvent(captureK(captor))
        val event = captor.value
        assertEquals("android-x", event.deviceId)
        assertEquals(pushed.serverTimestamp, event.serverTimestamp)
        assertEquals(2, event.counts["favorites"])
        assertEquals(1, event.counts["preferences"], "preferences 实际写入（replace 生效）计 1")
        assertFalse(event.counts.containsKey("history"), "仅计数 > 0 的实体入 counts")
        assertFalse(event.counts.containsKey("downloads"))
    }

    // ---- helpers ----

    private fun request(
        favorites: List<SyncFavoriteDto> = emptyList(),
        history: List<SyncHistoryDto> = emptyList(),
        preferences: SyncPreferencesDto? = null,
    ) = SyncPushRequest(
        entities = SyncEntityCollection(favorites = favorites, history = history, preferences = preferences),
        deviceId = "android-x",
        timestamp = 0L,
    )

    private fun fav(gid: Long, lastModified: Long, title: String = "Fav $gid") =
        SyncFavoriteDto(gid = gid, token = "tok$gid", title = title, lastModified = lastModified)

    private fun hist(gid: Long, lastModified: Long, title: String = "Hist $gid") =
        SyncHistoryDto(gid = gid, token = "tok$gid", title = title, lastModified = lastModified)

    private fun seedFavorite(gid: Long, lastModified: Long, deleted: Boolean = false, username: String? = "A") {
        LocalFavoriteInfoEntity().apply {
            this.gid = gid
            token = "tok$gid"
            title = "Fav $gid"
            this.lastModified = lastModified
            this.deleted = deleted
            this.username = username
        }.let { favoriteRepo.save(it) }
    }

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
        `when`(repo.existsById(anyString())).thenAnswer { inv -> store.containsKey(inv.getArgument<String>(0)) }
        return ServerConfigService(repo, EncryptionService(), SiteCoreConfigProperties())
    }

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
        `when`(repo.findAllByUsernameIsNull()).thenAnswer { store.values.filter { it.username == null } }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) }
        }
        `when`(repo.findByUsernameAndLastModifiedGreaterThan(anyString(), anyLong())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.lastModified > inv.getArgument<Long>(1) }
        }
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
        `when`(repo.findAllByUsernameIsNull()).thenAnswer { store.values.filter { it.username == null } }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) }
        }
        `when`(repo.findByUsernameAndLastModifiedGreaterThan(anyString(), anyLong())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.lastModified > inv.getArgument<Long>(1) }
        }
        return repo
    }

    private fun fakeDownloadRepo(): DownloadInfoRepository {
        val repo = mock(DownloadInfoRepository::class.java)
        val store = ConcurrentHashMap<Long, DownloadInfoEntity>()
        `when`(repo.save(any(DownloadInfoEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<DownloadInfoEntity>(0); store[e.gid] = e; e
        }
        `when`(repo.findAllByUsernameIsNull()).thenAnswer { store.values.filter { it.username == null } }
        return repo
    }

    private fun fakeDeviceRepo(): SyncDeviceRepository {
        val repo = mock(SyncDeviceRepository::class.java)
        val store = ConcurrentHashMap<String, SyncDeviceEntity>()
        `when`(repo.save(any(SyncDeviceEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<SyncDeviceEntity>(0); store[e.deviceId] = e; e
        }
        `when`(repo.findByDeviceId(anyString())).thenAnswer { inv -> store[inv.getArgument<String>(0)] }
        return repo
    }

    private fun fakePreferenceRepo(): UserPreferenceRepository {
        val repo = mock(UserPreferenceRepository::class.java)
        val store = ConcurrentHashMap<String, UserPreferenceEntity>()
        `when`(repo.save(any(UserPreferenceEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<UserPreferenceEntity>(0); store[e.username] = e; e
        }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv -> store[inv.getArgument<String>(0)] }
        return repo
    }
}
