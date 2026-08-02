package com.hippo.anotherviewer.web.service

import okhttp3.Cookie
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class SiteSessionManagerTest {

    private lateinit var manager: SiteSessionManager

    @BeforeEach
    fun setUp() {
        val serverConfig = mock(ServerConfigService::class.java)
        `when`(serverConfig.getBoolean(anyString(), anyBoolean())).thenReturn(false)
        `when`(serverConfig.get(anyString(), anyString())).thenReturn("")
        manager = SiteSessionManager(WebProxyManager(serverConfig))
    }

    private fun identityCookie(name: String, expiresAt: Long): Cookie =
        Cookie.Builder()
            .name(name)
            .value("test-value")
            .domain("gallery.test")
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
        assertSame(manager.cookieStore, manager.okHttpClient.cookieJar())
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
}
