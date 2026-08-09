package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.PairCompleteRequest
import com.hippo.anotherviewer.web.dto.RegisterDeviceRequest
import com.hippo.anotherviewer.web.entity.SyncDeviceEntity
import com.hippo.anotherviewer.web.entity.TokenEntity
import com.hippo.anotherviewer.web.repository.AuthConfigRepository
import com.hippo.anotherviewer.web.repository.SyncDeviceRepository
import com.hippo.anotherviewer.web.repository.TokenRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.hippo.anotherviewer.web.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import java.util.concurrent.ConcurrentHashMap

/**
 * Regression guard for the device pairing flow:
 *
 * 1. Generating a pairing code returns a short alphanumeric code.
 * 2. Completing pairing with a valid code registers the device and returns a
 *    token that authenticates as the code's owner.
 * 3. The pairing code is single-use: a second attempt fails.
 * 4. Revoking a device invalidates its token.
 * 5. Auto-pairing (register-device) registers a device without a code, honors
 *    the auto_pairing toggle and the optional setup key, and stays idempotent
 *    per deviceId.
 */
class PairingFlowTest {

    private fun newAuthService(
        deviceRepo: SyncDeviceRepository = mockDeviceRepo(),
        requireAuth: Boolean = false,
        autoPairing: Boolean = true,
        setupKey: String = "",
    ): SiteAuthService {
        val authRepo = mock(AuthConfigRepository::class.java)
        `when`(authRepo.existsByUsername(anyString())).thenReturn(false)
        `when`(authRepo.findByUsername(anyString())).thenReturn(null)
        val sessionManager = mock(SiteSessionManager::class.java)
        `when`(sessionManager.getStatus()).thenReturn(
            SiteSessionManager.SessionStatus(SiteSessionManager.SessionState.SIGNED_OUT, false, false, 0L)
        )
        val serverConfig = mock(ServerConfigService::class.java)
        `when`(serverConfig.getBoolean(anyString(), anyBoolean())).thenAnswer { inv ->
            when (inv.getArgument<String>(0)) {
                ServerConfigService.KEY_REQUIRE_AUTH -> requireAuth
                ServerConfigService.KEY_AUTO_PAIRING -> autoPairing
                else -> false
            }
        }
        `when`(serverConfig.get(anyString(), anyString())).thenAnswer { inv ->
            if (inv.getArgument<String>(0) == ServerConfigService.KEY_SETUP_KEY) setupKey else ""
        }
        `when`(serverConfig.getLong(anyString(), anyLong())).thenReturn(86400L)
        return SiteAuthService(
            authRepo,
            EncryptionService(),
            sessionManager,
            serverConfig,
            mockTokenRepo(),
            deviceRepo,
            SiteCoreConfigProperties(),
        )
    }

    /** In-memory fake that behaves like the real repository (persists across calls). */
    private fun mockDeviceRepo(): SyncDeviceRepository {
        val repo = mock(SyncDeviceRepository::class.java)
        val store = ConcurrentHashMap<String, SyncDeviceEntity>()
        `when`(repo.save(any(SyncDeviceEntity::class.java))).thenAnswer { inv ->
            val entity = inv.getArgument<SyncDeviceEntity>(0)
            store[entity.deviceId] = entity
            entity
        }
        `when`(repo.findByDeviceId(anyString())).thenAnswer { inv -> store[inv.getArgument(0)] }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        doAnswer { inv -> store.values.remove(inv.getArgument(0)) }.`when`(repo).delete(any(SyncDeviceEntity::class.java))
        return repo
    }

    /** In-memory fake token store, keyed by token hash. */
    private fun mockTokenRepo(): TokenRepository {
        val repo = mock(TokenRepository::class.java)
        val store = ConcurrentHashMap<String, TokenEntity>()
        `when`(repo.save(any(TokenEntity::class.java))).thenAnswer { inv ->
            val entity = inv.getArgument<TokenEntity>(0)
            store[entity.tokenHash] = entity
            entity
        }
        `when`(repo.findByTokenHash(anyString())).thenAnswer { inv -> store[inv.getArgument(0)] }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        doAnswer { inv -> store.remove(inv.getArgument<String>(0)) }.`when`(repo).deleteByTokenHash(anyString())
        doAnswer { inv -> store.values.remove(inv.getArgument(0)) }.`when`(repo).delete(any(TokenEntity::class.java))
        return repo
    }

