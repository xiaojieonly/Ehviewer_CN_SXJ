package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.entity.ServerConfigEntity
import com.hippo.anotherviewer.web.repository.ServerConfigRepository
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
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.Optional

/**
 * Guard for the runtime-mutable outbound proxy used for Gallery Site traffic:
 * settings come from server_config, and the selector/authenticator handed to
 * the shared OkHttp client must reflect them per connection attempt. The proxy
 * password is stored encrypted at rest and must be decrypted for use.
 */
class WebProxyManagerTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var serverConfig: ServerConfigService
    private lateinit var manager: WebProxyManager
    private val storedValues = HashMap<String, String>()

    /** In-memory-backed real [ServerConfigService]; no Mockito matcher pitfalls. */
    @BeforeEach
    fun setUp() {
        val repo = mock(ServerConfigRepository::class.java)
        val entities = HashMap<String, ServerConfigEntity>()
        `when`(repo.findById(anyString())).thenAnswer { Optional.ofNullable(entities[it.getArgument(0)]) }
        `when`(repo.existsById(anyString())).thenAnswer { entities.containsKey(it.getArgument(0)) }
        `when`(repo.save(any(ServerConfigEntity::class.java))).thenAnswer { inv ->
            val entity = inv.getArgument<ServerConfigEntity>(0)
            entities[entity.key] = entity
            storedValues[entity.key] = entity.value
            entity
        }
        val config = SiteCoreConfigProperties()
        config.security.encryptionKeyPath = File(tempDir, "test.key").absolutePath
        serverConfig = ServerConfigService(repo, EncryptionService(), config)
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

    @Test
    fun `proxy password is encrypted at rest and decrypted for use`() {
        enableProxy("http", "192.168.1.5", 8080)
        serverConfig.set(WebProxyManager.KEY_USERNAME, "bob")
        serverConfig.set(WebProxyManager.KEY_PASSWORD, "s3cret-pass")

        // The stored value must not be the plaintext password.
        val stored = storedValues[WebProxyManager.KEY_PASSWORD]!!
        assertTrue(stored != "s3cret-pass" && stored.startsWith("enc:v1:"))
        // The manager (and any consumer) sees the decrypted plaintext.
        assertEquals("s3cret-pass", manager.settings().password)
    }
}
