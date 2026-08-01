package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.web.entity.ServerConfigEntity
import com.hippo.ehviewer.web.repository.ServerConfigRepository
import okhttp3.Headers
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.Optional

/**
 * Guard for the runtime-mutable outbound proxy used for E-Hentai traffic:
 * settings come from server_config, and the selector/authenticator handed to
 * the shared OkHttp client must reflect them per connection attempt.
 */
class WebProxyManagerTest {

    private lateinit var serverConfig: ServerConfigService
    private lateinit var manager: WebProxyManager

    /** In-memory-backed real [ServerConfigService]; no Mockito matcher pitfalls. */
    @BeforeEach
    fun setUp() {
        val repo = mock(ServerConfigRepository::class.java)
        val map = HashMap<String, ServerConfigEntity>()
        `when`(repo.findById(anyString())).thenAnswer { Optional.ofNullable(map[it.getArgument(0)]) }
        `when`(repo.existsById(anyString())).thenAnswer { map.containsKey(it.getArgument(0)) }
        `when`(repo.save(any(ServerConfigEntity::class.java))).thenAnswer { inv ->
            val entity = inv.getArgument<ServerConfigEntity>(0)
            map[entity.key] = entity
            entity
        }
        serverConfig = ServerConfigService(repo)
        manager = WebProxyManager(serverConfig)
    }

    private fun enableProxy(type: String = "http", host: String = "127.0.0.1", port: Int = 7890) {
        serverConfig.setBoolean(WebProxyManager.KEY_ENABLED, true)
        serverConfig.set(WebProxyManager.KEY_TYPE, type)
        serverConfig.set(WebProxyManager.KEY_HOST, host)
        serverConfig.set(WebProxyManager.KEY_PORT, port.toString())
    }

    @Test
    fun `disabled proxy yields null and NO_PROXY selector`() {
        assertNull(manager.activeProxy())
        assertEquals(listOf(Proxy.NO_PROXY), manager.selector().select(URI.create("https://e-hentai.org/")))
    }

    @Test
    fun `enabled http proxy returns an HTTP proxy with host and port`() {
        enableProxy("http", "192.168.1.5", 8080)
        val proxy = manager.activeProxy()
        assertNotNull(proxy)
        assertEquals(Proxy.Type.HTTP, proxy!!.type())
        val addr = proxy.address() as InetSocketAddress
        assertEquals("192.168.1.5", addr.hostString)
        assertEquals(8080, addr.port)
        assertTrue(manager.selector().select(URI.create("https://e-hentai.org/")).contains(proxy))
    }

    @Test
    fun `socks5 type maps to a SOCKS proxy`() {
        enableProxy("socks5", "127.0.0.1", 1080)
        assertEquals(Proxy.Type.SOCKS, manager.activeProxy()!!.type())
    }

    @Test
    fun `incomplete or invalid settings yield no proxy`() {
        enableProxy("http", "127.0.0.1", 0)
        assertNull(manager.activeProxy())
        enableProxy("http", "", 7890)
        assertNull(manager.activeProxy())
        enableProxy("http", "127.0.0.1", 99999)
        assertNull(manager.activeProxy())
    }

    @Test
    fun `authenticator answers 407 only for the configured proxy with credentials`() {
        enableProxy("http", "192.168.1.5", 8080)
        serverConfig.set(WebProxyManager.KEY_USERNAME, "bob")
        serverConfig.set(WebProxyManager.KEY_PASSWORD, "secret")

        val request = Request.Builder().url("https://e-hentai.org/").build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(407)
            .message("Proxy Authentication Required")
            .headers(Headers.Builder().add("Proxy-Authenticate", "Basic realm=\"proxy\"").build())
            .build()

        val authenticated = manager.authenticator().authenticate(null, response)
        assertNotNull(authenticated)
        val header = authenticated!!.header("Proxy-Authorization")
        assertNotNull(header)
        assertTrue(header!!.startsWith("Basic "))
    }

    @Test
    fun `authenticator stays silent when proxy auth is not configured`() {
        enableProxy("http", "192.168.1.5", 8080)
        val request = Request.Builder().url("https://e-hentai.org/").build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(407)
            .message("Proxy Authentication Required")
            .headers(Headers.Builder().add("Proxy-Authenticate", "Basic realm=\"proxy\"").build())
            .build()
        assertNull(manager.authenticator().authenticate(null, response))
    }
}
