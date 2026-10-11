package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.ConflictStrategy
import com.hippo.anotherviewer.web.dto.SyncEntityCollection
import com.hippo.anotherviewer.web.dto.SyncFavoriteDto
import com.hippo.anotherviewer.web.dto.SyncPolicyDto
import com.hippo.anotherviewer.web.dto.SyncPushRequest
import com.hippo.anotherviewer.web.entity.LocalFavoriteInfoEntity
import com.hippo.anotherviewer.web.entity.ServerConfigEntity
import com.hippo.anotherviewer.web.repository.BookmarkInfoRepository
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import com.hippo.anotherviewer.web.repository.EhSessionRepository
import com.hippo.anotherviewer.web.repository.FilterRepository
import com.hippo.anotherviewer.web.repository.HistoryInfoRepository
import com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository
import com.hippo.anotherviewer.web.repository.QuickSearchRepository
import com.hippo.anotherviewer.web.repository.DownloadLabelRepository
import com.hippo.anotherviewer.web.repository.ServerConfigRepository
import com.hippo.anotherviewer.web.repository.SyncDeviceRepository
import com.hippo.anotherviewer.web.repository.UserPreferenceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * A7-T 同步回归锁（Web 收藏方向，与 [WebLocalTombstoneSyncTest] 互为镜像——该文件
 * 固化 history add/clearHistory，本文件固化 addFavorite/removeFavorite）：
 *
 *  - Web addFavorite（A7-1 stamping：username + lastModified 落库）→ 属主增量
 *    pull 直接可见；
 *  - Web removeFavorite（A7-2 墓碑化：deleted=true + lastModified bump，行保留）
 *    → 清空前水位的增量 pull 必须下发 deleted=true 的墓碑——物理删时代该响应
 *    为空，删除永不传播，App 侧差分方向也收不到信号。
 *
 * 真实 [FavoriteService] 与真实 [SyncService] 共享同一内存 favorite fake；App
 * push 方向的墓碑传播由 SyncServiceTest 既有用例固化，不在此重复。
 */
class WebFavoriteTombstoneSyncTest {

    private lateinit var favoriteRepository: LocalFavoriteInfoRepository
    private lateinit var favoriteStore: ConcurrentHashMap<String, LocalFavoriteInfoEntity>
    private lateinit var syncService: SyncService
    private lateinit var favoriteService: FavoriteService

    @BeforeEach
    fun setUp() {
        val (repo, store) = inMemoryFavoriteRepo()
        favoriteRepository = repo
        favoriteStore = store
        val serverConfig = newServerConfigService()
        val preferenceRepo = preferenceRepo()
        val historyRepository = mock(HistoryInfoRepository::class.java)
        syncService = SyncService(
            favoriteRepository,
            historyRepository,
            mock(DownloadInfoRepository::class.java),
            mock(BookmarkInfoRepository::class.java),
            mock(FilterRepository::class.java),
            mock(QuickSearchRepository::class.java),
            mock(DownloadLabelRepository::class.java),
            deviceRepo(),
            preferenceRepo,
            UserPreferenceService(preferenceRepo, newServerConfigService()),
            serverConfig,
            mock(EhSessionRepository::class.java),
            mock(SiteSessionManager::class.java),
            SyncClock(),
            mock(org.springframework.context.ApplicationEventPublisher::class.java),
        )
        // 钉在策略 B（lww）= v4 默认策略（与 WebLocalTombstoneSyncTest 一致）。
        syncService.updatePolicy(SyncPolicyDto(conflictStrategy = ConflictStrategy.LWW))
        val historyService = HistoryService(historyRepository, stubProvider("alice"))
        favoriteService = FavoriteService(
            favoriteRepository,
            historyRepository,
            mock(DownloadInfoRepository::class.java),
            historyService,
            stubProvider("alice"),
        )
    }

    @Test
    fun `web addFavorite row is stamped and reaches the owning user's incremental pull`() {
        // A7-1 stamping 的增量可达性（收藏方向）：Web 新增收藏行当场落
        // username/lastModified，属主增量 pull 直接可见（不依赖 adoptNullOwnership
        // 认领 NULL 行）。history 方向见 WebLocalTombstoneSyncTest 同名语义用例。
        val beforeAdd = System.currentTimeMillis() - 1000

        assertTrue(favoriteService.addFavorite(9L, "tok9", "Gallery 9", category = 1))

        val pulled = syncService.pull(beforeAdd, "alice").entities.favorites
        assertEquals(1, pulled.size)
        assertEquals(9L, pulled.single().gid)
        assertFalse(pulled.single().deleted)
        assertEquals("alice", favoriteStore.values.single().username)
        assertTrue(favoriteStore.values.single().lastModified > beforeAdd)
    }

