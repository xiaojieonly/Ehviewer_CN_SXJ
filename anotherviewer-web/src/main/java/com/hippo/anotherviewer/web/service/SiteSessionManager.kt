package com.hippo.anotherviewer.web.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.hippo.anotherviewer.Settings
import com.hippo.anotherviewer.client.SiteEngine
import com.hippo.anotherviewer.network.SiteCookieStore
import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.SyncEhSessionCookieDto
import com.hippo.anotherviewer.web.dto.SyncEhSessionDto
import com.hippo.anotherviewer.web.entity.EhSessionEntity
import com.hippo.anotherviewer.web.repository.EhSessionRepository
import jakarta.annotation.PostConstruct
import okhttp3.Cookie
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Unified Gallery Site cookie / session manager shared between the web backend and
 * anotherviewer-core's HTTP client.
 *
 * It owns a single [SiteCookieStore] (the in-memory [okhttp3.CookieJar] from
 * anotherviewer-core) and an [OkHttpClient] wired to that store. Any code that needs
 * to talk to Gallery Site — web services or core [SiteEngine] calls — should use
 * [okHttpClient] so that login cookies are shared across the whole process.
 *
 * Beyond sharing the store, this component is responsible for:
 *  - establishing / tearing down the Gallery Site session ([signIn] / [signOut]);
 *  - detecting when the login cookies have expired ([isExpired] / [getStatus]);
 *  - periodically re-checking session health ([healthCheck]);
 *  - persisting the session across restarts (ADR-0004): the full cookie set is
 *    saved as an encrypted JSON blob (enc:v1: + security.key) in [EhSessionEntity]
 *    on login / cookie import, restored at startup, and exposed to sync
 *    ([loadSyncEhSession] / [applySyncEhSession]).
 */
