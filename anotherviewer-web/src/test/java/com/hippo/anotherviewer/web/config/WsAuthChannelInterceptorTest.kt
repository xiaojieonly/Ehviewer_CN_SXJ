package com.hippo.anotherviewer.web.config

import com.hippo.anotherviewer.web.service.SiteAuthService
import com.hippo.anotherviewer.web.service.ServerConfigService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder

class WsAuthChannelInterceptorTest {

    private lateinit var authService: SiteAuthService
    private lateinit var serverConfig: ServerConfigService
    private lateinit var interceptor: WsAuthChannelInterceptor
    private lateinit var channel: MessageChannel

    @BeforeEach
    fun setUp() {
        authService = mock(SiteAuthService::class.java)
        serverConfig = mock(ServerConfigService::class.java)
        channel = mock(MessageChannel::class.java)
        interceptor = WsAuthChannelInterceptor(authService, serverConfig)
    }

    @AfterEach
    fun tearDown() {
        WebSocketConfig.activeConnections.set(0)
    }

    private fun frame(command: StompCommand, nativeHeader: Pair<String, String>? = null): Message<*> {
        val accessor = StompHeaderAccessor.create(command)
        if (nativeHeader != null) {
            accessor.setNativeHeader(nativeHeader.first, nativeHeader.second)
        }
        return MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
    }

    @Test
    // MASTER-2026-08-22 S4：计数迁至 session 生命周期事件——preSend 不再增减，
    // 计数由 onSessionConnected/onSessionDisconnected 负责（见 SessionCountTest）。
    fun `accepted CONNECT does not count by itself anymore`() {
        `when`(serverConfig.getBoolean(ServerConfigService.KEY_REQUIRE_AUTH, false)).thenReturn(true)
        `when`(authService.validateToken("valid-token")).thenReturn("user")

        val result = interceptor.preSend(frame(StompCommand.CONNECT, "login" to "valid-token"), channel)

        assertNotNull(result)
        assertEquals(0, WebSocketConfig.activeConnections.get())
    }

    @Test
    fun `rejected CONNECT throws and does not increment active connections`() {
        `when`(serverConfig.getBoolean(ServerConfigService.KEY_REQUIRE_AUTH, false)).thenReturn(true)
        `when`(authService.validateToken("bad-token")).thenReturn(null)

        assertThrows(IllegalArgumentException::class.java) {
            interceptor.preSend(frame(StompCommand.CONNECT, "login" to "bad-token"), channel)
        }
        assertEquals(0, WebSocketConfig.activeConnections.get())
    }

    @Test
    fun `CONNECT with auth disabled does not count by itself anymore`() {
        `when`(serverConfig.getBoolean(ServerConfigService.KEY_REQUIRE_AUTH, false)).thenReturn(false)

        val result = interceptor.preSend(frame(StompCommand.CONNECT), channel)

        assertNotNull(result)
        assertEquals(0, WebSocketConfig.activeConnections.get())
    }

    @Test
    fun `DISCONNECT frame no longer decrements - counting is event driven`() {
        `when`(serverConfig.getBoolean(ServerConfigService.KEY_REQUIRE_AUTH, false)).thenReturn(true)
        `when`(authService.validateToken("valid-token")).thenReturn("user")

        interceptor.preSend(frame(StompCommand.CONNECT, "login" to "valid-token"), channel)
        interceptor.preSend(frame(StompCommand.DISCONNECT), channel)

        assertEquals(0, WebSocketConfig.activeConnections.get())
    }

    @Test
    fun `DISCONNECT never drives the counter below zero`() {
        interceptor.preSend(frame(StompCommand.DISCONNECT), channel)
        interceptor.preSend(frame(StompCommand.DISCONNECT), channel)

        assertEquals(0, WebSocketConfig.activeConnections.get())
    }

    @Test
    fun `SUBSCRIBE and other commands leave the counter untouched`() {
        interceptor.preSend(frame(StompCommand.SUBSCRIBE), channel)

        assertEquals(0, WebSocketConfig.activeConnections.get())
    }
}