    @Test
    fun `registration follows the auth mode`() {
        assertFalse(newAuthService().isRegistrationAllowed())
        assertTrue(newAuthService(requireAuth = true).isRegistrationAllowed())
    }

    @Test
    fun `pairing code completes into a device token and is single-use`() {
        val deviceRepo = mockDeviceRepo()
        val authService = newAuthService(deviceRepo)

        val codeResponse = authService.generatePairCode("default")
        assertTrue(codeResponse.code.length == 6)
        assertTrue(codeResponse.code.all { it.isLetterOrDigit() })
        assertTrue(codeResponse.expiresAt > System.currentTimeMillis())

        val completed = authService.completePairing(
            PairCompleteRequest(
                code = codeResponse.code,
                deviceId = "android-test",
                deviceName = "Bob's Phone",
                platform = "android",
            )
        )
        assertTrue(completed.success)
        assertTrue(completed.token.length >= 32)
        assertEquals("default", completed.username)
        // The device token authenticates as the pairing user.
        assertEquals("default", authService.validateToken(completed.token))
        // The device row stores only the token hash, never the raw token.
        val storedHash = deviceRepo.findByDeviceId("android-test")!!.token
        assertTrue(storedHash != null && storedHash != completed.token)

        // Single-use: the same code must not complete again.
        val second = authService.completePairing(
            PairCompleteRequest(
                code = codeResponse.code,
                deviceId = "android-test",
                deviceName = "Bob's Phone",
                platform = "android",
            )
        )
        assertFalse(second.success)
    }

    @Test
    fun `invalid or expired code is rejected`() {
        val authService = newAuthService()
        val result = authService.completePairing(
            PairCompleteRequest(
                code = "ZZZZZZ",
                deviceId = "android-other",
                deviceName = "Other",
                platform = "android",
            )
        )
        assertFalse(result.success)
        assertTrue(result.message.contains("invalid or expired"))
    }

    @Test
    fun `expired pairing codes are pruned from the map`() {
        val authService = newAuthService()
        // TTL that has already elapsed at generation time (prune takes the
        // current time as an injectable parameter).
        val expired = authService.generatePairCode("default", ttlSeconds = -1)
        assertEquals(1, authService.activePairCodeCount())

        authService.pruneExpiredPairCodes()
        assertEquals(0, authService.activePairCodeCount())

        // A fresh code survives pruning and completes; the expired one is gone.
        val live = authService.generatePairCode("default")
        assertEquals(1, authService.activePairCodeCount())
        assertTrue(
            authService.completePairing(
                PairCompleteRequest(live.code, "android-prune", "Pruned", "android")
            ).success
        )
        assertFalse(
            authService.completePairing(
                PairCompleteRequest(expired.code, "android-prune", "Pruned", "android")
            ).success
        )
    }

    @Test
    fun `generating a new code prunes expired ones first`() {
        val authService = newAuthService()
        authService.generatePairCode("default", ttlSeconds = -1)
        // The next generation must have swept the expired code out.
        authService.generatePairCode("default")
        assertEquals(1, authService.activePairCodeCount())
    }

    @Test
    fun `revoking a device invalidates its token`() {
        val deviceRepo = mockDeviceRepo()
        val authService = newAuthService(deviceRepo)

        val code = authService.generatePairCode("default")
        val completed = authService.completePairing(
            PairCompleteRequest(code.code, "android-revoke", "Revoked Device", "android")
        )
        assertTrue(completed.success)
        assertEquals("default", authService.validateToken(completed.token))

        // Revocation removes the device and its token stops resolving.
        authService.revokeDevice("android-revoke")
        assertNull(authService.validateToken(completed.token))
        assertNull(deviceRepo.findByDeviceId("android-revoke"))
    }

    @Test
    fun `revoking an unpaired device still removes it`() {
        val deviceRepo = mockDeviceRepo()
        val authService = newAuthService(deviceRepo)
        // Device auto-created by a push (no token) — revoke must still delete it.
        deviceRepo.save(SyncDeviceEntity().apply { deviceId = "android-nopair" })

        authService.revokeDevice("android-nopair")
        assertNull(deviceRepo.findByDeviceId("android-nopair"))
    }

    @Test
    fun `revoking another user's device is refused`() {
        val deviceRepo = mockDeviceRepo()
        val authService = newAuthService(deviceRepo)
        deviceRepo.save(SyncDeviceEntity().apply { deviceId = "android-other"; username = "alice" })

        assertFalse(authService.revokeDevice("android-other", "bob"))
        assertTrue(deviceRepo.findByDeviceId("android-other") != null)
    }

