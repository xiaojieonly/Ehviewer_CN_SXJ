package com.hippo.anotherviewer.web.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.argThatK
import com.hippo.anotherviewer.web.config.SecurityConfig
import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.ConflictStrategy
import com.hippo.anotherviewer.web.dto.ConnectedDeviceDto
import com.hippo.anotherviewer.web.dto.DeviceInfoDto
import com.hippo.anotherviewer.web.dto.EntityCountsDto
import com.hippo.anotherviewer.web.dto.SyncBookmarkDto
import com.hippo.anotherviewer.web.dto.SyncDownloadDto
import com.hippo.anotherviewer.web.dto.SyncDownloadLabelDto
import com.hippo.anotherviewer.web.dto.SyncEntityCollection
import com.hippo.anotherviewer.web.dto.SyncFavoriteDto
import com.hippo.anotherviewer.web.dto.SyncFilterDto
import com.hippo.anotherviewer.web.dto.SyncHistoryDto
import com.hippo.anotherviewer.web.dto.SyncPolicyDto
import com.hippo.anotherviewer.web.dto.SyncPullResponse
import com.hippo.anotherviewer.web.dto.SyncPushRequest
import com.hippo.anotherviewer.web.dto.SyncPushResponse
import com.hippo.anotherviewer.web.dto.SyncQuickSearchDto
import com.hippo.anotherviewer.web.dto.SyncStatusResponse
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
import com.hippo.anotherviewer.web.service.EncryptionService
import com.hippo.anotherviewer.web.service.SiteAuthService
import com.hippo.anotherviewer.web.service.ServerConfigService
import com.hippo.anotherviewer.web.service.SiteSessionManager
import com.hippo.anotherviewer.web.service.SyncService
import com.hippo.anotherviewer.web.service.UserPreferenceService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.concurrent.ConcurrentHashMap

/**
 * Integration tests for the sync API ([SyncController]) and its backing
 * [SyncService] round trip.
 *
 * Two layers, mirroring AuthControllerTest:
 *
 * 1. HTTP layer: a @WebMvcTest slice that imports the real [SecurityConfig]
 *    filter chain, so the bearer-token semantics asserted here are exactly the
 *    production ones (missing/invalid token on /api routes -> 401 with the
 *    JSON body written by the SecurityConfig entry point). Services are mocked.
 *
 * 2. Service layer: the real [SyncService] wired to in-memory repository
 *    fakes, asserting push -> pull round trips, incremental `since` filtering,
 *    device tracking and lastSeen updates.
 */
