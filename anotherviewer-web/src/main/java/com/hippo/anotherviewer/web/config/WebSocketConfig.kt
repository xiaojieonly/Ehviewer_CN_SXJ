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
        // 原生 WebSocket 端点（STOMP 裸帧，无 SockJS 会话层）——前端首选。
        // 背景：SockJS 的 websocket 传输在本部署形态下握手成功但服务端首帧
        // `o` 静默丢失（101 后零字节、sendMessage 无异常返回，Tomcat
        // 10.1.36/10.1.40 × Framework 6.2.6/6.2.7/6.2.8 全复现），导致
        // STOMP 永远握不上、下载进度推送死。原生端点绕开 SockJS 会话层。
        registry.addEndpoint("/ws-native")
            .setAllowedOriginPatterns(*patterns.toTypedArray())
        // SockJS 回退端点（HTTP 流/轮询传输仍可用；/ws/websocket 传输在
        // 上述形态下不可用，保留给旧客户端与协议契约兼容）。
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns(*patterns.toTypedArray())
            .withSockJS()
    }
}
