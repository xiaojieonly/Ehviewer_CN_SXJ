package com.hippo.anotherviewer.web.config

import com.hippo.anotherviewer.web.repository.AuthConfigRepository
import com.hippo.anotherviewer.web.repository.SyncDeviceRepository
import com.hippo.anotherviewer.web.repository.TokenRepository
import com.hippo.anotherviewer.web.service.EncryptionService
import com.hippo.anotherviewer.web.service.ServerConfigService
import com.hippo.anotherviewer.web.service.SettingsService
import com.hippo.anotherviewer.web.service.SiteAuthService
import com.hippo.anotherviewer.web.service.SiteSessionManager
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import org.springframework.security.core.context.SecurityContextHolder

/**
 * N-4: with no DB row and no env (fresh deployment), every require_auth read
 * site must default to false — the filter/interceptor actually let requests
 * through, so the reported /auth/status and /settings values must match.
 */
class AuthDefaultsTest {

    @AfterEach
    fun tearDown() {
        WebSocketConfig.activeConnections.set(0)
        SecurityContextHolder.clearContext()
    }

    private fun frame(command: StompCommand): org.springframework.messaging.Message<*> {
        val accessor = StompHeaderAccessor.create(command)
        return MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
    }

    /** Mock ServerConfigService whose getBoolean records the default argument per key. */
    private fun configRecordingDefaults(): Pair<ServerConfigService, MutableMap<String, Boolean>> {
        val serverConfig = mock(ServerConfigService::class.java)
        val defaults = mutableMapOf<String, Boolean>()
        `when`(serverConfig.getBoolean(anyString(), anyBoolean())).thenAnswer { inv ->
            defaults[inv.getArgument<String>(0)] = inv.getArgument<Boolean>(1)
            false
        }
        return serverConfig to defaults
    }

    @Test
    fun `AuthTokenFilter passes requests through anonymously with default require_auth=false`() {
        val serverConfig = mock(ServerConfigService::class.java)
        val filter = AuthTokenFilter(mock(SiteAuthService::class.java), serverConfig)
        val request = mock(HttpServletRequest::class.java)
        val response = mock(HttpServletResponse::class.java)
        val chain = mock(FilterChain::class.java)

        filter.doFilter(request, response, chain)

        verify(serverConfig).getBoolean(ServerConfigService.KEY_REQUIRE_AUTH, false)
        verify(chain).doFilter(request, response)
        assertEquals("default", SecurityContextHolder.getContext().authentication?.name)
    }

    @Test
    fun `WsAuthChannelInterceptor accepts CONNECT without a token with default require_auth=false`() {
        val serverConfig = mock(ServerConfigService::class.java)
        val interceptor = WsAuthChannelInterceptor(mock(SiteAuthService::class.java), serverConfig)
        val channel = mock(MessageChannel::class.java)

        val result = interceptor.preSend(frame(StompCommand.CONNECT), channel)

        verify(serverConfig).getBoolean(ServerConfigService.KEY_REQUIRE_AUTH, false)
        assertNotNull(result)
        assertEquals(1, WebSocketConfig.activeConnections.get())
    }

    @Test
    fun `SettingsService reports requireAuth=false with default require_auth=false`() {
        val (serverConfig, defaults) = configRecordingDefaults()
        `when`(serverConfig.get(anyString(), anyString())).thenReturn("")
        val service = SettingsService(SiteCoreConfigProperties(), serverConfig)

        val settings = service.getSettings()

        assertFalse(settings.security.requireAuth)
        assertEquals(false, defaults[ServerConfigService.KEY_REQUIRE_AUTH])
    }

    @Test
    fun `getStatus reports authRequired=false with default require_auth=false`() {
        val sessionManager = mock(SiteSessionManager::class.java)
        `when`(sessionManager.getStatus()).thenReturn(
            SiteSessionManager.SessionStatus(SiteSessionManager.SessionState.SIGNED_OUT, false, false, 0L)
        )
        val (serverConfig, defaults) = configRecordingDefaults()
        val service = SiteAuthService(
            mock(AuthConfigRepository::class.java),
            mock(EncryptionService::class.java),
            sessionManager,
            serverConfig,
            mock(TokenRepository::class.java),
            mock(SyncDeviceRepository::class.java),
            SiteCoreConfigProperties(),
        )

        val status = service.getStatus(null)

        assertFalse(status.authRequired)
        assertEquals(false, defaults[ServerConfigService.KEY_REQUIRE_AUTH])
    }
}
