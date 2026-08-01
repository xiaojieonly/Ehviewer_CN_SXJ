package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.web.dto.PairCompleteRequest
import com.hippo.ehviewer.web.entity.SyncDeviceEntity
import com.hippo.ehviewer.web.repository.AuthConfigRepository
import com.hippo.ehviewer.web.repository.SyncDeviceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
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
 */
class PairingFlowTest {

    private fun newAuthService(deviceRepo: SyncDeviceRepository = mockDeviceRepo()): EhAuthService {
        val authRepo = mock(AuthConfigRepository::class.java)
        `when`(authRepo.existsByUsername(anyString())).thenReturn(false)
        `when`(authRepo.findByUsername(anyString())).thenReturn(null)
        val sessionManager = mock(EhSessionManager::class.java)
        `when`(sessionManager.getStatus()).thenReturn(
            EhSessionManager.SessionStatus(EhSessionManager.SessionState.SIGNED_OUT, false, false, 0L)
        )
        val serverConfig = mock(ServerConfigService::class.java)
        `when`(serverConfig.getBoolean(anyString(), anyBoolean())).thenReturn(false)
        val deviceService = DeviceService(deviceRepo)
        return EhAuthService(authRepo, EncryptionService(), sessionManager, serverConfig, deviceService)
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
}
