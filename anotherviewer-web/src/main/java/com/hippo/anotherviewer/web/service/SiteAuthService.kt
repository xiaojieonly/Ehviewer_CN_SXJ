package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.AuthResponse
import com.hippo.anotherviewer.web.dto.AuthStatusResponse
import com.hippo.anotherviewer.web.dto.ChangePasswordRequest
import com.hippo.anotherviewer.web.dto.EhSessionCookieDto
import com.hippo.anotherviewer.web.dto.EhSessionResponse
import com.hippo.anotherviewer.web.dto.LoginRequest
import com.hippo.anotherviewer.web.dto.PairCodeResponse
import com.hippo.anotherviewer.web.dto.PairCompleteRequest
import com.hippo.anotherviewer.web.dto.PairCompleteResponse
import com.hippo.anotherviewer.web.dto.RegisterDeviceRequest
import com.hippo.anotherviewer.web.dto.RegisterRequest
import com.hippo.anotherviewer.web.entity.AuthConfigEntity
import com.hippo.anotherviewer.web.entity.SyncDeviceEntity
import com.hippo.anotherviewer.web.entity.TokenEntity
import com.hippo.anotherviewer.web.repository.AuthConfigRepository
import com.hippo.anotherviewer.web.repository.SyncDeviceRepository
import com.hippo.anotherviewer.web.repository.TokenRepository
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

