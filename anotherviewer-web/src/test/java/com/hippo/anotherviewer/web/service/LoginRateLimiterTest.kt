package com.hippo.anotherviewer.web.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 登录限流行为回归：阈值锁定、成功清零、锁定期清理不误删、不同 (user, ip) 独立。
 */
class LoginRateLimiterTest {

    private fun limiter(maxFailures: Int = 5, lockoutMs: Long = 60_000): LoginRateLimiter =
        LoginRateLimiter(maxFailures, lockoutMs, enabled = true)

    @Test
    fun `locks out after threshold failures and clears on success`() {
        val limiter = limiter()
        repeat(5) { limiter.recordFailure("alice", "1.2.3.4") }
        assertTrue(limiter.isLocked("alice", "1.2.3.4"))

        // 锁定窗口内的失败不再累计（幂等）。
        limiter.recordFailure("alice", "1.2.3.4")
        assertTrue(limiter.isLocked("alice", "1.2.3.4"))

        limiter.recordSuccess("alice", "1.2.3.4")
        assertFalse(limiter.isLocked("alice", "1.2.3.4"))
    }

    @Test
    fun `active lockout survives cleanup`() {
        val limiter = limiter()
        repeat(5) { limiter.recordFailure("bob", "5.6.7.8") }
        assertTrue(limiter.isLocked("bob", "5.6.7.8"))

        limiter.cleanup()
        assertTrue(limiter.isLocked("bob", "5.6.7.8"))
    }

    @Test
    fun `buckets are independent per user and ip`() {
        val limiter = limiter()
        repeat(4) { limiter.recordFailure("carol", "9.9.9.9") }

        assertFalse(limiter.isLocked("carol", "9.9.9.9"))
        assertFalse(limiter.isLocked("carol", "8.8.8.8"))
        assertFalse(limiter.isLocked("dave", "9.9.9.9"))
    }
}
