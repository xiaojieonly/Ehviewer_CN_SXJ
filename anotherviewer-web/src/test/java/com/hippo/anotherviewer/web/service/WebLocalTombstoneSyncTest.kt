package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.ConflictStrategy
import com.hippo.anotherviewer.web.dto.SyncEntityCollection
import com.hippo.anotherviewer.web.dto.SyncHistoryDto
import com.hippo.anotherviewer.web.dto.SyncPolicyDto
import com.hippo.anotherviewer.web.dto.SyncPushRequest
import com.hippo.anotherviewer.web.entity.HistoryInfoEntity
import com.hippo.anotherviewer.web.entity.ServerConfigEntity
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import com.hippo.anotherviewer.web.repository.BookmarkInfoRepository
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
import org.junit.jupiter.api.Assertions.assertNotNull
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
 * A7-2 核心验收（docs/plan-a7-design.md §4.4「clearHistory tombstones reach an
 * incremental pull」）：Web 本地删除 = 墓碑行，必须是 App 增量 pull 可见的事件。
 *
 * 对应 SyncServiceTest 既有 `history tombstone reaches an incremental pull after
 * the bump`（App push 删除方向）的 Web 本地删除版本：真实 [HistoryService] 与真实
 * [SyncService] 共享同一内存 history fake——clearHistory 墓碑化后，since=清空前的
 * 增量 pull 必须返回 deleted=true 的行（物理删时代该响应为空，删除永不传播）。
 */
class WebLocalTombstoneSyncTest {

    private lateinit var historyRepository: HistoryInfoRepository
    private lateinit var historyStore: ConcurrentHashMap<String, HistoryInfoEntity>
    private lateinit var syncService: SyncService
    private lateinit var historyService: HistoryService

    @BeforeEach
    fun setUp() {
        val (repo, store) = inMemoryHistoryRepo()
        historyRepository = repo
        historyStore = store
        val serverConfig = newServerConfigService()
        val preferenceRepo = preferenceRepo()
        syncService = SyncService(
            mock(LocalFavoriteInfoRepository::class.java),
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
        // 钉在策略 B（lww）= v4 默认策略（与 SyncControllerTest 服务层一致）。
        syncService.updatePolicy(SyncPolicyDto(conflictStrategy = ConflictStrategy.LWW))
        historyService = HistoryService(historyRepository, stubProvider("alice"))
    }

    @Test
    fun `clearHistory tombstones reach an incremental pull`() {
        // 1. App push 一条活历史行（username=alice, lastModified=1000, deleted=false）。
        syncService.push(
            SyncPushRequest(
                entities = SyncEntityCollection(
                    history = listOf(
                        SyncHistoryDto(gid = 2L, token = "hist-token", title = "History Two", time = 2000L, lastModified = 1000L),
                    )
                ),
                deviceId = "android-tombstone",
                timestamp = 0L,
            ),
            "alice",
        )
        assertEquals(1, historyStore.size)
        assertTrue(historyStore.values.single().deleted.not())

        // 2. Web 端清空历史（墓碑化，不物理删）。
        // 水位提前 1s，规避 currentTimeMillis 毫秒粒度下 `lastModified > since` 的等值失配。
        val beforeClear = System.currentTimeMillis() - 1000
        historyService.clearHistory()

        // 行仍在库——物理删才会让 DB 行数下降（行为变化，PR 需声明）。
        assertEquals(1, historyStore.size)

        // 3. App 以清空前的水位做增量 pull：必须收到 deleted=true 的墓碑。
        val pulled = syncService.pull(beforeClear, "alice").entities
        assertEquals(1, pulled.history.size)
        val tombstone = pulled.history.single()
        assertEquals(2L, tombstone.gid)
        assertTrue(tombstone.deleted, "web-initiated clear must reach the app as a tombstone")
        assertTrue(tombstone.lastModified > beforeClear)

        // 4. 墓碑行属主为 alice（Web 写路径 stamping），行本身保留（无 GC）。
        val row = historyStore.values.single()
        assertEquals("alice", row.username)
        assertTrue(row.deleted)
    }

    @Test
    fun `web-add history row is stamped and pulled by the owning user`() {
        // A7-1 stamping 的增量可达性：Web 新增历史行当场落 username/lastModified，
        // 属主增量 pull 直接可见（不再依赖 adoptNullOwnership 认领 NULL 行）。
        val beforeAdd = System.currentTimeMillis() - 1000
        historyService.addHistory(7L, "tok7", "Gallery 7", null, null, 1, 5.0f, mode = 3)

        val pulled = syncService.pull(beforeAdd, "alice").entities
        assertEquals(1, pulled.history.size)
        assertEquals(7L, pulled.history.single().gid)
        assertEquals("alice", historyStore.values.single().username)
        assertTrue(historyStore.values.single().lastModified > beforeAdd)
    }

    // ── helpers（内存 fake，模式同 SyncControllerTest 服务层） ──

    private fun stubProvider(name: String): com.hippo.anotherviewer.web.config.CurrentUsernameProvider =
        mock(com.hippo.anotherviewer.web.config.CurrentUsernameProvider::class.java)
            .apply { `when`(currentUsername()).thenReturn(name) }

    private fun inMemoryHistoryRepo(): Pair<HistoryInfoRepository, ConcurrentHashMap<String, HistoryInfoEntity>> {
        val repo = mock(HistoryInfoRepository::class.java)
        val store = ConcurrentHashMap<String, HistoryInfoEntity>()
        `when`(repo.save(any(HistoryInfoEntity::class.java))).thenAnswer { inv ->
            val entity = inv.getArgument<HistoryInfoEntity>(0)
            store[entity.gid.toString()] = entity
            entity
        }
        `when`(repo.findByGid(anyLong())).thenAnswer { inv -> store[inv.getArgument<Long>(0).toString()] }
        `when`(repo.findById(anyLong())).thenAnswer { inv ->
            Optional.ofNullable(store[inv.getArgument<Long>(0).toString()])
        }
        `when`(repo.findAllByUsernameIsNull()).thenAnswer { store.values.filter { it.username == null } }
        `when`(repo.countByUsername(anyString())).thenAnswer { inv ->
            store.values.count { it.username == inv.getArgument<String>(0) }.toLong()
        }
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
