package com.hippo.anotherviewer.web.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import java.util.concurrent.atomic.AtomicInteger

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val authInterceptor: WsAuthChannelInterceptor,
    @Value("\${ANOTHERVIEWER_WS_ORIGINS:*}")
    private val wsOrigins: String,
) : WebSocketMessageBrokerConfigurer {

    companion object {
        /**
         * Number of STOMP sessions with an accepted CONNECT frame.
         * Incremented by [WsAuthChannelInterceptor] on successful CONNECT,
         * decremented on DISCONNECT; exposed via the metrics endpoint.
         */
        val activeConnections = AtomicInteger(0)
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic")
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(authInterceptor)
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        val origins = wsOrigins.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val patterns = if (origins.contains("*")) listOf("*") else origins
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns(*patterns.toTypedArray())
            .withSockJS()
    }
}
