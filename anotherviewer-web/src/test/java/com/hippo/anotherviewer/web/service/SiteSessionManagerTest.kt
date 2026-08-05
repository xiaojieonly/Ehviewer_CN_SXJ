package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.Settings
import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.entity.EhSessionEntity
import com.hippo.anotherviewer.web.repository.EhSessionRepository
import okhttp3.Cookie
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class SiteSessionManagerTest {

    private lateinit var serverConfig: ServerConfigService
    private lateinit var ehSessionRepo: EhSessionRepository
    private lateinit var manager: SiteSessionManager
    private var savedGallerySite: Int = 0
    private var keyPath: String = ""

    /** In-memory EhSessionRepository fake: real persistence round-trip (encrypt/decrypt) without a DB. */
    private fun fakeEhSessionRepo(): EhSessionRepository {
        val store = java.util.concurrent.ConcurrentHashMap<String, EhSessionEntity>()
        val repo = mock(EhSessionRepository::class.java)
        org.mockito.Mockito.`when`(repo.findByUsername(anyString())).thenAnswer { inv ->
            store[inv.getArgument<String>(0)]
        }
        org.mockito.Mockito.`when`(repo.save(any(EhSessionEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<EhSessionEntity>(0)
            store[e.username] = e
            e
        }
        org.mockito.Mockito.`when`(repo.findByUsernameAndLastModifiedGreaterThan(anyString(), anyLong())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.lastModified > inv.getArgument<Long>(1) }
        }
        return repo
    }

    @BeforeEach
    fun setUp() {
        // Settings is a process-wide static: pin a deterministic baseline and
        // restore the prior value afterwards so tests stay hermetic.
        savedGallerySite = Settings.getGallerySite()
        Settings.setGallerySite(Settings.SITE_E)
        serverConfig = mock(ServerConfigService::class.java)
        `when`(serverConfig.getBoolean(anyString(), anyBoolean())).thenReturn(false)
        `when`(serverConfig.get(anyString(), anyString())).thenReturn("")
        ehSessionRepo = fakeEhSessionRepo()
        keyPath = "${java.nio.file.Files.createTempDirectory("ehsession-key").toAbsolutePath()}/security.key"
        val config = SiteCoreConfigProperties()
        config.security.encryptionKeyPath = keyPath
        manager = SiteSessionManager(
            WebProxyManager(serverConfig),
            serverConfig,
            ehSessionRepo,
            EncryptionService(),
            config,
        )
    }

    @AfterEach
    fun tearDown() {
        Settings.setGallerySite(savedGallerySite)
    }

    private fun identityCookie(name: String, expiresAt: Long): Cookie =
        Cookie.Builder()
            .name(name)
            .value("test-value")
            .domain("e-hentai.org")
            .path("/")
            .expiresAt(expiresAt)
            .build()

    private fun addValidSession() {
        val future = System.currentTimeMillis() + 3_600_000L
        manager.cookieStore.addCookie(identityCookie(SiteSessionManager.KEY_IPB_MEMBER_ID, future))
        manager.cookieStore.addCookie(identityCookie(SiteSessionManager.KEY_IPB_PASS_HASH, future))
    }

    private fun addExpiredSession() {
        val past = 1_000L
        manager.cookieStore.addCookie(identityCookie(SiteSessionManager.KEY_IPB_MEMBER_ID, past))
        manager.cookieStore.addCookie(identityCookie(SiteSessionManager.KEY_IPB_PASS_HASH, past))
    }

    @Test
    fun `initial state is signed out and not expired`() {
        assertFalse(manager.hasSignedIn())
        assertFalse(manager.isExpired())
        val status = manager.getStatus()
        assertEquals(SiteSessionManager.SessionState.SIGNED_OUT, status.state)
        assertFalse(status.signedIn)
        assertFalse(status.expired)
    }

    @Test
    fun `web client shares the same cookie store as core`() {
        assertSame(manager.cookieStore, manager.okHttpClient.cookieJar)
    }

    @Test
    fun `valid identity cookies produce a valid session`() {
        addValidSession()
        assertTrue(manager.hasSignedIn())
        assertFalse(manager.isExpired())
        val status = manager.getStatus()
        assertEquals(SiteSessionManager.SessionState.VALID, status.state)
        assertTrue(status.signedIn)
        assertFalse(status.expired)
    }

    @Test
    fun `a single identity cookie is not a signed-in session`() {
        val future = System.currentTimeMillis() + 3_600_000L
        manager.cookieStore.addCookie(identityCookie(SiteSessionManager.KEY_IPB_MEMBER_ID, future))
        assertFalse(manager.hasSignedIn())
        assertEquals(SiteSessionManager.SessionState.SIGNED_OUT, manager.getStatus().state)
    }

    @Test
    fun `past-expiry identity cookies are detected as expired`() {
        addExpiredSession()
        assertTrue(manager.hasSignedIn())
        assertTrue(manager.isExpired())
        val status = manager.getStatus()
        assertEquals(SiteSessionManager.SessionState.EXPIRED, status.state)
        assertTrue(status.expired)
    }

    @Test
    fun `requireValidSession throws only when expired`() {
        // Signed out: no throw.
        assertDoesNotThrow { manager.requireValidSession() }

        // Valid: no throw.
        addValidSession()
        assertDoesNotThrow { manager.requireValidSession() }

        // Expired: throws.
        manager.signOut()
        addExpiredSession()
        assertThrows(SiteSessionExpiredException::class.java) { manager.requireValidSession() }
    }

    @Test
    fun `signOut clears the shared cookie store`() {
        addValidSession()
        assertTrue(manager.hasSignedIn())
        manager.signOut()
        assertFalse(manager.hasSignedIn())
        assertTrue(manager.cookieStore.getAll().isEmpty())
        assertEquals(SiteSessionManager.SessionState.SIGNED_OUT, manager.getStatus().state)
    }

    @Test
    fun `healthCheck runs without error in every state`() {
        assertDoesNotThrow { manager.healthCheck() }
        addValidSession()
        assertDoesNotThrow { manager.healthCheck() }
        manager.signOut()
        addExpiredSession()
        assertDoesNotThrow { manager.healthCheck() }
    }

    @Test
    fun `setGallerySite persists the choice and applies it to core Settings`() {
        assertTrue(manager.setGallerySite(Settings.SITE_EX))
        verify(serverConfig).set(ServerConfigService.KEY_SITE_GALLERY, "1")
        assertEquals(Settings.SITE_EX, Settings.getGallerySite())
        assertEquals(Settings.SITE_EX, manager.getGallerySite())
    }

    @Test
    fun `setGallerySite back to e-hentai persists 0`() {
        assertTrue(manager.setGallerySite(Settings.SITE_EX))
        assertTrue(manager.setGallerySite(Settings.SITE_E))
        verify(serverConfig).set(ServerConfigService.KEY_SITE_GALLERY, "0")
        assertEquals(Settings.SITE_E, Settings.getGallerySite())
    }

    @Test
    fun `setGallerySite rejects invalid values without persisting or applying`() {
        assertFalse(manager.setGallerySite(2))
        assertFalse(manager.setGallerySite(Settings.SITE_EP))
        verify(serverConfig, never()).set(anyString(), anyString())
        assertEquals(Settings.SITE_E, Settings.getGallerySite())
    }

    @Test
    fun `loadGallerySite restores a persisted exhentai selection`() {
        `when`(serverConfig.get(ServerConfigService.KEY_SITE_GALLERY, "0")).thenReturn("1")
        manager.loadGallerySite()
        assertEquals(Settings.SITE_EX, Settings.getGallerySite())
    }

    @Test
    fun `loadGallerySite falls back to e-hentai for unparseable values`() {
        `when`(serverConfig.get(ServerConfigService.KEY_SITE_GALLERY, "0")).thenReturn("bogus")
        manager.loadGallerySite()
        assertEquals(Settings.SITE_E, Settings.getGallerySite())
    }

    // ==================== ehSession 持久化桥（ADR-0004）====================

    private fun syncSession(
        lastModified: Long,
        deleted: Boolean = false,
        gallerySite: Int? = null,
        cookies: List<com.hippo.anotherviewer.web.dto.SyncEhSessionCookieDto> = emptyList(),
    ) = com.hippo.anotherviewer.web.dto.SyncEhSessionDto(
        cookies = cookies,
        displayName = "tester",
        gallerySite = gallerySite,
        lastModified = lastModified,
        deviceId = "android-test",
        deleted = deleted,
    )

    private fun ipbCookie(name: String = SiteSessionManager.KEY_IPB_MEMBER_ID) = com.hippo.anotherviewer.web.dto.SyncEhSessionCookieDto(
        name = name,
        value = "111",
        domain = "e-hentai.org",
        path = "/",
        expiresAt = 0,
        persistent = true,
        httpOnly = true,
        hostOnly = false,
        secure = false,
    )

    /** 两个 identity cookie 才构成 hasSignedIn() 的真会话。 */
    private fun identityPair() = listOf(
        ipbCookie(SiteSessionManager.KEY_IPB_MEMBER_ID),
        ipbCookie(SiteSessionManager.KEY_IPB_PASS_HASH),
    )

    @Test
    fun `applySyncEhSession stores a new session and materializes its cookies`() {
        val applied = manager.applySyncEhSession(
            syncSession(lastModified = 1_000, gallerySite = Settings.SITE_EX, cookies = identityPair()),
            "android-test",
        )

        assertTrue(manager.hasSignedIn())
        val row = ehSessionRepo.findByUsername(SiteSessionManager.EH_SESSION_OWNER)!!
        assertEquals(1_000L, row.lastModified)
        assertEquals("tester", row.displayName)
        // gallerySite 必须同时落 entity（pull 回显）与 Settings（站点生效）。
        assertEquals(Settings.SITE_EX, row.gallerySite)
        assertEquals(Settings.SITE_EX, manager.getGallerySite())
        assertEquals(Settings.SITE_EX, manager.loadSyncEhSession()!!.gallerySite)
        // 无存量行 → 不冲突。
        assertFalse(applied)
    }

    @Test
    fun `applySyncEhSession rejects a stale session older than skew`() {
        manager.applySyncEhSession(syncSession(lastModified = 10_000, cookies = identityPair()), "android-test")

        val stale = syncSession(lastModified = 1_000, cookies = identityPair())
        val applied = manager.applySyncEhSession(stale, "android-other")

        assertFalse(applied)
        assertEquals(10_000L, ehSessionRepo.findByUsername(SiteSessionManager.EH_SESSION_OWNER)!!.lastModified)
        assertTrue(manager.hasSignedIn())
    }

    @Test
    fun `applySyncEhSession tombstone clears cookies and propagates the logout`() {
        manager.applySyncEhSession(syncSession(lastModified = 10_000, cookies = identityPair()), "android-test")
        assertTrue(manager.hasSignedIn())

        val tombstone = syncSession(lastModified = 20_000, deleted = true)
        manager.applySyncEhSession(tombstone, "android-other")

        assertFalse(manager.hasSignedIn())
        val row = ehSessionRepo.findByUsername(SiteSessionManager.EH_SESSION_OWNER)!!
        assertTrue(row.deleted)
        assertEquals(20_000L, row.lastModified)
        // tombstone 落库但无存量冲突计数。
    }

    @Test
    fun `signOut writes a tombstone when a session existed`() {
        manager.applySyncEhSession(syncSession(lastModified = 10_000, cookies = identityPair()), "android-test")

        manager.signOut()

        val row = ehSessionRepo.findByUsername(SiteSessionManager.EH_SESSION_OWNER)!!
        assertTrue(row.deleted)
        assertFalse(manager.hasSignedIn())
    }

    @Test
    fun `persisted session survives a restart via restorePersistedSession`() {
        manager.applySyncEhSession(syncSession(lastModified = 10_000, cookies = identityPair()), "android-test")

        // 模拟重启：新建 manager（同一 fake repo / 密钥），恢复持久化会话。
        val config = SiteCoreConfigProperties()
        config.security.encryptionKeyPath = keyPath
        val restarted = SiteSessionManager(
            WebProxyManager(serverConfig),
            serverConfig,
            ehSessionRepo,
            EncryptionService(),
            config,
        )
        restarted.restorePersistedSession()

        assertTrue(restarted.hasSignedIn())
    }
}