@WebMvcTest(SyncController::class)
@Import(SecurityConfig::class)
class SyncControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockBean
    lateinit var syncService: SyncService

    @MockBean
    lateinit var authService: SiteAuthService

    @MockBean
    lateinit var serverConfigService: ServerConfigService

    @BeforeEach
    fun setUp() {
        // Auth is enforced like production would be with require_auth=true.
        `when`(serverConfigService.getBoolean(ServerConfigService.KEY_REQUIRE_AUTH, false)).thenReturn(true)
        `when`(authService.validateToken("valid-token")).thenReturn("alice")
    }

    // ---------------------------------------------------------------------
    // HTTP layer
    // ---------------------------------------------------------------------

    @Test
    fun `push with all seven entity types parses the body and returns the contract response`() {
        val mapper = jacksonObjectMapper()
        val request = pushRequest("android-http")
        `when`(syncService.push(any(SyncPushRequest::class.java), anyString()))
            .thenReturn(SyncPushResponse(success = true, serverTimestamp = 12345L, conflicts = 0))

        mockMvc.perform(
            post("/api/v1/sync/push")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.serverTimestamp").value(12345L))
            .andExpect(jsonPath("$.conflicts").value(0))

        verify(syncService).push(
            argThatK<SyncPushRequest> {
                it.entities.favorites.size == 1 &&
                    it.entities.favorites[0].gid == 1L &&
                    it.entities.favorites[0].token == "fav-token" &&
                    it.entities.history.size == 1 &&
                    it.entities.history[0].gid == 2L &&
                    it.entities.downloads.size == 1 &&
                    it.entities.downloads[0].label == "DL-Label-A" &&
                    it.entities.bookmarks.size == 1 &&
                    it.entities.bookmarks[0].page == 7 &&
                    it.entities.filters.size == 1 &&
                    it.entities.filters[0].text == "filter-text" &&
                    it.entities.quickSearches.size == 1 &&
                    it.entities.quickSearches[0].name == "quick-search-1" &&
                    it.entities.downloadLabels.size == 1 &&
                    it.entities.downloadLabels[0].label == "Label One" &&
                    it.deviceId == "android-http" &&
                    it.timestamp == request.timestamp
            },
            argThatK { it == "alice" },
        )
    }

    @Test
    fun `pull forwards since and deviceId and returns entities`() {
        `when`(syncService.pull(anyLong(), anyString(), anyString())).thenReturn(
            SyncPullResponse(
                entities = SyncEntityCollection(
                    favorites = listOf(SyncFavoriteDto(gid = 1L, token = "fav-token", title = "Favorite One")),
                ),
                serverTimestamp = 6789L,
            )
        )

        mockMvc.perform(
            get("/api/v1/sync/pull")
                .header("Authorization", "Bearer valid-token")
                .param("since", "1000")
                .param("deviceId", "android-http")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.serverTimestamp").value(6789L))
            .andExpect(jsonPath("$.entities.favorites[0].gid").value(1L))
            .andExpect(jsonPath("$.entities.favorites[0].title").value("Favorite One"))

        verify(syncService).pull(1000L, "alice", "android-http")
    }

    @Test
    fun `status returns connected devices and entity counts`() {
        `when`(syncService.status("alice")).thenReturn(
            SyncStatusResponse(
                lastSyncTimestamp = 5000L,
                connectedDevices = listOf(ConnectedDeviceDto("android-1", "Phone", "android", 4000L)),
                entityCounts = EntityCountsDto(favorites = 3, history = 2, downloads = 1),
            )
        )

        mockMvc.perform(get("/api/v1/sync/status").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.lastSyncTimestamp").value(5000L))
            .andExpect(jsonPath("$.connectedDevices[0].deviceId").value("android-1"))
            .andExpect(jsonPath("$.connectedDevices[0].deviceName").value("Phone"))
            .andExpect(jsonPath("$.connectedDevices[0].platform").value("android"))
            .andExpect(jsonPath("$.connectedDevices[0].lastSeen").value(4000L))
            .andExpect(jsonPath("$.entityCounts.favorites").value(3))
            .andExpect(jsonPath("$.entityCounts.history").value(2))
            .andExpect(jsonPath("$.entityCounts.downloads").value(1))
    }

    @Test
    fun `devices returns the paired device list`() {
        `when`(syncService.listDevices("alice")).thenReturn(
            listOf(DeviceInfoDto("android-1", "Phone", "android", pairedAt = 1000L, lastSeen = 2000L))
        )

        mockMvc.perform(get("/api/v1/sync/devices").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].deviceId").value("android-1"))
            .andExpect(jsonPath("$[0].deviceName").value("Phone"))
            .andExpect(jsonPath("$[0].pairedAt").value(1000L))
    }

    @Test
    fun `revoking an own device returns success`() {
        `when`(authService.revokeDevice("android-1", "alice")).thenReturn(true)

        mockMvc.perform(delete("/api/v1/sync/devices/android-1").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
    }

    @Test
    fun `revoking a foreign device is forbidden`() {
        `when`(authService.revokeDevice("android-1", "alice")).thenReturn(false)

        mockMvc.perform(delete("/api/v1/sync/devices/android-1").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    fun `push parses the v2 policy field and forwards it to the service`() {
        `when`(syncService.push(any(SyncPushRequest::class.java), anyString()))
            .thenReturn(SyncPushResponse(success = true, serverTimestamp = 1L, conflicts = 0))

        mockMvc.perform(
            post("/api/v1/sync/push")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"entities":{},"deviceId":"android-http","timestamp":0,""" +
                        """"policy":{"conflictStrategy":"lww","clientTier":2,"autoSyncIntervalSec":60}}"""
                )
        )
            .andExpect(status().isOk)

        verify(syncService).push(
            argThatK<SyncPushRequest> {
                it.policy == SyncPolicyDto(ConflictStrategy.LWW, clientTier = 2, autoSyncIntervalSec = 60)
            },
            argThatK { it == "alice" },
        )
    }

    @Test
    fun `push without policy (legacy client) parses cleanly and succeeds`() {
        `when`(syncService.push(any(SyncPushRequest::class.java), anyString()))
            .thenReturn(SyncPushResponse(success = true, serverTimestamp = 1L, conflicts = 0))

        mockMvc.perform(
            post("/api/v1/sync/push")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"entities":{},"deviceId":"android-http","timestamp":0}""")
        )
            .andExpect(status().isOk)

        verify(syncService).push(
            argThatK<SyncPushRequest> { it.policy == null && it.deviceId == "android-http" },
            argThatK { it == "alice" },
        )
    }

    @Test
    fun `android push with invalid policy values is a 400 validation error (D2 = PUT semantics)`() {
        `when`(syncService.push(any(SyncPushRequest::class.java), anyString()))
            .thenThrow(IllegalArgumentException("clientTier must be one of 0..3"))

        mockMvc.perform(
            post("/api/v1/sync/push")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"entities":{},"deviceId":"android-http","timestamp":0,""" +
                        """"policy":{"conflictStrategy":"device_priority","clientTier":9,"autoSyncIntervalSec":60}}"""
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("clientTier must be one of 0..3"))
    }

    @Test
    fun `pull returns the current policy in the response`() {
        `when`(syncService.pull(anyLong(), anyString(), anyString())).thenReturn(
            SyncPullResponse(
                entities = SyncEntityCollection(),
                serverTimestamp = 6789L,
                policy = SyncPolicyDto(ConflictStrategy.WEB_PRIORITY, clientTier = 2, autoSyncIntervalSec = 30),
            )
        )

        mockMvc.perform(
            get("/api/v1/sync/pull")
                .header("Authorization", "Bearer valid-token")
                .param("since", "1000")
                .param("deviceId", "android-http")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.policy.conflictStrategy").value("web_priority"))
            .andExpect(jsonPath("$.policy.clientTier").value(2))
            .andExpect(jsonPath("$.policy.autoSyncIntervalSec").value(30))
    }

    @Test
    fun `push without bearer token is rejected with 401 per SecurityConfig`() {
        mockMvc.perform(
            post("/api/v1/sync/push")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"entities":{},"deviceId":"android-x","timestamp":0}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Authentication required"))
    }

    @Test
    fun `push with an unknown bearer token is rejected with 401`() {
        mockMvc.perform(
            post("/api/v1/sync/push")
                .header("Authorization", "Bearer bogus-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"entities":{},"deviceId":"android-x","timestamp":0}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Authentication required"))
    }

    // ---------------------------------------------------------------------
    // Service layer: real SyncService + in-memory repository fakes
    // ---------------------------------------------------------------------

    @Test
    fun `push seven entity types then pull since 0 returns all with matching fields`() {
        val service = newSyncService()
        val pushed = service.push(pushRequest("android-t1"), "alice")

        assertTrue(pushed.success)
        assertEquals(0, pushed.conflicts)

        val e = service.pull(0L, "alice").entities
        assertEquals(1, e.favorites.size)
        assertEquals(1L, e.favorites[0].gid)
        assertEquals("fav-token", e.favorites[0].token)
        assertEquals("Favorite One", e.favorites[0].title)
        assertEquals(2, e.favorites[0].favoriteSlot)

        assertEquals(1, e.history.size)
        assertEquals(2L, e.history[0].gid)
        assertEquals("History Two", e.history[0].title)
        assertEquals(2000L, e.history[0].time)

        assertEquals(1, e.downloads.size)
        assertEquals(3L, e.downloads[0].gid)
        assertEquals("Download Three", e.downloads[0].title)
        assertEquals(10, e.downloads[0].total)
        assertEquals(5, e.downloads[0].finished)
        // NOTE: the string label on a download row is not persisted (the entity
        // stores an Int label index); label names round-trip via downloadLabels.

        assertEquals(1, e.bookmarks.size)
        assertEquals(4L, e.bookmarks[0].gid)
        assertEquals(7, e.bookmarks[0].page)

        assertEquals(1, e.filters.size)
        assertEquals("filter-text", e.filters[0].text)
        assertEquals(0, e.filters[0].mode)
        assertTrue(e.filters[0].enabled)

        assertEquals(1, e.quickSearches.size)
        assertEquals("quick-search-1", e.quickSearches[0].name)
        assertEquals("sakura", e.quickSearches[0].keyword)

        // M-14: push 的 download 携带 label "DL-Label-A" 时服务端自动补建该标签行，
        // pull 的 adoptNullOwnership 认领后随全量拉取一并返回（Label One + DL-Label-A）。
        assertEquals(2, e.downloadLabels.size)
        assertEquals(setOf("Label One", "DL-Label-A"), e.downloadLabels.map { it.label }.toSet())

        // Another user's pull must not leak alice's rows.
        val other = service.pull(0L, "bob").entities
        assertTrue(other.favorites.isEmpty())
        assertTrue(other.history.isEmpty())
        assertTrue(other.downloads.isEmpty())
        assertTrue(other.bookmarks.isEmpty())
        assertTrue(other.filters.isEmpty())
        assertTrue(other.quickSearches.isEmpty())
        assertTrue(other.downloadLabels.isEmpty())
    }

    @Test
    fun `pull since the push server timestamp returns no new changes`() {
        val service = newSyncService()
        val pushed = service.push(pushRequest("android-t2"), "alice")

        val e = service.pull(pushed.serverTimestamp, "alice").entities
        assertTrue(e.favorites.isEmpty())
        assertTrue(e.history.isEmpty())
        assertTrue(e.downloads.isEmpty())
        assertTrue(e.bookmarks.isEmpty())
        assertTrue(e.filters.isEmpty())
        assertTrue(e.quickSearches.isEmpty())
        assertTrue(e.downloadLabels.isEmpty())
    }

    @Test
    fun `pull since zero returns records with zero lastModified`() {
        val service = newSyncService()
        // 手工构造 lastModified=0 的下载记录（如 time=0 的旧记录），经 push 入库。
        val req = pushRequest("android-t2b")
        val zero = req.entities.downloads.map { it.copy(lastModified = 0L) }
        service.push(req.copy(entities = req.entities.copy(downloads = zero)), "alice")

        val e = service.pull(0, "alice").entities
        assertEquals(1, e.downloads.size)
        assertEquals(0L, e.downloads[0].lastModified)
        // since>0 时这些记录不再返回（增量语义不受影响）。
        assertTrue(service.pull(1, "alice").entities.downloads.isEmpty())
    }

    @Test
    fun `push registers the device and status tracks it with lastSeen`() {
        val service = newSyncService()
        val pushed = service.push(pushRequest("android-t3"), "alice")

        val status = service.status("alice")
        assertEquals(1, status.connectedDevices.size)
        assertEquals("android-t3", status.connectedDevices[0].deviceId)
        assertEquals("android", status.connectedDevices[0].platform)
        assertEquals(pushed.serverTimestamp, status.connectedDevices[0].lastSeen)
        assertEquals(pushed.serverTimestamp, status.lastSyncTimestamp)
        assertEquals(1, status.entityCounts.favorites)
        assertEquals(1, status.entityCounts.history)
        assertEquals(1, status.entityCounts.downloads)
        assertEquals(1, status.entityCounts.bookmarks)
        assertEquals(1, status.entityCounts.filters)
        assertEquals(1, status.entityCounts.quickSearches)
        // MASTER-2026-08-22 P3：自动补建标签（M-14 resolveLabelId）创建即落
        // username（不再留 NULL 待收养），故 push 后 status 计数立即含
        // "Label One" + "DL-Label-A" 两行。
        assertEquals(2, status.entityCounts.downloadLabels)

        // A later push bumps lastSeen.
        val pushedAgain = service.push(pushRequest("android-t3"), "alice")
        assertEquals(pushedAgain.serverTimestamp, service.status("alice").connectedDevices[0].lastSeen)
    }

    @Test
    fun `pull with deviceId updates device lastSeen but pull without or by another user does not`() {
        val service = newSyncService()
        service.push(pushRequest("android-t4"), "alice")
        val before = service.status("alice").connectedDevices[0].lastSeen

        val pulled = service.pull(0L, "alice", "android-t4")
        assertTrue(pulled.serverTimestamp >= before)
        assertEquals(pulled.serverTimestamp, service.status("alice").connectedDevices[0].lastSeen)

        // Pull without a deviceId must not touch the device row.
        val afterPull = service.status("alice").connectedDevices[0].lastSeen
        service.pull(0L, "alice")
        assertEquals(afterPull, service.status("alice").connectedDevices[0].lastSeen)

        // Pull by another user must not touch alice's device row either.
        service.pull(0L, "bob", "android-t4")
        assertEquals(afterPull, service.status("alice").connectedDevices[0].lastSeen)
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /** A push request carrying at least one entity of each of the seven types. */
    private fun pushRequest(
        deviceId: String,
        timestamp: Long = System.currentTimeMillis(),
    ): SyncPushRequest = SyncPushRequest(
        entities = SyncEntityCollection(
            favorites = listOf(
                SyncFavoriteDto(gid = 1L, token = "fav-token", title = "Favorite One", favoriteSlot = 2, lastModified = 1000L),
            ),
            history = listOf(
                SyncHistoryDto(gid = 2L, token = "hist-token", title = "History Two", time = 2000L, lastModified = 2000L),
            ),
            downloads = listOf(
                SyncDownloadDto(gid = 3L, token = "dl-token", title = "Download Three", label = "DL-Label-A", total = 10, finished = 5, lastModified = 3000L),
            ),
            bookmarks = listOf(
                SyncBookmarkDto(gid = 4L, token = "bm-token", title = "Bookmark Four", page = 7, lastModified = 4000L),
            ),
            filters = listOf(
                SyncFilterDto(mode = 0, text = "filter-text", enabled = true, lastModified = 5000L),
            ),
            quickSearches = listOf(
                SyncQuickSearchDto(name = "quick-search-1", keyword = "sakura", lastModified = 6000L),
            ),
            downloadLabels = listOf(
                SyncDownloadLabelDto(label = "Label One", lastModified = 7000L),
            ),
        ),
        deviceId = deviceId,
        timestamp = timestamp,
    )

    private fun newSyncService(deviceRepo: SyncDeviceRepository = deviceRepo()): SyncService {
        val preferenceRepo = preferenceRepo()
        val service = SyncService(
            favoriteRepo(),
            historyRepo(),
            downloadRepo(),
            bookmarkRepo(),
            filterRepo(),
            quickSearchRepo(),
            downloadLabelRepo(),
            deviceRepo,
            preferenceRepo,
            UserPreferenceService(preferenceRepo),
            newServerConfigService(),
            mock(EhSessionRepository::class.java),
            mock(SiteSessionManager::class.java),
        )
        // v1 回环套件钉在策略 B（lww）= v1.0 完整语义；v2 策略行为见 SyncStrategyMatrixTest。
        return service.apply { updatePolicy(SyncPolicyDto(conflictStrategy = ConflictStrategy.LWW)) }
    }

    /** Real [ServerConfigService] over an in-memory repository fake (policy/provenance KV). */
    private fun newServerConfigService(): ServerConfigService {
        val repo = mock(ServerConfigRepository::class.java)
        val store = ConcurrentHashMap<String, ServerConfigEntity>()
        `when`(repo.findById(anyString())).thenAnswer { inv ->
            java.util.Optional.ofNullable(store[inv.getArgument<String>(0)])
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

    /** In-memory fake that behaves like the real repository (persists across calls). */
    private fun favoriteRepo(): LocalFavoriteInfoRepository {
        val repo = mock(LocalFavoriteInfoRepository::class.java)
        val store = ConcurrentHashMap<String, LocalFavoriteInfoEntity>()
        `when`(repo.save(any(LocalFavoriteInfoEntity::class.java))).thenAnswer { inv ->
            val entity = inv.getArgument<LocalFavoriteInfoEntity>(0)
            store[entity.gid.toString()] = entity
            entity
        }
        `when`(repo.findByGid(anyLong())).thenAnswer { inv -> store[inv.getArgument<Long>(0).toString()] }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        `when`(repo.findAllByUsernameIsNull()).thenAnswer { store.values.filter { it.username == null } }
        `when`(repo.countByUsername(anyString())).thenAnswer { inv ->
            store.values.count { it.username == inv.getArgument<String>(0) }.toLong()
        }
        // H-3: pull 现在走派生查询（全量/增量），stub 两个新方法。
        `when`(repo.findByUsername(anyString())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) }
        }
        `when`(repo.findByUsernameAndLastModifiedGreaterThan(anyString(), anyLong())).thenAnswer { inv ->
            val u = inv.getArgument<String>(0)
            val lm = inv.getArgument<Long>(1)
            store.values.filter { it.username == u && it.lastModified > lm }
        }
        return repo
    }

    private fun historyRepo(): HistoryInfoRepository {
        val repo = mock(HistoryInfoRepository::class.java)
        val store = ConcurrentHashMap<String, HistoryInfoEntity>()
        `when`(repo.save(any(HistoryInfoEntity::class.java))).thenAnswer { inv ->
            val entity = inv.getArgument<HistoryInfoEntity>(0)
            store[entity.gid.toString()] = entity
            entity
        }
        `when`(repo.findByGid(anyLong())).thenAnswer { inv -> store[inv.getArgument<Long>(0).toString()] }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        `when`(repo.findAllByUsernameIsNull()).thenAnswer { store.values.filter { it.username == null } }
        doAnswer { inv -> store.values.remove(inv.getArgument(0)) }.`when`(repo).delete(any(HistoryInfoEntity::class.java))
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
        return repo
    }

    private fun downloadRepo(): DownloadInfoRepository {
        val repo = mock(DownloadInfoRepository::class.java)
        val store = ConcurrentHashMap<String, DownloadInfoEntity>()
        `when`(repo.save(any(DownloadInfoEntity::class.java))).thenAnswer { inv ->
            val entity = inv.getArgument<DownloadInfoEntity>(0)
            store[entity.gid.toString()] = entity
            entity
        }
        `when`(repo.findByGid(anyLong())).thenAnswer { inv -> store[inv.getArgument<Long>(0).toString()] }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
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
        return repo
    }

    private fun bookmarkRepo(): BookmarkInfoRepository {
        val repo = mock(BookmarkInfoRepository::class.java)
        val store = ConcurrentHashMap<String, BookmarkInfoEntity>()
        `when`(repo.save(any(BookmarkInfoEntity::class.java))).thenAnswer { inv ->
            val entity = inv.getArgument<BookmarkInfoEntity>(0)
            store[entity.gid.toString()] = entity
            entity
        }
        `when`(repo.findByGid(anyLong())).thenAnswer { inv -> store[inv.getArgument<Long>(0).toString()] }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        `when`(repo.findAllByUsernameIsNull()).thenAnswer { store.values.filter { it.username == null } }
        doAnswer { inv -> store.values.remove(inv.getArgument(0)) }.`when`(repo).delete(any(BookmarkInfoEntity::class.java))
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
        return repo
    }

    private fun filterRepo(): FilterRepository {
        val repo = mock(FilterRepository::class.java)
        val store = ConcurrentHashMap<String, FilterEntity>()
        `when`(repo.save(any(FilterEntity::class.java))).thenAnswer { inv ->
            val entity = inv.getArgument<FilterEntity>(0)
            store[entity.text] = entity
            entity
        }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        `when`(repo.findByTypeAndText(anyInt(), anyString())).thenAnswer { inv ->
            store.values.firstOrNull { it.type == inv.getArgument<Int>(0) && it.text == inv.getArgument<String>(1) }
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
        return repo
    }

    private fun quickSearchRepo(): QuickSearchRepository {
        val repo = mock(QuickSearchRepository::class.java)
        val store = ConcurrentHashMap<String, QuickSearchEntity>()
        `when`(repo.save(any(QuickSearchEntity::class.java))).thenAnswer { inv ->
            val entity = inv.getArgument<QuickSearchEntity>(0)
            store[entity.name] = entity
            entity
        }
        `when`(repo.findByName(anyString())).thenAnswer { inv -> store[inv.getArgument(0)] }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
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
        return repo
    }

    private fun downloadLabelRepo(): DownloadLabelRepository {
        val repo = mock(DownloadLabelRepository::class.java)
        val store = ConcurrentHashMap<String, DownloadLabelEntity>()
        `when`(repo.save(any(DownloadLabelEntity::class.java))).thenAnswer { inv ->
            val entity = inv.getArgument<DownloadLabelEntity>(0)
            store[entity.label] = entity
            entity
        }
        `when`(repo.findByLabel(anyString())).thenAnswer { inv -> store[inv.getArgument(0)] }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
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
        return repo
    }

    private fun deviceRepo(): SyncDeviceRepository {
        val repo = mock(SyncDeviceRepository::class.java)
        val store = ConcurrentHashMap<String, SyncDeviceEntity>()
        `when`(repo.save(any(SyncDeviceEntity::class.java))).thenAnswer { inv ->
            val entity = inv.getArgument<SyncDeviceEntity>(0)
            store[entity.deviceId] = entity
            entity
        }
        `when`(repo.findByDeviceId(anyString())).thenAnswer { inv -> store[inv.getArgument(0)] }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        return repo
    }

    private fun preferenceRepo(): UserPreferenceRepository {
        val repo = mock(UserPreferenceRepository::class.java)
        val store = ConcurrentHashMap<String, UserPreferenceEntity>()
        `when`(repo.save(any(UserPreferenceEntity::class.java))).thenAnswer { inv ->
            val entity = inv.getArgument<UserPreferenceEntity>(0)
            store[entity.username] = entity
            entity
        }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv -> store[inv.getArgument(0)] }
        return repo
    }
}