    @Test
    fun `web removeFavorite tombstone reaches the owning user's incremental pull`() {
        // A7-2 核心验收（收藏方向）：App push 一条活收藏行 → Web 取消收藏（墓碑化，
        // 不物理删）→ App 以取消前水位做增量 pull：必须收到 deleted=true 的墓碑。
        syncService.push(
            SyncPushRequest(
                entities = SyncEntityCollection(
                    favorites = listOf(
                        SyncFavoriteDto(gid = 10L, token = "fav-token", title = "Fav Ten", lastModified = 1000L),
                    )
                ),
                deviceId = "android-fav-tombstone",
                timestamp = 0L,
            ),
            "alice",
        )
        assertEquals(1, favoriteStore.size)
        assertTrue(favoriteStore.values.single().deleted.not())

        // 水位提前 1s，规避 currentTimeMillis 毫秒粒度下 `lastModified > since` 的等值失配。
        val beforeRemove = System.currentTimeMillis() - 1000
        assertTrue(favoriteService.removeFavorite(10L))

        // 行仍在库——物理删才会让 DB 行数下降（行为变化，PR 需声明）。
        assertEquals(1, favoriteStore.size)

        val pulled = syncService.pull(beforeRemove, "alice").entities.favorites
        assertEquals(1, pulled.size)
        assertEquals(10L, pulled.single().gid)
        assertTrue(pulled.single().deleted, "web-initiated unfavorite must reach the app as a tombstone")
        assertTrue(pulled.single().lastModified > beforeRemove)

        // 墓碑行属主保持 alice（Web 写路径不跨用户改行），行本身保留（无 GC）。
        val row = favoriteStore.values.single()
        assertEquals("alice", row.username)
        assertTrue(row.deleted)
    }

    // ── helpers（内存 fake，模式同 WebLocalTombstoneSyncTest） ──

    private fun stubProvider(name: String): com.hippo.anotherviewer.web.config.CurrentUsernameProvider =
        mock(com.hippo.anotherviewer.web.config.CurrentUsernameProvider::class.java)
            .apply { `when`(currentUsername()).thenReturn(name) }

    private fun inMemoryFavoriteRepo(): Pair<LocalFavoriteInfoRepository, ConcurrentHashMap<String, LocalFavoriteInfoEntity>> {
        val repo = mock(LocalFavoriteInfoRepository::class.java)
        val store = ConcurrentHashMap<String, LocalFavoriteInfoEntity>()
        `when`(repo.save(any(LocalFavoriteInfoEntity::class.java))).thenAnswer { inv ->
            val entity = inv.getArgument<LocalFavoriteInfoEntity>(0)
            store[entity.gid.toString()] = entity
            entity
        }
        `when`(repo.findByGid(anyLong())).thenAnswer { inv -> store[inv.getArgument<Long>(0).toString()] }
        `when`(repo.findAllByGid(anyLong())).thenAnswer { inv ->
            store[inv.getArgument<Long>(0).toString()]?.let { listOf(it) } ?: emptyList<LocalFavoriteInfoEntity>()
        }
        `when`(repo.findAllByUsernameIsNull()).thenAnswer { store.values.filter { it.username == null } }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) }
        }
        `when`(repo.findByUsernameAndLastModifiedGreaterThan(anyString(), anyLong())).thenAnswer { inv ->
            val u = inv.getArgument<String>(0)
            val lm = inv.getArgument<Long>(1)
            store.values.filter { it.username == u && it.lastModified > lm }
        }
        return repo to store
    }

    private fun newServerConfigService(): ServerConfigService {
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

    private fun deviceRepo(): SyncDeviceRepository {
        val repo = mock(SyncDeviceRepository::class.java)
        val store = ConcurrentHashMap<String, com.hippo.anotherviewer.web.entity.SyncDeviceEntity>()
        `when`(repo.save(any(com.hippo.anotherviewer.web.entity.SyncDeviceEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<com.hippo.anotherviewer.web.entity.SyncDeviceEntity>(0)
            store[e.deviceId] = e
            e
        }
        `when`(repo.findByDeviceId(anyString())).thenAnswer { inv -> store[inv.getArgument(0)] }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        return repo
    }

    private fun preferenceRepo(): UserPreferenceRepository {
        val repo = mock(UserPreferenceRepository::class.java)
        val store = ConcurrentHashMap<String, com.hippo.anotherviewer.web.entity.UserPreferenceEntity>()
        `when`(repo.save(any(com.hippo.anotherviewer.web.entity.UserPreferenceEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<com.hippo.anotherviewer.web.entity.UserPreferenceEntity>(0)
            store[e.username] = e
            e
        }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv -> store[inv.getArgument(0)] }
        return repo
    }
}
