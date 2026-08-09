package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.AuthResponse
import com.hippo.anotherviewer.web.dto.ChangePasswordRequest
import com.hippo.anotherviewer.web.dto.EhSessionCookieDto
import com.hippo.anotherviewer.web.dto.EhSessionResponse
import com.hippo.anotherviewer.web.dto.LoginRequest
import com.hippo.anotherviewer.web.dto.PairCompleteResponse
import com.hippo.anotherviewer.web.dto.RegisterDeviceRequest
import com.hippo.anotherviewer.web.entity.AuthConfigEntity
import com.hippo.anotherviewer.web.entity.SyncDeviceEntity
import com.hippo.anotherviewer.web.entity.TokenEntity
import com.hippo.anotherviewer.web.repository.AuthConfigRepository
import com.hippo.anotherviewer.web.repository.SyncDeviceRepository
import com.hippo.anotherviewer.web.repository.TokenRepository
import com.hippo.anotherviewer.web.service.SiteAuthService
import com.hippo.anotherviewer.web.service.SiteSessionManager
import com.hippo.anotherviewer.web.service.EncryptionService
import com.hippo.anotherviewer.web.service.LoginRateLimiter
import com.hippo.anotherviewer.web.service.ServerConfigService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.concurrent.ConcurrentHashMap

/**
 * Contract tests for POST /api/v1/auth/change-password and login rate limiting:
 *
 * HTTP layer (MockMvc + real bean validation) asserts the exact response
 * shapes; service layer (in-memory repo fakes, real bcrypt) asserts the
 * re-hash semantics: old password stops working, new one takes over, and
 * existing sessions stay valid.
 */
class AuthControllerTest {

    private val encryption = EncryptionService()

    // ---------------------------------------------------------------------
    // HTTP layer
    // ---------------------------------------------------------------------

