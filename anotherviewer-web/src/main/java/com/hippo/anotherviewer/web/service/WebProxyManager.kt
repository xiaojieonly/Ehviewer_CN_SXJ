package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.dto.ProxySettings
import okhttp3.Authenticator
import okhttp3.Challenge
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import org.springframework.stereotype.Component
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.Base64

/**
 * Runtime-mutable outbound proxy configuration for all Gallery Site traffic.
 *
 * The server fetches galleries / images from Gallery Site through the shared
 * [SiteSessionManager.okHttpClient]. OkHttp consults a [ProxySelector] and an
 * [Authenticator] at connection time, so this holder can switch the proxy on /
 * off without rebuilding the client: settings written via the admin panel are
 * read from [ServerConfigService] on every connection attempt.
 */
@Component
class WebProxyManager(private val serverConfig: ServerConfigService) {

    companion object {
        const val KEY_ENABLED = "proxy.enabled"
        const val KEY_TYPE = "proxy.type"
        const val KEY_HOST = "proxy.host"
        const val KEY_PORT = "proxy.port"
        const val KEY_USERNAME = "proxy.username"
        const val KEY_PASSWORD = "proxy.password"
    }

    /** Current proxy settings; never throws — missing keys fall back to defaults. */
    fun settings(): ProxySettings = ProxySettings(
        enabled = serverConfig.getBoolean(KEY_ENABLED, false),
        type = serverConfig.get(KEY_TYPE, "http").lowercase().ifEmpty { "http" },
        host = serverConfig.get(KEY_HOST),
        port = serverConfig.get(KEY_PORT, "0").toIntOrNull() ?: 0,
        username = serverConfig.get(KEY_USERNAME),
        password = serverConfig.get(KEY_PASSWORD),
    )

    /** The active [Proxy], or null when disabled / incomplete. */
    fun activeProxy(): Proxy? {
        val s = settings()
        if (!s.enabled || s.host.isBlank() || s.port <= 0 || s.port > 65535) return null
        val type = if (s.type == "socks5" || s.type == "socks") Proxy.Type.SOCKS else Proxy.Type.HTTP
        return Proxy(type, InetSocketAddress(s.host.trim(), s.port))
    }

    /**
     * Selector used by the shared OkHttp client. Returns the configured proxy for
     * every origin host when enabled, otherwise NO_PROXY (direct connection).
     */
    fun selector(): ProxySelector = object : ProxySelector() {
        override fun select(uri: URI): List<Proxy> {
            val proxy = activeProxy()
            return if (proxy != null) listOf(proxy) else listOf(Proxy.NO_PROXY)
        }

        override fun connectFailed(uri: URI, sa: SocketAddress, ioe: java.io.IOException) {
            // OkHttp falls back to a direct connection on failure; nothing to do.
        }
    }

    /**
     * Authenticator answering 407 Proxy-Authorization challenges with the
     * configured credentials. Only answers when the challenge target is our
     * proxy (i.e. [Route.proxy] is the configured one) and a username exists.
     */
    fun authenticator(): Authenticator = Authenticator { route, response ->
        val proxy = activeProxy() ?: return@Authenticator null
        val s = settings()
        if (s.username.isBlank()) return@Authenticator null
        // Only ever answer proxy (407) challenges, and never for a different proxy.
        if (response.code() != 407) return@Authenticator null
        if (route != null && route.proxy() != null && !route.proxy().equals(proxy)) {
            return@Authenticator null
        }
        val challenge = response.challenges().firstOrNull() ?: return@Authenticator null
        response.request().newBuilder()
            .header("Proxy-Authorization", proxyCredentials(challenge, s.username, s.password))
            .build()
    }

    private fun proxyCredentials(challenge: Challenge, username: String, password: String): String {
        return when (challenge.scheme().lowercase()) {
            "basic" -> Credentials.basic(username, password)
            else -> "Basic " + Base64.getEncoder().encodeToString(
                ("$username:$password").toByteArray(Charsets.UTF_8)
            )
        }
    }
}
