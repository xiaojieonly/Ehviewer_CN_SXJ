package com.hippo.anotherviewer.web.config

import com.hippo.anotherviewer.web.service.ServerConfigService
import com.hippo.anotherviewer.web.service.SiteAuthService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.web.socket.messaging.SessionConnectedEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent

/**
 * MASTER-2026-08-22 S4：activeConnections 按 session 生命周期对账——
 * 同 sessionId 的 CONNECT 事件不重复计数；DISCONNECT 事件精确递减；
 * 异常掉线（未送达 DISCONNECT 帧的场景由传输层保证触发
 * SessionDisconnectEvent）不再泄漏计数。
 */
class WsAuthChannelInterceptorSessionCountTest {

    private lateinit var authService: SiteAuthService
    private lateinit var serverConfig: ServerConfigService
    private lateinit var interceptor: WsAuthChannelInterceptor

    @BeforeEach
    fun setUp() {
        authService = mock(SiteAuthService::class.java)
        serverConfig = mock(ServerConfigService::class.java)
        `when`(serverConfig.getBoolean(anyString(), org.mockito.ArgumentMatchers.anyBoolean())).thenAnswer { inv ->
            inv.getArgument<String>(0) == ServerConfigService.KEY_REQUIRE_AUTH && false
        }
        `when`(authService.validateToken("")).thenReturn(null)
        interceptor = WsAuthChannelInterceptor(authService, serverConfig)
        WebSocketConfig.activeConnections.set(0)
    }

    private fun connectedEvent(sessionId: String): SessionConnectedEvent {
        val message: Message<ByteArray> = MessageBuilder
            .withPayload(ByteArray(0))
            .setHeader("simpSessionId", sessionId)
            .build()
        return SessionConnectedEvent(this, message)
    }

    private fun disconnectEvent(sessionId: String): SessionDisconnectEvent =
        SessionDisconnectEvent(this, MessageBuilder.withPayload(ByteArray(0)).build(), sessionId, null)

    @Test
    fun `connect counts once per session and disconnect decrements`() {
        interceptor.onSessionConnected(connectedEvent("s1"))
        assertEquals(1, WebSocketConfig.activeConnections.get())

        interceptor.onSessionDisconnected(disconnectEvent("s1"))
        assertEquals(0, WebSocketConfig.activeConnections.get())
    }

    @Test
    fun `duplicate connect events for the same session are not double counted`() {
        interceptor.onSessionConnected(connectedEvent("s1"))
        interceptor.onSessionConnected(connectedEvent("s1"))
        assertEquals(1, WebSocketConfig.activeConnections.get())
    }

    @Test
    fun `disconnect for unknown session does not drive the counter negative`() {
        interceptor.onSessionDisconnected(disconnectEvent("ghost"))
        assertEquals(0, WebSocketConfig.activeConnections.get())
    }

    @Test
    fun `concurrent sessions count independently`() {
        interceptor.onSessionConnected(connectedEvent("a"))
        interceptor.onSessionConnected(connectedEvent("b"))
        assertEquals(2, WebSocketConfig.activeConnections.get())
        interceptor.onSessionDisconnected(disconnectEvent("a"))
        assertEquals(1, WebSocketConfig.activeConnections.get())
        interceptor.onSessionDisconnected(disconnectEvent("b"))
        assertEquals(0, WebSocketConfig.activeConnections.get())
        assertTrue(WebSocketConfig.activeConnections.get() >= 0)
    }
}