    private fun principal(name: String): RequestPostProcessor = RequestPostProcessor { request ->
        request.userPrincipal = UsernamePasswordAuthenticationToken(
            name, null, listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
        request
    }

    /**
     * Stubs the service with real argument values: Kotlin's call-site null
     * assertions reject Mockito's null-returning `any(Class)` matcher for
     * non-null Kotlin parameters (NPE "any(...) must not be null").
     */
    private fun mockMvc(serviceResult: AuthResponse): MockMvc {
        val authService = mock(SiteAuthService::class.java)
        for (username in listOf("alice", "default")) {
            for (oldPassword in listOf("old-pass", "wrong")) {
                `when`(authService.changePassword(username, ChangePasswordRequest(oldPassword, "new-pass")))
                    .thenReturn(serviceResult)
            }
        }
        return MockMvcBuilders.standaloneSetup(AuthController(authService, LoginRateLimiter(5, 60000, false))).build()
    }

    @Test
    fun `successful change returns 200 with the exact contract body`() {
        val mvc = mockMvc(AuthResponse(true, "Password changed"))
        mvc.perform(
            post("/api/v1/auth/change-password")
                .with(principal("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"oldPassword":"old-pass","newPassword":"new-pass"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Password changed"))
    }

    @Test
    fun `service failure returns 400 with the exact contract body`() {
        val mvc = mockMvc(AuthResponse(false, "Old password is incorrect"))
        mvc.perform(
            post("/api/v1/auth/change-password")
                .with(principal("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"oldPassword":"wrong","newPassword":"new-pass"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Old password is incorrect"))
    }

    @Test
    fun `auth disabled returns 400`() {
        val mvc = mockMvc(AuthResponse(false, "Authentication is disabled on this server"))
        mvc.perform(
            post("/api/v1/auth/change-password")
                .with(principal("default"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"oldPassword":"old-pass","newPassword":"new-pass"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Authentication is disabled on this server"))
    }

    @Test
    fun `new password shorter than 6 characters is rejected by validation`() {
        val mvc = mockMvc(AuthResponse(true, "Password changed"))
        mvc.perform(
            post("/api/v1/auth/change-password")
                .with(principal("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"oldPassword":"old-pass","newPassword":"short"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("New password must be at least 6 characters"))
    }

    @Test
    fun `new password longer than 72 characters is rejected by validation`() {
        val mvc = mockMvc(AuthResponse(true, "Password changed"))
        mvc.perform(
            post("/api/v1/auth/change-password")
                .with(principal("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"oldPassword":"old-pass","newPassword":"${"a".repeat(73)}"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("New password must be at most 72 characters"))
    }

    @Test
    fun `missing fields are rejected by validation`() {
        val mvc = mockMvc(AuthResponse(true, "Password changed"))
        mvc.perform(
            post("/api/v1/auth/change-password")
                .with(principal("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"newPassword":"new-pass"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Malformed request body: required fields are missing or invalid"))
    }

    // ---------------------------------------------------------------------
    // Auto-pairing (POST /api/v1/auth/register-device)
    // ---------------------------------------------------------------------

    private fun registerDeviceMvc(response: PairCompleteResponse): MockMvc {
        val authService = mock(SiteAuthService::class.java)
        // Standalone MockMvc has no security filter, so the controller falls
        // back to the anonymous "default" principal.
        `when`(authService.registerDevice("default", RegisterDeviceRequest("android-x", "Phone", "android", null)))
            .thenReturn(response)
        return MockMvcBuilders.standaloneSetup(AuthController(authService, LoginRateLimiter(5, 60000, false))).build()
    }

    private fun registerDevicePost(body: String) = post("/api/v1/auth/register-device")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)

    @Test
    fun `register-device success returns 200 with the exact contract body`() {
        val mvc = registerDeviceMvc(PairCompleteResponse(true, "Pairing successful", "tok", "default"))
        mvc.perform(
            registerDevicePost("""{"deviceId":"android-x","deviceName":"Phone","platform":"android"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Pairing successful"))
            .andExpect(jsonPath("$.token").value("tok"))
            .andExpect(jsonPath("$.username").value("default"))
    }

    @Test
    fun `register-device with auto-pairing disabled returns 400`() {
        val mvc = registerDeviceMvc(PairCompleteResponse(false, "Auto-pairing disabled on this server"))
        mvc.perform(registerDevicePost("""{"deviceId":"android-x","deviceName":"Phone"}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Auto-pairing disabled on this server"))
    }

    @Test
    fun `register-device with a missing setup key returns 409`() {
        val mvc = registerDeviceMvc(PairCompleteResponse(false, "Setup key required"))
        mvc.perform(registerDevicePost("""{"deviceId":"android-x","deviceName":"Phone"}"""))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Setup key required"))
    }

    @Test
    fun `register-device with a blank deviceId is rejected by validation`() {
        val mvc = registerDeviceMvc(PairCompleteResponse(true, "Pairing successful", "tok", "default"))
        mvc.perform(registerDevicePost("""{"deviceId":"","deviceName":"Phone"}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
    }

    // ---------------------------------------------------------------------
    // Login rate limiting
    // ---------------------------------------------------------------------

    private fun loginPost(ip: String = "127.0.0.1") = post("/api/v1/auth/login")
        .with { request ->
            request.remoteAddr = ip
            request
        }
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"username":"alice","password":"wrong-pass"}""")

    private fun loginMvc(
        rateLimiter: LoginRateLimiter = LoginRateLimiter(5, 60000, true),
    ): MockMvc {
        val authService = mock(SiteAuthService::class.java)
        `when`(authService.login(LoginRequest("alice", "wrong-pass")))
            .thenReturn(AuthResponse(false, "Invalid username or password"))
        return MockMvcBuilders.standaloneSetup(AuthController(authService, rateLimiter)).build()
    }

    @Test
    fun `login locks after 5 consecutive failures and rejects the 6th with 429`() {
        val mvc = loginMvc()
        repeat(5) {
            mvc.perform(loginPost()).andExpect(status().isBadRequest)
        }
        mvc.perform(loginPost())
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Too many login attempts. Try again later."))
    }

    @Test
    fun `login rate limit is bypassed when disabled`() {
        val mvc = loginMvc(rateLimiter = LoginRateLimiter(5, 60000, false))
        repeat(6) {
            mvc.perform(loginPost()).andExpect(status().isBadRequest)
        }
    }

    @Test
    fun `lockout is per IP`() {
        val mvc = loginMvc()
        repeat(5) {
            mvc.perform(loginPost("1.1.1.1")).andExpect(status().isBadRequest)
        }
        mvc.perform(loginPost("1.1.1.1")).andExpect(status().isTooManyRequests)
        mvc.perform(loginPost("2.2.2.2")).andExpect(status().isBadRequest)
    }

    @Test
    fun `successful login resets the failure counter`() {
        val authService = mock(SiteAuthService::class.java)
        `when`(authService.login(LoginRequest("alice", "wrong-pass")))
            .thenReturn(AuthResponse(false, "Invalid username or password"))
        `when`(authService.login(LoginRequest("alice", "right-pass")))
            .thenReturn(AuthResponse(true, "Logged in", token = "tok"))
        val mvc = MockMvcBuilders.standaloneSetup(AuthController(authService, LoginRateLimiter(5, 60000, true))).build()

        repeat(4) {
            mvc.perform(loginPost()).andExpect(status().isBadRequest)
        }
        mvc.perform(
            post("/api/v1/auth/login")
                .with { request ->
                    request.remoteAddr = "127.0.0.1"
                    request
                }
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"alice","password":"right-pass"}""")
        ).andExpect(status().isOk)
        repeat(5) {
            mvc.perform(loginPost()).andExpect(status().isBadRequest)
        }
    }

    @Test
    fun `lockout expires after the configured window`() {
        val mvc = loginMvc(rateLimiter = LoginRateLimiter(5, 100, true))
        repeat(5) {
            mvc.perform(loginPost()).andExpect(status().isBadRequest)
        }
        mvc.perform(loginPost()).andExpect(status().isTooManyRequests)
        Thread.sleep(150)
        mvc.perform(loginPost()).andExpect(status().isBadRequest)
    }

    // ---------------------------------------------------------------------
    // EH session endpoints
    // ---------------------------------------------------------------------

    private fun ehMvc(authService: SiteAuthService): MockMvc =
        MockMvcBuilders.standaloneSetup(AuthController(authService, LoginRateLimiter(5, 60000, false))).build()

    @Test
    fun `eh login success returns 200 with the exact contract body`() {
        val authService = mock(SiteAuthService::class.java)
        `when`(authService.loginEh("alice", "secret"))
            .thenReturn(AuthResponse(true, "Gallery Site login successful", username = "Alice"))
        ehMvc(authService).perform(
            post("/api/v1/auth/eh-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"alice","password":"secret"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Gallery Site login successful"))
            .andExpect(jsonPath("$.username").value("Alice"))
    }

    @Test
    fun `eh login failure returns 400 with the exact contract body`() {
        val authService = mock(SiteAuthService::class.java)
        `when`(authService.loginEh("alice", "wrong"))
            .thenReturn(AuthResponse(false, "Gallery Site login failed: invalid credentials"))
        ehMvc(authService).perform(
            post("/api/v1/auth/eh-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"alice","password":"wrong"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Gallery Site login failed: invalid credentials"))
    }

    @Test
    fun `eh logout clears only the site session and keeps the webui token`() {
        val authService = mock(SiteAuthService::class.java)
        ehMvc(authService).perform(post("/api/v1/auth/eh-logout"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Logged out"))
        verify(authService).logoutEh()
        verify(authService, never()).logout(anyString())
    }

    @Test
    fun `eh session returns the cookie contract shape when signed in`() {
        val authService = mock(SiteAuthService::class.java)
        `when`(authService.ehSession()).thenReturn(
            EhSessionResponse(
                signedIn = true,
                expired = false,
                cookies = listOf(
                    EhSessionCookieDto("ipb_member_id", "12345", ".e-hentai.org", 9_999_999_999_999L),
                    EhSessionCookieDto("ipb_pass_hash", "abc123", ".e-hentai.org", 9_999_999_999_999L),
                ),
                gallerySite = 1,
            )
        )
        ehMvc(authService).perform(get("/api/v1/auth/eh-session"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.signedIn").value(true))
            .andExpect(jsonPath("$.expired").value(false))
            .andExpect(jsonPath("$.gallerySite").value(1))
            .andExpect(jsonPath("$.cookies.length()").value(2))
            .andExpect(jsonPath("$.cookies[0].name").value("ipb_member_id"))
            .andExpect(jsonPath("$.cookies[0].value").value("12345"))
            .andExpect(jsonPath("$.cookies[1].name").value("ipb_pass_hash"))
    }

    @Test
    fun `eh session reports signed out with empty cookies and default gallery site`() {
        val authService = mock(SiteAuthService::class.java)
        `when`(authService.ehSession()).thenReturn(EhSessionResponse(signedIn = false, expired = false, cookies = emptyList()))
        ehMvc(authService).perform(get("/api/v1/auth/eh-session"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.signedIn").value(false))
            .andExpect(jsonPath("$.expired").value(false))
            .andExpect(jsonPath("$.gallerySite").value(0))
            .andExpect(jsonPath("$.cookies").isArray)
            .andExpect(jsonPath("$.cookies.length()").value(0))
    }

    @Test
    fun `eh site with a valid value returns 200 with the exact contract body`() {
        val authService = mock(SiteAuthService::class.java)
        `when`(authService.setGallerySite(1)).thenReturn(true)
        ehMvc(authService).perform(
            put("/api/v1/auth/eh-site")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gallerySite":1}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Gallery site updated"))
        verify(authService).setGallerySite(1)
    }

    @Test
    fun `eh site with an out-of-range value fails validation with 400`() {
        val authService = mock(SiteAuthService::class.java)
        ehMvc(authService).perform(
            put("/api/v1/auth/eh-site")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gallerySite":2}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
        verify(authService, never()).setGallerySite(anyInt())
    }

    @Test
    fun `eh site without the required field fails with 400`() {
        val authService = mock(SiteAuthService::class.java)
        ehMvc(authService).perform(
            put("/api/v1/auth/eh-site")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("must not be null"))
        verify(authService, never()).setGallerySite(anyInt())
    }

    @Test
    fun `eh site service rejection returns 400`() {
        val authService = mock(SiteAuthService::class.java)
        `when`(authService.setGallerySite(0)).thenReturn(false)
        ehMvc(authService).perform(
            put("/api/v1/auth/eh-site")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gallerySite":0}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Invalid gallery site value"))
    }

    // ---------------------------------------------------------------------
    // Service layer
    // ---------------------------------------------------------------------

    /** In-memory fake that persists accounts across calls. */
    private fun mockAuthRepo(vararg users: AuthConfigEntity): AuthConfigRepository {
        val repo = mock(AuthConfigRepository::class.java)
        val store = ConcurrentHashMap<String, AuthConfigEntity>()
        users.forEach { store[it.username] = it }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv -> store[inv.getArgument(0)] }
        `when`(repo.existsByUsername(anyString())).thenAnswer { inv -> store.containsKey(inv.getArgument(0)) }
        `when`(repo.save(any(AuthConfigEntity::class.java))).thenAnswer { inv ->
            val entity = inv.getArgument<AuthConfigEntity>(0)
            store[entity.username] = entity
            entity
        }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        return repo
    }

    /** In-memory fake token store, keyed by token hash. */
    private fun mockTokenRepo(): TokenRepository {
        val repo = mock(TokenRepository::class.java)
        val store = ConcurrentHashMap<String, TokenEntity>()
        `when`(repo.save(any(TokenEntity::class.java))).thenAnswer { inv ->
            val entity = inv.getArgument<TokenEntity>(0)
            store[entity.tokenHash] = entity
            entity
        }
        `when`(repo.findByTokenHash(anyString())).thenAnswer { inv -> store[inv.getArgument(0)] }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        doAnswer { inv -> store.remove(inv.getArgument<String>(0)) }.`when`(repo).deleteByTokenHash(anyString())
        doAnswer { inv -> store.values.remove(inv.getArgument(0)) }.`when`(repo).delete(any(TokenEntity::class.java))
        return repo
    }

    private fun newAuthService(
        authRepo: AuthConfigRepository = mockAuthRepo(),
        requireAuth: Boolean = false,
        tokenRepo: TokenRepository = mockTokenRepo(),
    ): SiteAuthService {
        val sessionManager = mock(SiteSessionManager::class.java)
        `when`(sessionManager.getStatus()).thenReturn(
            SiteSessionManager.SessionStatus(SiteSessionManager.SessionState.SIGNED_OUT, false, false, 0L)
        )
        val serverConfig = mock(ServerConfigService::class.java)
        `when`(serverConfig.getBoolean(anyString(), anyBoolean())).thenAnswer { inv ->
            inv.getArgument<String>(0) == ServerConfigService.KEY_REQUIRE_AUTH && requireAuth
        }
        `when`(serverConfig.getLong(anyString(), anyLong())).thenReturn(86400L)
        return SiteAuthService(
            authRepo,
            encryption,
            sessionManager,
            serverConfig,
            tokenRepo,
            mock(SyncDeviceRepository::class.java),
            SiteCoreConfigProperties(),
        )
    }

    private fun account(username: String, password: String): AuthConfigEntity =
        AuthConfigEntity().apply {
            this.username = username
            passwordHash = encryption.hashPassword(password)
        }

    @Test
    fun `successful change rehashes so old password stops working and new one works`() {
        val authRepo = mockAuthRepo(account("alice", "old-pass"))
        val authService = newAuthService(authRepo, requireAuth = true)

        val result = authService.changePassword("alice", ChangePasswordRequest("old-pass", "new-pass"))

        assertTrue(result.success)
        assertEquals("Password changed", result.message)
        val storedHash = authRepo.findByUsername("alice")!!.passwordHash
        assertFalse(encryption.verifyPassword("old-pass", storedHash))
        assertTrue(encryption.verifyPassword("new-pass", storedHash))
        assertTrue(authService.login(LoginRequest("alice", "new-pass")).success)
        assertFalse(authService.login(LoginRequest("alice", "old-pass")).success)
    }

    @Test
    fun `successful change keeps existing sessions valid`() {
        val authRepo = mockAuthRepo(account("alice", "old-pass"))
        val authService = newAuthService(authRepo, requireAuth = true)

        val login = authService.login(LoginRequest("alice", "old-pass"))
        val token = login.token!!
        authService.changePassword("alice", ChangePasswordRequest("old-pass", "new-pass"))

        assertEquals("alice", authService.validateToken(token))
    }

    @Test
    fun `wrong old password is rejected`() {
        val authService = newAuthService(mockAuthRepo(account("alice", "old-pass")), requireAuth = true)

        val result = authService.changePassword("alice", ChangePasswordRequest("wrong-pass", "new-pass"))

        assertFalse(result.success)
        assertEquals("Old password is incorrect", result.message)
    }

    @Test
    fun `short new password is rejected`() {
        val authService = newAuthService(mockAuthRepo(account("alice", "old-pass")), requireAuth = true)

        val result = authService.changePassword("alice", ChangePasswordRequest("old-pass", "five!"))
        assertFalse(result.success)
        assertEquals("New password must be at least 6 characters", result.message)
    }

    @Test
    fun `overlong new password is rejected`() {
        val authService = newAuthService(mockAuthRepo(account("alice", "old-pass")), requireAuth = true)

        val result = authService.changePassword("alice", ChangePasswordRequest("old-pass", "a".repeat(73)))
        assertFalse(result.success)
        assertEquals("New password must be at most 72 characters", result.message)
    }

    @Test
    fun `blank fields are rejected`() {
        val authService = newAuthService(mockAuthRepo(account("alice", "old-pass")), requireAuth = true)

        val result = authService.changePassword("alice", ChangePasswordRequest(" ", " "))
        assertFalse(result.success)
        assertEquals("Old password and new password are required", result.message)
    }

    @Test
    fun `unknown account is rejected`() {
        val authService = newAuthService(mockAuthRepo(), requireAuth = true)

        val result = authService.changePassword("ghost", ChangePasswordRequest("old-pass", "new-pass"))
        assertFalse(result.success)
        assertEquals("Account not found", result.message)
    }

    @Test
    fun `change is refused when auth is disabled`() {
        val authService = newAuthService(mockAuthRepo(account("alice", "old-pass")), requireAuth = false)

        val result = authService.changePassword("default", ChangePasswordRequest("old-pass", "new-pass"))
        assertFalse(result.success)
        assertEquals("Authentication is disabled on this server", result.message)
    }
}