    @Test
    fun `device list reflects registered devices`() {
        val deviceRepo = mockDeviceRepo()
        val authService = newAuthService(deviceRepo)

        val code = authService.generatePairCode("default")
        authService.completePairing(
            PairCompleteRequest(code.code, "android-one", "Phone", "android")
        )

        val devices = DeviceService(deviceRepo).list()
        assertEquals(1, devices.size)
        assertEquals("android-one", devices[0].deviceId)
        assertEquals("Phone", devices[0].deviceName)
        assertTrue(devices[0].pairedAt > 0)
    }

    // ---------------------------------------------------------------------
    // Auto-pairing (register-device)
    // ---------------------------------------------------------------------

    private fun registerRequest(
        deviceId: String = "android-auto",
        deviceName: String = "Auto Phone",
        platform: String = "android",
        setupKey: String? = null,
    ) = RegisterDeviceRequest(deviceId, deviceName, platform, setupKey)

    @Test
    fun `auto-pairing is on by default and registers a device under the anonymous user`() {
        val deviceRepo = mockDeviceRepo()
        val authService = newAuthService(deviceRepo)

        assertTrue(authService.isAutoPairingEnabled())
        val registered = authService.registerDevice("default", registerRequest())
        assertTrue(registered.success)
        assertEquals("default", registered.username)
        assertTrue(registered.token.length >= 32)
        // The token authenticates as the anonymous single user.
        assertEquals("default", authService.validateToken(registered.token))
        // The device row stores only the token hash, never the raw token.
        val stored = deviceRepo.findByDeviceId("android-auto")!!
        assertEquals("android-auto", stored.deviceId)
        assertTrue(stored.token != null && stored.token != registered.token)
        assertEquals(SiteAuthService.sha256(registered.token), stored.token)
    }

    @Test
    fun `auto-pairing disabled rejects registration`() {
        val authService = newAuthService(autoPairing = false)
        val result = authService.registerDevice("default", registerRequest())
        assertFalse(result.success)
        assertTrue(result.message.contains("disabled"))
    }

    @Test
    fun `setup key is required when configured`() {
        val authService = newAuthService(setupKey = "secret")
        val result = authService.registerDevice("default", registerRequest(setupKey = null))
        assertFalse(result.success)
        assertEquals(SiteAuthService.MSG_SETUP_KEY_REQUIRED, result.message)
    }

    @Test
    fun `wrong setup key is rejected`() {
        val authService = newAuthService(setupKey = "secret")
        val result = authService.registerDevice("default", registerRequest(setupKey = "wrong"))
        assertFalse(result.success)
        assertTrue(result.message.contains("Invalid setup key"))
    }

    @Test
    fun `correct setup key registers the device`() {
        val authService = newAuthService(setupKey = "secret")
        val result = authService.registerDevice("default", registerRequest(setupKey = "secret"))
        assertTrue(result.success)
        assertEquals("default", authService.validateToken(result.token))
    }

    @Test
    fun `re-registering the same device refreshes its token idempotently`() {
        val deviceRepo = mockDeviceRepo()
        val authService = newAuthService(deviceRepo)

        val first = authService.registerDevice("default", registerRequest())
        assertTrue(first.success)
        val second = authService.registerDevice("default", registerRequest())
        assertTrue(second.success)

        // A fresh token replaces the old one; the old token stops working.
        assertNotEquals(first.token, second.token)
        assertNull(authService.validateToken(first.token))
        assertEquals("default", authService.validateToken(second.token))
        // The device row is upserted in place: still exactly one row.
        val rows = deviceRepo.findAll()
        assertEquals(1, rows.size)
        assertEquals("android-auto", rows[0].deviceId)
        assertEquals(SiteAuthService.sha256(second.token), rows[0].token)
    }

    @Test
    fun `auto-registered device lists and revokes with its token invalidated`() {
        val deviceRepo = mockDeviceRepo()
        val authService = newAuthService(deviceRepo)

        val registered = authService.registerDevice("default", registerRequest())
        assertTrue(registered.success)

        val devices = DeviceService(deviceRepo).list()
        assertEquals(1, devices.size)
        assertEquals("android-auto", devices[0].deviceId)
        assertEquals("Auto Phone", devices[0].deviceName)

        authService.revokeDevice("android-auto")
        assertNull(authService.validateToken(registered.token))
        assertNull(deviceRepo.findByDeviceId("android-auto"))
    }
}
