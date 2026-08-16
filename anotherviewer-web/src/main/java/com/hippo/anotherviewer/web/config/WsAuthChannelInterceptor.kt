package com.hippo.anotherviewer.web.config

import com.hippo.anotherviewer.web.service.SiteAuthService
import com.hippo.anotherviewer.web.service.ServerConfigService
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.stereotype.Component

/**
 * Validates the STOMP CONNECT frame against the API token (sent in the `login`
 * header per contracts/websocket-protocol.md §1.3, mirrored as
 * `Authorization: Bearer …` by the frontend). When auth is required and the
 * token is missing/invalid the CONNECT is rejected with a STOMP ERROR frame.
 */
@Component
class WsAuthChannelInterceptor(
    private val authService: SiteAuthService,
    private val serverConfig: ServerConfigService,
) : ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
            ?: return message
        when (accessor.command) {
            StompCommand.CONNECT -> {
                if (!serverConfig.getBoolean(ServerConfigService.KEY_REQUIRE_AUTH, false)) {
                    WebSocketConfig.activeConnections.incrementAndGet()
                } else {
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
                    WebSocketConfig.activeConnections.incrementAndGet()
                }
                return message
            }
            StompCommand.DISCONNECT -> {
                WebSocketConfig.activeConnections.updateAndGet { maxOf(0, it - 1) }
                return message
            }
            else -> return message
        }
    }
}
