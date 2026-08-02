package com.hippo.anotherviewer.web.config

import com.hippo.anotherviewer.web.service.SiteAuthService
import com.hippo.anotherviewer.web.service.ServerConfigService
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageBuilder
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
                val accepted = if (!serverConfig.getBoolean(ServerConfigService.KEY_REQUIRE_AUTH, true)) {
                    true
                } else {
                    val token = accessor.getFirstNativeHeader("login")
                        ?: accessor.getFirstNativeHeader("Authorization")
                            ?.removePrefix("Bearer ")
                            ?.trim()
                        ?: ""
                    if (authService.validateToken(token) == null) {
                        val error = StompHeaderAccessor.create(StompCommand.ERROR)
                        error.message = "Authentication required"
                        error.setSessionId(accessor.sessionId)
                        val errorMessage = MessageBuilder.createMessage(ByteArray(0), error.messageHeaders)
                        channel.send(errorMessage)
                        return null
                    }
                    true
                }
                if (accepted) WebSocketConfig.activeConnections.incrementAndGet()
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
