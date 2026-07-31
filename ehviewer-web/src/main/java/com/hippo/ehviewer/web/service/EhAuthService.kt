package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.web.dto.AuthResponse
import com.hippo.ehviewer.web.dto.AuthStatusResponse
import com.hippo.ehviewer.web.dto.LoginRequest
import com.hippo.ehviewer.web.dto.RegisterRequest
import com.hippo.ehviewer.web.entity.AuthConfigEntity
import com.hippo.ehviewer.web.repository.AuthConfigRepository
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class EhAuthService(
    private val authRepository: AuthConfigRepository,
    private val encryptionService: EncryptionService,
    private val sessionManager: EhSessionManager,
    private val serverConfig: ServerConfigService
) {
    private val tokenStore = ConcurrentHashMap<String, String>()

    fun register(request: RegisterRequest): AuthResponse {
        if (authRepository.existsByUsername(request.username)) {
            return AuthResponse(false, "Username already exists")
        }
        val entity = AuthConfigEntity().apply {
            username = request.username
            passwordHash = encryptionService.hashPassword(request.password)
            createdAt = System.currentTimeMillis()
        }
        authRepository.save(entity)
        val token = encryptionService.generateToken()
        tokenStore[token] = request.username
        return AuthResponse(true, "Registration successful", token, request.username)
    }

    fun login(request: LoginRequest): AuthResponse {
        val entity = authRepository.findByUsername(request.username)
            ?: return AuthResponse(false, "Invalid username or password")
        if (!encryptionService.verifyPassword(request.password, entity.passwordHash)) {
            return AuthResponse(false, "Invalid username or password")
        }
        entity.lastLoginAt = System.currentTimeMillis()
        authRepository.save(entity)
        val token = encryptionService.generateToken()
        tokenStore[token] = request.username
        return AuthResponse(true, "Login successful", token, request.username)
    }

    /**
     * Establish an E-Hentai session through the unified cookie store shared with
     * ehviewer-core. On success the login cookies live in [EhSessionManager.cookieStore]
     * and are reused by core's HTTP client.
     */
    fun loginEh(username: String, password: String): AuthResponse {
        return sessionManager.signIn(username, password).fold(
            onSuccess = { displayName ->
                AuthResponse(true, "E-Hentai login successful", username = displayName ?: username)
            },
            onFailure = { err ->
                AuthResponse(false, "E-Hentai login failed: ${err.message}")
            }
        )
    }

    fun getStatus(token: String?): AuthStatusResponse {
        // E-Hentai session state is reported regardless of the WebUI token so clients
        // can detect an expired E-Hentai login and prompt for re-login.
        val ehStatus = sessionManager.getStatus()
        val username = token?.let { tokenStore[it] }
        val authRequired = serverConfig.getBoolean(ServerConfigService.KEY_REQUIRE_AUTH, false)
        return AuthStatusResponse(
            authenticated = username != null,
            username = username,
            authRequired = authRequired,
            ehSessionValid = ehStatus.state == EhSessionManager.SessionState.VALID,
            ehSessionExpired = ehStatus.expired
        )
    }

    fun validateToken(token: String?): String? {
        if (token == null) return null
        return tokenStore[token]
    }

    fun logout(token: String?) {
        if (token != null) {
            tokenStore.remove(token)
        }
        // Also tear down the shared E-Hentai session so core's client stops sending
        // the (now unwanted) login cookies.
        sessionManager.signOut()
    }
}