@Component
class SiteSessionManager(
    private val proxyManager: WebProxyManager,
    private val serverConfig: ServerConfigService,
    private val ehSessionRepository: EhSessionRepository,
    private val encryptionService: EncryptionService,
    private val config: SiteCoreConfigProperties,
) {

    private val logger = LoggerFactory.getLogger(SiteSessionManager::class.java)

    private val mapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    /** The single cookie store shared with anotherviewer-core. */
    val cookieStore: SiteCookieStore = SiteCookieStore()

    /**
     * OkHttp client whose cookie jar is [cookieStore]; use for all Gallery Site traffic.
     * The proxy selector/authenticator are consulted per connection, so the admin
     * proxy settings ([WebProxyManager]) take effect immediately without a rebuild.
     */
    val okHttpClient: OkHttpClient = run {
        OkHttpClient.Builder()
            .cookieJar(cookieStore)
            .proxySelector(proxyManager.selector())
            .proxyAuthenticator(proxyManager.authenticator())
            // Site traffic runs through the system curl binary: Cloudflare
            // fingerprints the TLS ClientHello (JA3) and rejects the Java
            // handshake on the strict exhentai paths, while the system curl
            // fingerprint is admitted (see CurlSiteExecutor). Application
            // interceptor: it manages cookies itself, so it must sit before
            // OkHttp's BridgeInterceptor (which would otherwise override them).
            .addInterceptor(CurlSiteExecutor(cookieStore, proxy = { proxyManager.activeProxy() }))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val lastValidatedAt = AtomicLong(0L)
    private val lastState = AtomicReference(SessionState.SIGNED_OUT)

    /**
     * Restore the persisted Gallery Site selection ([ServerConfigService.KEY_SITE_GALLERY])
     * into anotherviewer-core's [Settings] so [com.hippo.anotherviewer.client.SiteUrl]
     * picks the configured host across restarts, then restore the persisted session
     * (ADR-0004 D4) into the shared cookie store. Safe to call repeatedly.
     */
    @PostConstruct
    fun initGallerySite() {
        loadGallerySite()
        restorePersistedSession()
    }

    internal fun loadGallerySite() {
        val stored = serverConfig.get(ServerConfigService.KEY_SITE_GALLERY, Settings.SITE_E.toString())
            .toIntOrNull() ?: Settings.SITE_E
        Settings.setGallerySite(if (stored == Settings.SITE_EX) Settings.SITE_EX else Settings.SITE_E)
    }

    /** The currently selected Gallery Site, as consumed by SiteUrl host picking. */
    fun getGallerySite(): Int = Settings.getGallerySite()

    /**
     * Switch the Gallery Site (0 = e-hentai, 1 = exhentai): persists the choice and
     * applies it to core's [Settings] immediately. Returns false for invalid values.
     */
    fun setGallerySite(site: Int): Boolean {
        if (site != Settings.SITE_E && site != Settings.SITE_EX) return false
        serverConfig.set(ServerConfigService.KEY_SITE_GALLERY, site.toString())
        Settings.setGallerySite(site)
        logger.info("Gallery Site switched to {}", site)
        return true
    }

    /** Coarse lifecycle state of the Gallery Site session. */
    enum class SessionState { SIGNED_OUT, VALID, EXPIRED }

    /** Snapshot of the current Gallery Site session state. */
    data class SessionStatus(
        val state: SessionState,
        val signedIn: Boolean,
        val expired: Boolean,
        val lastValidatedAt: Long,
    )

    /**
     * Sign in to Gallery Site using [username] / [password]. The response cookies are
     * stored automatically into the shared [cookieStore] by OkHttp. Returns the
     * display name parsed from the sign-in response on success.
     */
    fun signIn(username: String, password: String): Result<String> = runCatching {
        val displayName = SiteEngine.signIn(null, okHttpClient, username, password)
        lastValidatedAt.set(System.currentTimeMillis())
        lastState.set(SessionState.VALID)
        logger.info("Gallery Site sign-in succeeded")
        saveSessionSnapshot(displayName)
        displayName
    }.onFailure {
        logger.warn("Gallery Site sign-in failed: {}", it.message)
    }

    /**
     * Clear all shared cookies, signing out of Gallery Site. When a session was
     * active, writes a tombstone row (deleted=true + bumped lastModified) so the
     * logout propagates to synced devices (ADR-0004 D3).
     */
    fun signOut() {
        val hadSession = hasSignedIn() || ehSessionRepository.findByUsername(EH_SESSION_OWNER)?.deleted == false
        cookieStore.clear()
        lastValidatedAt.set(0L)
        lastState.set(SessionState.SIGNED_OUT)
        if (hadSession) writeTombstone()
        logger.info("Gallery Site session cleared")
    }

    // ---------------------------------------------------------------------
    // ADR-0004: persisted-session bridge (encrypted at rest + sync accessors)
    // ---------------------------------------------------------------------

    /**
     * Persist the current [cookieStore] contents (site-domain cookies only) as the
     * ehSession snapshot: cookies JSON is encrypted with enc:v1: + security.key and
     * written to the fixed singleton row ([EH_SESSION_OWNER]). Called after a
     * successful sign-in and after cookie import. Cookie values never hit the log.
     */
    fun saveSessionSnapshot(displayName: String? = null) {
        val records = cookieStore.getAll().values.flatten()
            .filter { isSiteDomain(it.domain) }
            .map { it.toCookieRecord() }
        val entity = ehSessionRepository.findByUsername(EH_SESSION_OWNER) ?: EhSessionEntity()
        entity.apply {
            username = EH_SESSION_OWNER
            cookies = encryptCookies(records)
            this.displayName = displayName ?: this.displayName
            gallerySite = getGallerySite()
            lastModified = System.currentTimeMillis()
            deleted = false
            updatedBy = SERVER_DEVICE_ID
        }
        ehSessionRepository.save(entity)
        logger.info("Persisted Gallery Site session snapshot ({} cookies)", records.size)
    }

    /**
     * Startup restore (ADR-0004 D4): load the live persisted session, decrypt and
     * rehydrate the in-memory cookie store. Bad / undecryptable rows are skipped
     * rather than failing startup.
     */
    @PostConstruct
    fun restorePersistedSession() {
        val entity = ehSessionRepository.findByUsername(EH_SESSION_OWNER) ?: return
        if (entity.deleted) return
        val records = decryptCookies(entity.cookies)
            .filterNot { isCloudflareClearance(it.name) }
        records.forEach { rec ->
            rec.toOkHttpCookie()?.let { cookieStore.addCookie(it) }
        }
        if (records.isNotEmpty()) {
            lastValidatedAt.set(System.currentTimeMillis())
            logger.info("Restored persisted Gallery Site session ({} cookie records)", records.size)
        }
    }

    /**
     * Current persisted session as a sync DTO (decrypted cookie values on the wire),
     * or null when no row exists. A tombstone row is returned as-is so pull can
     * propagate the logout to other devices.
     */
    fun loadSyncEhSession(): SyncEhSessionDto? {
        val entity = ehSessionRepository.findByUsername(EH_SESSION_OWNER) ?: return null
        return entity.toSyncEhSessionDto()
    }

    /**
     * Apply an incoming sync ehSession (ADR-0004): LWW merge (±5s skew, independent
     * of the sync conflict strategy), tombstone propagation for deleted=true, then
     * materialize the winning session into the in-memory cookie store. Returns true
     * when the incoming record resolved an existing row (conflict counter).
     */
    fun applySyncEhSession(incoming: SyncEhSessionDto, pushDeviceId: String): Boolean {
        val incomingDevice = incoming.deviceId.ifEmpty { pushDeviceId }
        val existing = ehSessionRepository.findByUsername(EH_SESSION_OWNER)

        if (incoming.deleted) {
            // D3: tombstone 无条件传播（对齐 §3.8 history/bookmark），bump lastModified 供增量 pull。
            if (existing != null) {
                existing.deleted = true
                existing.lastModified = maxOf(existing.lastModified, incoming.lastModified)
                existing.updatedBy = incomingDevice
                ehSessionRepository.save(existing)
            } else {
                ehSessionRepository.save(EhSessionEntity().apply {
                    username = EH_SESSION_OWNER
                    lastModified = incoming.lastModified
                    deleted = true
                    updatedBy = incomingDevice
                })
            }
            cookieStore.clear()
            return false
        }

        // D2: LWW ± skew，skew 内 last-received-wins；A/C 平台序不适用。
        if (existing != null && existing.lastModified > incoming.lastModified + SYNC_SKEW_TOLERANCE) return false

        val entity = existing ?: EhSessionEntity()
        entity.apply {
            username = EH_SESSION_OWNER
            cookies = encryptCookies(incoming.cookies.orEmpty().map { it.toCookieRecord() })
            displayName = incoming.displayName
            avatar = incoming.avatar
            // 同步回显与站点生效都需落地：entity 字段供 pull 回显，setGallerySite 持久化 KV + 生效到 Settings。
            incoming.gallerySite?.takeIf { it == Settings.SITE_E || it == Settings.SITE_EX }?.let {
                gallerySite = it
                setGallerySite(it)
            }
            lastModified = incoming.lastModified
            deleted = false
            updatedBy = incomingDevice
        }
        ehSessionRepository.save(entity)
        materializeCookies(incoming)
        return existing != null
    }

    private fun materializeCookies(session: SyncEhSessionDto) {
        cookieStore.clear()
        session.cookies.orEmpty()
            .filter { isSiteDomain(it.domain) }
            // cf_clearance binds to the UA + IP that solved the challenge. On
            // the server that UA/IP combination never matches (the app's
            // WebView solved it), and Cloudflare rejects requests carrying an
            // invalid cf_clearance — worse than not carrying one at all, since
            // a clean proxy egress (or direct IP) is otherwise admitted.
            // The server's own browser-fingerprint + egress handles any
            // challenge-free traffic, so drop the cookie entirely here.
            .filterNot { isCloudflareClearance(it.name) }
            .mapNotNull { it.toCookieRecord().toOkHttpCookie() }
            .forEach { cookieStore.addCookie(it) }
    }

    /** Whether this persisted cookie record is a Cloudflare clearance cookie. */
    private fun isCloudflareClearance(name: String): Boolean =
        name.equals(CF_CLEARANCE_NAME, ignoreCase = true)

    private fun writeTombstone() {
        val entity = ehSessionRepository.findByUsername(EH_SESSION_OWNER) ?: EhSessionEntity()
        entity.apply {
            username = EH_SESSION_OWNER
            deleted = true
            lastModified = System.currentTimeMillis()
            updatedBy = SERVER_DEVICE_ID
        }
        ehSessionRepository.save(entity)
    }

    private fun EhSessionEntity.toSyncEhSessionDto(): SyncEhSessionDto {
        val records = if (deleted) emptyList() else decryptCookies(cookies)
        return SyncEhSessionDto(
            cookies = records.map { it.toSyncCookieDto() },
            displayName = displayName,
            avatar = avatar,
            gallerySite = gallerySite,
            lastModified = lastModified,
            deviceId = updatedBy ?: SERVER_DEVICE_ID,
            deleted = deleted,
        )
    }

    private fun encryptCookies(records: List<CookieRecord>): String =
        ENC_PREFIX + encryptionService.encrypt(mapper.writeValueAsString(records), encryptionKey())

    private fun decryptCookies(stored: String): List<CookieRecord> {
        if (!stored.startsWith(ENC_PREFIX)) return emptyList()
        return runCatching {
            mapper.readValue<List<CookieRecord>>(encryptionService.decrypt(stored.removePrefix(ENC_PREFIX), encryptionKey()))
        }.getOrElse { e ->
            logger.warn("EhSession 持久化 cookie 解密/解析失败，跳过恢复: {}", e.message)
            emptyList()
        }
    }

    private fun encryptionKey(): String {
        val file = File(config.security.encryptionKeyPath)
        if (file.exists()) return file.readText().trim()
        val key = encryptionService.generateToken()
        file.parentFile?.mkdirs()
        file.writeText(key)
        return key
    }

    // ---------------------------------------------------------------------
    // Cookie <-> wire DTO mapping
    // ---------------------------------------------------------------------

    /** 落库序列化单元：镜像 okhttp3.Cookie 全字段（含 igneous 等非 identity cookie）。 */
    internal data class CookieRecord(
        val name: String,
        val value: String,
        val domain: String,
        val path: String = "/",
        val expiresAt: Long = 0,
        val secure: Boolean = false,
        val httpOnly: Boolean = false,
        val persistent: Boolean = false,
        val hostOnly: Boolean = false,
    )

    private fun Cookie.toCookieRecord() = CookieRecord(
        name = name,
        value = value,
        domain = domain,
        path = path,
        expiresAt = expiresAt,
        secure = secure,
        httpOnly = httpOnly,
        persistent = persistent,
        hostOnly = hostOnly,
    )

    private fun CookieRecord.toSyncCookieDto() = SyncEhSessionCookieDto(
        name = name,
        value = value,
        domain = domain,
        path = path,
        expiresAt = expiresAt,
        secure = secure,
        httpOnly = httpOnly,
        persistent = persistent,
        hostOnly = hostOnly,
    )

    private fun SyncEhSessionCookieDto.toCookieRecord() = CookieRecord(
        name = name,
        value = value,
        domain = domain,
        path = path,
        expiresAt = expiresAt,
        secure = secure,
        httpOnly = httpOnly,
        persistent = persistent,
        hostOnly = hostOnly,
    )

    private fun CookieRecord.toOkHttpCookie(): Cookie? = try {
        val expiresAt = if (this.expiresAt > 0) this.expiresAt else Long.MAX_VALUE
        val builder = Cookie.Builder()
            .name(name)
            .value(value)
            .expiresAt(expiresAt)
            .path(path.ifBlank { "/" }.let { if (it.startsWith("/")) it else "/$it" })
        val domain = normalizeDomain(this.domain)
        if (hostOnly) builder.hostOnlyDomain(domain) else builder.domain(domain)
        if (secure) builder.secure()
        if (httpOnly) builder.httpOnly()
        builder.build()
    } catch (e: Exception) {
        logger.warn("忽略无法恢复的 cookie {}@{}: {}", name, domain, e.message)
        null
    }

    private fun isSiteDomain(domain: String): Boolean {
        val d = normalizeDomain(domain)
        if (d.isEmpty()) return false
        return SITE_DOMAINS.any { it == d || d.endsWith(".$it") }
    }

    private fun normalizeDomain(domain: String): String =
        domain.trim().lowercase().removePrefix(".").trimEnd('.')

    /** All identity cookies (member id / pass hash) currently held, across hosts. */
    fun identityCookies(): List<Cookie> = cookieStore.getAll().values.flatten()
        .filter { it.name == KEY_IPB_MEMBER_ID || it.name == KEY_IPB_PASS_HASH }

    /** True when both identity cookies are present (i.e. a login has happened). */
    fun hasSignedIn(): Boolean {
        val names = identityCookies().map { it.name }.toSet()
        return KEY_IPB_MEMBER_ID in names && KEY_IPB_PASS_HASH in names
    }

    /**
     * True when identity cookies exist but at least one persistent identity cookie
     * is past its expiry time — i.e. the login was valid once but has since lapsed.
     */
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
        val identity = identityCookies()
        if (identity.isEmpty()) return false
        return identity.any { it.persistent && it.expiresAt < now }
    }

    /** Compute and cache the current [SessionStatus]. Cheap — no network I/O. */
    fun getStatus(): SessionStatus {
        val signedIn = hasSignedIn()
        val expired = signedIn && isExpired()
        val state = when {
            !signedIn -> SessionState.SIGNED_OUT
            expired -> SessionState.EXPIRED
            else -> SessionState.VALID
        }
        lastState.set(state)
        return SessionStatus(
            state = state,
            signedIn = signedIn,
            expired = expired,
            lastValidatedAt = lastValidatedAt.get(),
        )
    }

    /**
     * Guard for protected operations: throws [SiteSessionExpiredException] when the
     * Gallery Site session has expired so callers surface a 401 prompting re-login.
     * A signed-out (never logged in) state does not throw.
     */
    fun requireValidSession() {
        if (getStatus().state == SessionState.EXPIRED) {
            throw SiteSessionExpiredException()
        }
    }

    /**
     * Periodic cookie health check. Re-evaluates the session state and logs a
     * warning when the login has expired so operators know a re-login is needed.
     */
    @Scheduled(fixedDelayString = "\${anotherviewer.web.session.health-check-interval-ms:300000}")
    fun healthCheck() {
        when (getStatus().state) {
            SessionState.EXPIRED ->
                logger.warn("Gallery Site session expired; re-login required")
            SessionState.VALID ->
                logger.debug("Gallery Site session healthy")
            SessionState.SIGNED_OUT ->
                logger.debug("No active Gallery Site session")
        }
    }

    companion object {
        const val KEY_IPB_MEMBER_ID = "ipb_member_id"
        const val KEY_IPB_PASS_HASH = "ipb_pass_hash"
        const val KEY_IGNEOUS = "igneous"

        /** Cloudflare challenge cookie; bound to the UA+IP that solved it (see materializeCookies). */
        const val CF_CLEARANCE_NAME = "cf_clearance"

        /** ehSession 单例行的固定属主（服务器级共享）；与 provenance 缺省 deviceId 一致。 */
        const val EH_SESSION_OWNER = "server"

        /** 服务器本机写入会话（WebUI 登录/登出/导入）的行级来源 deviceId。 */
        const val SERVER_DEVICE_ID = "server"

        const val ENC_PREFIX = "enc:v1:"

        private const val SYNC_SKEW_TOLERANCE = 5000L

        private val SITE_DOMAINS = setOf("e-hentai.org", "exhentai.org", "ehgt.org", "forums.e-hentai.org")
    }
}
