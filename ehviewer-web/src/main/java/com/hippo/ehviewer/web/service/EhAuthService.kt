package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.web.config.EhCoreConfigProperties
import com.hippo.ehviewer.web.dto.AuthResponse
import com.hippo.ehviewer.web.dto.AuthStatusResponse
import com.hippo.ehviewer.web.dto.LoginRequest
import com.hippo.ehviewer.web.dto.PairCodeResponse
import com.hippo.ehviewer.web.dto.PairCompleteRequest
import com.hippo.ehviewer.web.dto.PairCompleteResponse
import com.hippo.ehviewer.web.dto.RegisterRequest
import com.hippo.ehviewer.web.entity.AuthConfigEntity
import com.hippo.ehviewer.web.entity.SyncDeviceEntity
import com.hippo.ehviewer.web.entity.TokenEntity
import com.hippo.ehviewer.web.repository.AuthConfigRepository
import com.hippo.ehviewer.web.repository.SyncDeviceRepository
import com.hippo.ehviewer.web.repository.TokenRepository
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

@Service
class EhAuthService(
    private val authRepository: AuthConfigRepository,
    private val encryptionService: EncryptionService,
    private val sessionManager: EhSessionManager,
    private val serverConfig: ServerConfigService,
    private val tokenRepository: TokenRepository,
    private val syncDeviceRepository: SyncDeviceRepository,
    private val config: EhCoreConfigProperties,
) {
    // tokenHash -> cached token metadata. Rebuilt from the DB at startup so
    // tokens survive restarts; every check also validates the TTL.
    private val tokenCache = ConcurrentHashMap<String, TokenCacheEntry>()
    private val pairCodes = ConcurrentHashMap<String, PairCodeEntry>()

    private class TokenCacheEntry(val username: String, val expiresAt: Long)

    private class PairCodeEntry(val username: String, val expiresAt: Long)

    companion object {
        const val PAIR_CODE_TTL_SECONDS = 600L
        const val PAIR_CODE_LENGTH = 6
        const val MIN_TOKEN_TTL_SECONDS = 60L
        private val PAIR_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun sha256(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
    }

    @PostConstruct
    fun rebuildTokenCache() {
        tokenCache.clear()
        tokenRepository.findAll().forEach {
            tokenCache[it.tokenHash] = TokenCacheEntry(it.username, it.expiresAt)
        }
    }

    /** Registration follows the auth mode: allowed whenever require_auth is on. */
    fun isRegistrationAllowed(): Boolean =
        serverConfig.getBoolean(ServerConfigService.KEY_REQUIRE_AUTH, false)

    fun register(request: RegisterRequest): AuthResponse {
        if (!isRegistrationAllowed()) {
            return AuthResponse(false, "Registration is disabled on this server")
        }
        if (authRepository.existsByUsername(request.username)) {
            return AuthResponse(false, "Username already exists")
        }
        val entity = AuthConfigEntity().apply {
            username = request.username
            passwordHash = encryptionService.hashPassword(request.password)
            createdAt = System.currentTimeMillis()
        }
        authRepository.save(entity)
        val token = issueToken(request.username)
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
        val token = issueToken(request.username)
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
        val username = validateToken(token)
        val authRequired = serverConfig.getBoolean(ServerConfigService.KEY_REQUIRE_AUTH, true)
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
        val hash = sha256(token)
        val now = System.currentTimeMillis()
        var entry = tokenCache[hash]
        if (entry == null) {
            // Cache miss: fall back to the DB (token issued before restart).
            val entity = tokenRepository.findByTokenHash(hash) ?: return null
            if (entity.expiresAt < now) {
                tokenRepository.delete(entity)
                return null
            }
            entry = TokenCacheEntry(entity.username, entity.expiresAt)
            tokenCache[hash] = entry
        }
        if (entry.expiresAt < now) {
            tokenCache.remove(hash)
            tokenRepository.deleteByTokenHash(hash)
            return null
        }
        return entry.username
    }

    fun logout(token: String?) {
        if (token != null) {
            val hash = sha256(token)
            tokenRepository.deleteByTokenHash(hash)
            tokenCache.remove(hash)
            // Drop any device bound to this token as well.
            syncDeviceRepository.findByToken(hash)?.let { syncDeviceRepository.delete(it) }
        }
        // Also tear down the shared E-Hentai session so core's client stops sending
        // the (now unwanted) login cookies.
        sessionManager.signOut()
    }

    // -------------------------------------------------------------------------
    // Device pairing
    // -------------------------------------------------------------------------

    /** Generates a short-lived single-use pairing code for the given username. */
    fun generatePairCode(username: String, ttlSeconds: Long = PAIR_CODE_TTL_SECONDS): PairCodeResponse {
        val secureRandom = SecureRandom()
        val code = buildString(PAIR_CODE_LENGTH) {
            repeat(PAIR_CODE_LENGTH) {
                append(PAIR_CODE_ALPHABET[secureRandom.nextInt(PAIR_CODE_ALPHABET.length)])
            }
        }
        pairCodes[code] = PairCodeEntry(username, System.currentTimeMillis() + ttlSeconds * 1000)
        return PairCodeResponse(code, System.currentTimeMillis() + ttlSeconds * 1000)
    }

    /**
     * Exchanges a pairing code for a device-scoped token. Single-use; codes
     * expire after 10 minutes. The returned token authenticates the device as
     * the user who generated the code.
     */
    fun completePairing(request: PairCompleteRequest): PairCompleteResponse {
        val entry = pairCodes.remove(request.code)
        if (entry == null || entry.expiresAt < System.currentTimeMillis()) {
            return PairCompleteResponse(false, "Pairing code is invalid or expired")
        }
        val token = issueToken(entry.username)
        val device = syncDeviceRepository.findByDeviceId(request.deviceId)
            ?: SyncDeviceEntity().apply { this.deviceId = request.deviceId }
        device.deviceName = request.deviceName
        device.platform = request.platform
        device.pairedAt = System.currentTimeMillis()
        device.lastSeen = device.pairedAt
        device.token = sha256(token)
        device.username = entry.username
        syncDeviceRepository.save(device)
        return PairCompleteResponse(true, "Pairing successful", token, entry.username)
    }

    /**
     * Revokes a paired device and invalidates its token. When [username] is
     * provided the device must belong to that user; returns false otherwise.
     */
    fun revokeDevice(deviceId: String, username: String? = null): Boolean {
        val device = syncDeviceRepository.findByDeviceId(deviceId) ?: return false
        if (username != null && device.username != username) return false
        device.token?.let { hash ->
            tokenRepository.deleteByTokenHash(hash)
            tokenCache.remove(hash)
        }
        syncDeviceRepository.delete(device)
        return true
    }

    private fun issueToken(username: String): String {
        val raw = encryptionService.generateToken()
        val hash = sha256(raw)
        val ttlSeconds = serverConfig.getLong(
            ServerConfigService.KEY_SESSION_TIMEOUT,
            config.security.sessionTimeout
        ).coerceAtLeast(MIN_TOKEN_TTL_SECONDS)
        val now = System.currentTimeMillis()
        val expiresAt = now + ttlSeconds * 1000
        tokenRepository.save(TokenEntity().apply {
            tokenHash = hash
            this.username = username
            createdAt = now
            this.expiresAt = expiresAt
        })
        tokenCache[hash] = TokenCacheEntry(username, expiresAt)
        return raw
    }
}
