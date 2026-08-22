package com.hippo.anotherviewer.web.config

import com.hippo.anotherviewer.web.service.SiteAuthService
import com.hippo.anotherviewer.web.service.ServerConfigService
import org.springframework.context.event.EventListener
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionConnectedEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Validates the STOMP CONNECT frame against the API token (sent in the `login`
 * header per contracts/websocket-protocol.md §1.3, mirrored as
 * `Authorization: Bearer …` by the frontend). When auth is required and the
 * token is missing/invalid the CONNECT is rejected with a STOMP ERROR frame.
 *
 * MASTER-2026-08-22 S4：activeConnections 计数从 preSend 的 CONNECT/DISCONNECT
 * 帧处理迁到 Spring 的 session 生命周期事件——异常掉线（TCP 断开、超时）不会送达
 * DISCONNECT 帧，旧实现只增不减泄漏失真；SessionDisconnectEvent 由传输层保证
 * 对每个会话（含异常终止）触发一次。
 */
@Component
class WsAuthChannelInterceptor(
    private val authService: SiteAuthService,
    private val serverConfig: ServerConfigService,
) : ChannelInterceptor {

    /** 已计数会话的 sessionId 集合，防 CONNECT 事件重复计数。 */
    private val countedSessions: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
            ?: return message
        if (accessor.command == StompCommand.CONNECT) {
            if (serverConfig.getBoolean(ServerConfigService.KEY_REQUIRE_AUTH, false)) {
                val token = accessor.getFirstNativeHeader("login")
                    ?: accessor.getFirstNativeHeader("Authorization")
                        ?.removePrefix("Bearer ")
                        ?.trim()
                    ?: ""
                // Throw rather than return null: an inbound-channel interceptor
                // returning null makes AbstractMessageChannel.send() return false
                // without notifying the STOMP client. Throwing is caught by
                // StompSubProtocolHandler, which sends the client an ERROR frame
                // and closes the connection with PROTOCOL_ERROR.
                require(authService.validateToken(token) != null) { "Authentication required" }
            }
        }
        return message
    }

    @EventListener
    fun onSessionConnected(event: SessionConnectedEvent) {
        val sessionId = StompHeaderAccessor.wrap(event.message).sessionId ?: return
        if (countedSessions.add(sessionId)) {
            WebSocketConfig.activeConnections.incrementAndGet()
        }
    }

    @EventListener
    fun onSessionDisconnected(event: SessionDisconnectEvent) {
        if (countedSessions.remove(event.sessionId)) {
            WebSocketConfig.activeConnections.updateAndGet { maxOf(0, it - 1) }
        }
    }
}
