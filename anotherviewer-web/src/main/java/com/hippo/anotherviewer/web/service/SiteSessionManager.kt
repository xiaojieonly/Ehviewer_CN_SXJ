package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.client.SiteEngine
import com.hippo.anotherviewer.network.SiteCookieStore
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
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
 *  - periodically re-checking session health ([healthCheck]).
 */
@Component
class SiteSessionManager(
    private val proxyManager: WebProxyManager,
    @Value("\${anotherviewer.gallery.mock-base-url:}") private val mockBaseUrl: String = "",
) {

    private val logger = LoggerFactory.getLogger(SiteSessionManager::class.java)

    /** The single cookie store shared with anotherviewer-core. */
    val cookieStore: SiteCookieStore = SiteCookieStore()

    /**
     * OkHttp client whose cookie jar is [cookieStore]; use for all Gallery Site traffic.
     * The proxy selector/authenticator are consulted per connection, so the admin
     * proxy settings ([WebProxyManager]) take effect immediately without a rebuild.
     *
     * Dev/test only: when `anotherviewer.gallery.mock-base-url` is set (default
     * empty = off), requests to the neutralized `gallery.test` host are rewritten
     * to that base (e.g. `http://127.0.0.1:4100`) — path/query and existing
     * headers (Referer etc.) are preserved, so the mock corpus drives the full
     * WebUI E2E without touching production behavior.
     */
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieStore)
        .proxySelector(proxyManager.selector())
        .proxyAuthenticator(proxyManager.authenticator())
        .addInterceptor(mockRewriteInterceptor())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun mockRewriteInterceptor() = Interceptor { chain ->
        val original = chain.request()
        val host = original.url().host()
        val isMockHost = host == "gallery.test" || host.endsWith(".gallery.test")
        if (mockBaseUrl.isBlank() || !isMockHost) {
            chain.proceed(original)
        } else {
            val base = HttpUrl.parse(mockBaseUrl) ?: return@Interceptor chain.proceed(original)
            val rewritten = original.url().newBuilder()
                .scheme(base.scheme())
                .host(base.host())
                .port(base.port())
                .build()
            chain.proceed(original.newBuilder().url(rewritten).build())
        }
    }

    private val lastValidatedAt = AtomicLong(0L)
    private val lastState = AtomicReference(SessionState.SIGNED_OUT)

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
        displayName
    }.onFailure {
        logger.warn("Gallery Site sign-in failed: {}", it.message)
    }

    /** Clear all shared cookies, signing out of Gallery Site. */
    fun signOut() {
        cookieStore.clear()
        lastValidatedAt.set(0L)
        lastState.set(SessionState.SIGNED_OUT)
        logger.info("Gallery Site session cleared")
    }

    /** All identity cookies (member id / pass hash) currently held, across hosts. */
    fun identityCookies(): List<Cookie> = cookieStore.getAll().values.flatten()
        .filter { it.name() == KEY_IPB_MEMBER_ID || it.name() == KEY_IPB_PASS_HASH }

    /** True when both identity cookies are present (i.e. a login has happened). */
    fun hasSignedIn(): Boolean {
        val names = identityCookies().map { it.name() }.toSet()
        return KEY_IPB_MEMBER_ID in names && KEY_IPB_PASS_HASH in names
    }

    /**
     * True when identity cookies exist but at least one persistent identity cookie
     * is past its expiry time — i.e. the login was valid once but has since lapsed.
     */
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
        val identity = identityCookies()
        if (identity.isEmpty()) return false
        return identity.any { it.persistent() && it.expiresAt() < now }
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
    }
}