@Service
class SiteAuthService(
    private val authRepository: AuthConfigRepository,
    private val encryptionService: EncryptionService,
    private val sessionManager: SiteSessionManager,
    private val serverConfig: ServerConfigService,
    private val tokenRepository: TokenRepository,
    private val syncDeviceRepository: SyncDeviceRepository,
    private val config: SiteCoreConfigProperties,
) {
    // tokenHash -> cached token metadata. Rebuilt from the DB at startup so
    // tokens survive restarts; every check also validates the TTL.
    private val tokenCache = ConcurrentHashMap<String, TokenCacheEntry>()
    // Pairing codes are purely in-memory (single-use, 10-min TTL). Growth is
    // bounded: every generate/complete prunes expired entries first, so the map
    // holds at most the codes issued within one TTL window (a handful per user).
    private val pairCodes = ConcurrentHashMap<String, PairCodeEntry>()

    private class TokenCacheEntry(val username: String, val expiresAt: Long)

    private class PairCodeEntry(val username: String, val expiresAt: Long)

    companion object {
        const val PAIR_CODE_TTL_SECONDS = 600L
        const val PAIR_CODE_LENGTH = 6
        const val MIN_TOKEN_TTL_SECONDS = 60L
        const val MSG_SETUP_KEY_REQUIRED = "Setup key required"
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

    fun isAuthEnabled(): Boolean =
        serverConfig.getBoolean(ServerConfigService.KEY_REQUIRE_AUTH, false)

    /** Registration follows the auth mode: allowed whenever require_auth is on. */
    fun isRegistrationAllowed(): Boolean = isAuthEnabled()

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
     * Re-hashes the account password after verifying the caller knows the old
     * one. Only the hash is updated: tokens issued to other sessions stay valid.
     * With auth disabled the caller cannot be identified (the filter stamps a
     * generic principal), so changing a password is refused outright.
     */
    fun changePassword(username: String, request: ChangePasswordRequest): AuthResponse {
        if (!isAuthEnabled()) {
            return AuthResponse(false, "Authentication is disabled on this server")
        }
        if (request.oldPassword.isBlank() || request.newPassword.isBlank()) {
            return AuthResponse(false, "Old password and new password are required")
        }
        if (request.newPassword.length < 6) {
            return AuthResponse(false, "New password must be at least 6 characters")
        }
        if (request.newPassword.length > 72) {
            return AuthResponse(false, "New password must be at most 72 characters")
        }
        val entity = authRepository.findByUsername(username)
            ?: return AuthResponse(false, "Account not found")
        if (!encryptionService.verifyPassword(request.oldPassword, entity.passwordHash)) {
            return AuthResponse(false, "Old password is incorrect")
        }
        entity.passwordHash = encryptionService.hashPassword(request.newPassword)
        authRepository.save(entity)
        return AuthResponse(true, "Password changed")
    }

    /**
     * Establish an Gallery Site session through the unified cookie store shared with
     * anotherviewer-core. On success the login cookies live in [SiteSessionManager.cookieStore]
     * and are reused by core's HTTP client.
     */
    fun loginEh(username: String, password: String): AuthResponse {
        return sessionManager.signIn(username, password).fold(
            onSuccess = { displayName ->
                AuthResponse(true, "Gallery Site login successful", username = displayName ?: username)
            },
            onFailure = { err ->
                AuthResponse(false, "Gallery Site login failed: ${err.message}")
            }
        )
    }

    /**
     * Tear down only the Gallery Site session. Unlike [logout], the WebUI token is
     * left untouched: the caller stays authenticated to the server, just not to EH.
     */
    fun logoutEh() {
        sessionManager.signOut()
    }

    /**
     * Snapshot of the Gallery Site session: whether the identity cookies are present
     * and expired, plus the identity cookies themselves (ipb_member_id / ipb_pass_hash
     * and igneous when present) so clients can mirror the session or detect a lapsed login.
     */
    fun ehSession(): EhSessionResponse {
        val status = sessionManager.getStatus()
        val gallerySite = sessionManager.getGallerySite()
        if (!status.signedIn) {
            return EhSessionResponse(
                signedIn = false,
                expired = false,
                cookies = emptyList(),
                gallerySite = gallerySite,
            )
        }
        val identityNames = setOf(
            SiteSessionManager.KEY_IPB_MEMBER_ID,
            SiteSessionManager.KEY_IPB_PASS_HASH,
            SiteSessionManager.KEY_IGNEOUS,
        )
        val cookies = sessionManager.cookieStore.getAll().values.flatten()
            .filter { it.name in identityNames }
            .map {
                EhSessionCookieDto(
                    name = it.name,
                    value = it.value,
                    domain = it.domain,
                    expiresAt = it.expiresAt,
                )
            }
        return EhSessionResponse(
            signedIn = status.signedIn,
            expired = status.expired,
            cookies = cookies,
            gallerySite = gallerySite,
        )
    }

    /** Switch the Gallery Site (0 = e-hentai, 1 = exhentai); false when the value is invalid. */
    fun setGallerySite(site: Int): Boolean = sessionManager.setGallerySite(site)

    fun getStatus(token: String?): AuthStatusResponse {
        // Gallery Site session state is reported regardless of the WebUI token so clients
        // can detect an expired Gallery Site login and prompt for re-login.
        val ehStatus = sessionManager.getStatus()
        val username = validateToken(token)
        val authRequired = serverConfig.getBoolean(ServerConfigService.KEY_REQUIRE_AUTH, false)
        return AuthStatusResponse(
            authenticated = username != null,
            username = username,
            authRequired = authRequired,
            ehSessionValid = ehStatus.state == SiteSessionManager.SessionState.VALID,
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
        // Also tear down the shared Gallery Site session so core's client stops sending
        // the (now unwanted) login cookies.
        sessionManager.signOut()
    }

    // -------------------------------------------------------------------------
    // Device pairing
    // -------------------------------------------------------------------------

    /**
     * Removes pairing codes whose TTL has elapsed. Run at the start of every
     * [generatePairCode]/[completePairing] instead of a scheduled task: a
     * periodic sweep would only run on an idle server anyway, and pruning on
     * access already bounds the map to the codes issued within one TTL window,
     * which is small enough to keep the implementation simple. [now] is
     * injectable so tests can drive the clock.
     */
    internal fun pruneExpiredPairCodes(now: Long = System.currentTimeMillis()) {
        pairCodes.entries.removeIf { it.value.expiresAt < now }
    }

    /** Number of live pairing codes; exposed for tests. */
    internal fun activePairCodeCount(): Int = pairCodes.size

    /** Generates a short-lived single-use pairing code for the given username. */
    fun generatePairCode(username: String, ttlSeconds: Long = PAIR_CODE_TTL_SECONDS): PairCodeResponse {
        pruneExpiredPairCodes()
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
        pruneExpiredPairCodes()
        val entry = pairCodes.remove(request.code)
        if (entry == null || entry.expiresAt < System.currentTimeMillis()) {
            return PairCompleteResponse(false, "Pairing code is invalid or expired")
        }
        return registerDeviceToken(entry.username, request.deviceId, request.deviceName, request.platform)
    }

    /** Whether clients may auto-register devices without a pairing code. */
    fun isAutoPairingEnabled(): Boolean =
        serverConfig.getBoolean(ServerConfigService.KEY_AUTO_PAIRING, true)

    /** Static setup key gate for auto-pairing; empty means none is required. */
    fun getRequiredSetupKey(): String = serverConfig.get(ServerConfigService.KEY_SETUP_KEY)

    /**
     * Auto-pairs a device without a code (LAN single-user mode). Gated by
     * [isAutoPairingEnabled]; when a setup key is configured it must be
     * supplied and match (compared in constant time). The device is bound to
     * [username] — the anonymous principal ("default") when auth is off.
     */
    fun registerDevice(username: String, request: RegisterDeviceRequest): PairCompleteResponse {
        if (!isAutoPairingEnabled()) {
            return PairCompleteResponse(false, "Auto-pairing disabled on this server")
        }
        // auth-on 时该端点 permitAll，无 token 的匿名请求也会走到这里（identity 为
        // Spring 的 "anonymousUser"）；若直接签发 token 等于绕过登录门。只有配置了
        // setup key（作为该场景的共享密钥）才允许继续；未配置 → 拒绝 400，客户端
        // 按契约（openapi.yaml register-device）回退 /pair/complete。
        if (isAuthEnabled() && getRequiredSetupKey().isEmpty()) {
            return PairCompleteResponse(false, "Auto-pairing requires a setup key when auth is enabled")
        }
        val requiredKey = getRequiredSetupKey()
        if (requiredKey.isNotEmpty()) {
            val provided = request.setupKey ?: return PairCompleteResponse(false, MSG_SETUP_KEY_REQUIRED)
            if (!MessageDigest.isEqual(
                    requiredKey.toByteArray(Charsets.UTF_8),
                    provided.toByteArray(Charsets.UTF_8)
                )
            ) {
                return PairCompleteResponse(false, "Invalid setup key")
            }
        }
        return registerDeviceToken(username, request.deviceId, request.deviceName, request.platform)
    }

    /** Shared device-binding tail for both pairing flows (code + auto). */
    private fun registerDeviceToken(
        username: String,
        deviceId: String,
        deviceName: String,
        platform: String,
    ): PairCompleteResponse {
        val token = issueToken(username)
        val device = syncDeviceRepository.findByDeviceId(deviceId)
            ?: SyncDeviceEntity().apply { this.deviceId = deviceId }
        // Rotate credentials: a re-registering device's previous token is
        // revoked so a stale token cannot outlive re-registration (same
        // policy as revokeDevice).
        device.token?.let { oldHash ->
            tokenRepository.deleteByTokenHash(oldHash)
            tokenCache.remove(oldHash)
        }
        device.deviceName = deviceName
        device.platform = platform
        device.pairedAt = System.currentTimeMillis()
        device.lastSeen = device.pairedAt
        device.token = sha256(token)
        device.username = username
        syncDeviceRepository.save(device)
        return PairCompleteResponse(true, "Pairing successful", token, username)
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
