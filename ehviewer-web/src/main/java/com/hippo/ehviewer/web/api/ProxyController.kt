package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.ProxyTestResponse
import com.hippo.ehviewer.web.service.WebProxyManager
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.Proxy
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/v1/proxy")
class ProxyController(private val proxyManager: WebProxyManager) {

    /**
     * Tests connectivity to E-Hentai through the currently configured proxy.
     * With the proxy disabled this exercises the direct connection instead.
     */
    @PostMapping("/test")
    fun test(): ResponseEntity<ProxyTestResponse> {
        val proxy = proxyManager.activeProxy()
        val client = OkHttpClient.Builder()
            .proxy(proxy ?: Proxy.NO_PROXY)
            .proxyAuthenticator(proxyManager.authenticator())
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        val start = System.currentTimeMillis()
        return try {
            client.newCall(Request.Builder().url("https://e-hentai.org/").get().build()).execute().use { response ->
                val ok = response.isSuccessful || response.code() < 500
                ResponseEntity.ok(
                    ProxyTestResponse(ok, System.currentTimeMillis() - start, if (ok) "" else "HTTP ${response.code()}")
                )
            }
        } catch (e: Exception) {
            ResponseEntity.ok(
                ProxyTestResponse(false, System.currentTimeMillis() - start, e.message ?: e.javaClass.simpleName)
            )
        }
    }
}
