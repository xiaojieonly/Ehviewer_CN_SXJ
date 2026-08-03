package com.hippo.anotherviewer.web.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * 登录限速器：内存计数 + 锁定窗口，按 (用户名, 客户端IP) 维度记录登录失败，防止暴力破解。
 *
 * 语义：
 *  - 同一 (user, ip) 连续失败达到 [maxFailures] 次后进入锁定窗口，窗口内登录直接拒绝；
 *  - 锁定窗口结束后计数清零；再次达到阈值时锁定时长按指数退避翻倍（上限 [MAX_BACKOFF_SHIFT] 次翻倍）；
 *  - 登录成功立即清零该 (user, ip) 的计数与锁定；
 *  - [enabled] = false 时完全旁路，所有方法直接返回、不记录；
 *  - 仅在内存中维护（ConcurrentHashMap），进程重启即失效，无需持久化；
 *    失效条目由 [cleanup] 定时清理，避免长期无人访问的条目驻留。
 *
 * 指数退避：连续第 n 次锁定（n 从 0 计）的窗口时长为 lockoutMs * 2^n（n 最大 3，即 8 倍封顶）。
 */
@Component
class LoginRateLimiter(
    @Value("\${anotherviewer.security.login-max-failures:5}")
    private val maxFailures: Int,
    @Value("\${anotherviewer.security.login-lockout-ms:60000}")
    private val lockoutMs: Long,
    @Value("\${anotherviewer.security.login-rate-limit-enabled:true}")
    private val enabled: Boolean,
) {

    private val attempts = ConcurrentHashMap<String, AttemptState>()

    private class AttemptState {
        var failures: Int = 0
        var lockoutUntil: Long = 0L
        var lockoutCycles: Int = 0
    }

    /** 该 (user, ip) 当前是否处于锁定窗口（窗口已过视为未锁定，条目由后续访问或 [cleanup] 清理）。 */
    fun isLocked(username: String, ip: String): Boolean {
        if (!enabled) return false
        val state = attempts[key(username, ip)] ?: return false
        synchronized(state) {
            return state.lockoutUntil > System.currentTimeMillis()
        }
    }

    /** 记录一次登录失败；达到阈值时进入锁定窗口，锁定时长随连续锁定次数指数翻倍。 */
    fun recordFailure(username: String, ip: String) {
        if (!enabled) return
        val state = attempts.computeIfAbsent(key(username, ip)) { AttemptState() }
        synchronized(state) {
            val now = System.currentTimeMillis()
            if (now < state.lockoutUntil) return // 已在锁定中：被拒绝的请求不再累计计数
            if (state.lockoutUntil != 0L) state.failures = 0 // 锁定窗口已结束，计数重置
            state.failures++
            if (state.failures >= maxFailures.coerceAtLeast(1)) {
                state.lockoutUntil = now + backoffMs(state.lockoutCycles)
                state.lockoutCycles++
                state.failures = 0
            }
        }
    }

    /** 登录成功：清零该 (user, ip) 的全部计数与锁定状态。 */
    fun recordSuccess(username: String, ip: String) {
        if (!enabled) return
        attempts.remove(key(username, ip))
    }

    private fun backoffMs(cycles: Int): Long {
        val multiplier = 1L shl cycles.coerceAtMost(MAX_BACKOFF_SHIFT)
        return lockoutMs * multiplier
    }

    /** 周期清理：无计数、锁定已过期的条目直接移除，防止内存无限增长。 */
    @Scheduled(fixedDelayString = "\${anotherviewer.security.login-rate-limit-cleanup-ms:600000}")
    fun cleanup() {
        val now = System.currentTimeMillis()
        attempts.entries.removeIf { (_, state) ->
            synchronized(state) { state.lockoutUntil <= now && state.failures == 0 }
        }
    }

    private fun key(username: String, ip: String): String = "$username|$ip"

    companion object {
        private const val MAX_BACKOFF_SHIFT = 3
    }
}
