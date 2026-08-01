package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.network.EhCookieStore
import okhttp3.Cookie
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Unified E-Hentai cookie / session manager shared between the web backend and
 * ehviewer-core's HTTP client.
 *
 * It owns a single [EhCookieStore] (the in-memory [okhttp3.CookieJar] from
 * ehviewer-core) and an [OkHttpClient] wired to that store. Any code that needs
 * to talk to E-Hentai — web services or core [EhEngine] calls — should use
 * [okHttpClient] so that login cookies are shared across the whole process.
 *
 * Beyond sharing the store, this component is responsible for:
 *  - establishing / tearing down the E-Hentai session ([signIn] / [signOut]);
 *  - detecting when the login cookies have expired ([isExpired] / [getStatus]);
 *  - periodically re-checking session health ([healthCheck]).
 */
@Component
class EhSessionManager(private val proxyManager: WebProxyManager) {

    private val logger = LoggerFactory.getLogger(EhSessionManager::class.java)

    /** The single cookie store shared with ehviewer-core. */
    val cookieStore: EhCookieStore = EhCookieStore()

    /**
     * OkHttp client whose cookie jar is [cookieStore]; use for all E-Hentai traffic.
     * The proxy selector/authenticator are consulted per connection, so the admin
     * proxy settings ([WebProxyManager]) take effect immediately without a rebuild.
     */
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieStore)
        .proxySelector(proxyManager.selector())
        .proxyAuthenticator(proxyManager.authenticator())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val lastValidatedAt = AtomicLong(0L)
    private val lastState = AtomicReference(SessionState.SIGNED_OUT)

    /** Coarse lifecycle state of the E-Hentai session. */
    enum class SessionState { SIGNED_OUT, VALID, EXPIRED }

    /** Snapshot of the current E-Hentai session state. */
    data class SessionStatus(
        val state: SessionState,
        val signedIn: Boolean,
        val expired: Boolean,
        val lastValidatedAt: Long,
    )

    /**
     * Sign in to E-Hentai using [username] / [password]. The response cookies are
     * stored automatically into the shared [cookieStore] by OkHttp. Returns the
     * display name parsed from the sign-in response on success.
     */
    fun signIn(username: String, password: String): Result<String> = runCatching {
        val displayName = EhEngine.signIn(null, okHttpClient, username, password)
        lastValidatedAt.set(System.currentTimeMillis())
        lastState.set(SessionState.VALID)
        logger.info("E-Hentai sign-in succeeded")
        displayName
    }.onFailure {
        logger.warn("E-Hentai sign-in failed: {}", it.message)
    }

    /** Clear all shared cookies, signing out of E-Hentai. */
    fun signOut() {
        cookieStore.clear()
        lastValidatedAt.set(0L)
        lastState.set(SessionState.SIGNED_OUT)
        logger.info("E-Hentai session cleared")
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
     * Guard for protected operations: throws [EhSessionExpiredException] when the
     * E-Hentai session has expired so callers surface a 401 prompting re-login.
     * A signed-out (never logged in) state does not throw.
     */
    fun requireValidSession() {
        if (getStatus().state == SessionState.EXPIRED) {
            throw EhSessionExpiredException()
        }
    }

    /**
     * Periodic cookie health check. Re-evaluates the session state and logs a
     * warning when the login has expired so operators know a re-login is needed.
     */
    @Scheduled(fixedDelayString = "\${ehviewer.web.session.health-check-interval-ms:300000}")
    fun healthCheck() {
        when (getStatus().state) {
            SessionState.EXPIRED ->
                logger.warn("E-Hentai session expired; re-login required")
            SessionState.VALID ->
                logger.debug("E-Hentai session healthy")
            SessionState.SIGNED_OUT ->
                logger.debug("No active E-Hentai session")
        }
    }

    companion object {
        const val KEY_IPB_MEMBER_ID = "ipb_member_id"
        const val KEY_IPB_PASS_HASH = "ipb_pass_hash"
        const val KEY_IGNEOUS = "igneous"
    }
}
