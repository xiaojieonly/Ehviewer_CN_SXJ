package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.dto.ProxySettings
import com.hippo.anotherviewer.web.dto.ProxyTestRequest
import com.hippo.anotherviewer.web.dto.ProxyTestResponse
import com.hippo.anotherviewer.web.service.WebProxyManager
import jakarta.validation.Valid
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/v1/proxy")
class ProxyController(private val proxyManager: WebProxyManager) {

    /**
     * Tests connectivity to Gallery Site through a proxy. With no body the currently
     * saved settings are used; with a body the given values are tested, so the
     * admin UI can validate a form before saving it.
     */
    @PostMapping("/test")
    fun test(@Valid @RequestBody(required = false) request: ProxyTestRequest?): ResponseEntity<ProxyTestResponse> {
        val s = mergeWithSaved(request)
        val proxy = toProxy(s)
        val client = OkHttpClient.Builder()
            .proxy(proxy ?: Proxy.NO_PROXY)
            .proxyAuthenticator { _, response ->
                if (s.username.isBlank()) return@proxyAuthenticator null
                response.request.newBuilder()
                    .header("Proxy-Authorization", Credentials.basic(s.username, s.password))
                    .build()
            }
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        val start = System.currentTimeMillis()
        return try {
            client.newCall(Request.Builder().url("https://e-hentai.org/").get().build()).execute().use { response ->
                val ok = response.isSuccessful || response.code < 500
                ResponseEntity.ok(
                    ProxyTestResponse(ok, System.currentTimeMillis() - start, if (ok) "" else "HTTP ${response.code}")
                )
            }
        } catch (e: Exception) {
            ResponseEntity.ok(
                ProxyTestResponse(false, System.currentTimeMillis() - start, e.message ?: e.javaClass.simpleName)
            )
        }
    }

    private fun mergeWithSaved(request: ProxyTestRequest?): ProxySettings {
        val saved = proxyManager.settings()
        if (request == null) return saved
        return saved.copy(
            enabled = request.enabled ?: saved.enabled,
            type = request.type ?: saved.type,
            host = request.host ?: saved.host,
            port = request.port ?: saved.port,
            username = request.username ?: saved.username,
            password = request.password ?: saved.password,
        )
    }

    private fun toProxy(s: ProxySettings): Proxy? {
        if (!s.enabled || s.host.isBlank() || s.port <= 0 || s.port > 65535) return null
        val type = if (s.type == "socks5" || s.type == "socks") Proxy.Type.SOCKS else Proxy.Type.HTTP
        return Proxy(type, InetSocketAddress(s.host.trim(), s.port))
    }
}
