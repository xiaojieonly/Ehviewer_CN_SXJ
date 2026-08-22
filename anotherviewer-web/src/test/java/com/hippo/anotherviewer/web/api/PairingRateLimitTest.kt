package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.dto.PairCompleteRequest
import com.hippo.anotherviewer.web.dto.PairCompleteResponse
import com.hippo.anotherviewer.web.dto.RegisterDeviceRequest
import com.hippo.anotherviewer.web.service.LoginRateLimiter
import com.hippo.anotherviewer.web.service.SiteAuthService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * MASTER-2026-08-22 S1：配对凭证端点的暴力破解限流。
 *
 * - POST /api/v1/auth/pair/complete（6 位一次性码）与
 *   POST /api/v1/auth/register-device（setup key 共享密钥）在 auth-on 下均为
 *   permitAll，无失败限流时可被穷举。复用登录限流的 (key, ip) 分桶：
 *   同 IP 连续失败达阈值后进入锁定窗口，窗口内直接 429；成功配对清零。
 */
class PairingRateLimitTest {

    private lateinit var authService: SiteAuthService

    @BeforeEach
    fun setUp() {
        authService = mock(SiteAuthService::class.java)
    }

    private fun mockMvc(limiter: LoginRateLimiter): MockMvc =
        MockMvcBuilders.standaloneSetup(AuthController(authService, limiter)).build()

    @Test
    fun `pair complete locks out with 429 after threshold consecutive failures`() {
        val failing = (0..4).map { PairCompleteRequest(code = "${100000 + it}", deviceId = "d$it", deviceName = "n") }
        val locked = PairCompleteRequest(code = "999999", deviceId = "dx", deviceName = "nx")
        for (req in failing + locked) {
            `when`(authService.completePairing(req))
                .thenReturn(PairCompleteResponse(false, "Pairing code is invalid or expired"))
        }
        val mvc = mockMvc(LoginRateLimiter(5, 60_000, true))

        failing.forEachIndexed { i, req ->
            mvc.perform(post("/api/v1/auth/pair/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"${req.code}","deviceId":"${req.deviceId}","deviceName":"n"}"""))
                .andExpect(status().isBadRequest)
        }
        mvc.perform(post("/api/v1/auth/pair/complete")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"code":"999999","deviceId":"dx","deviceName":"nx"}"""))
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    fun `register device wrong setup key locks out with 429 after threshold failures`() {
        val failing = (0..4).map {
            RegisterDeviceRequest(deviceId = "dev$it", deviceName = "n$it", setupKey = "wrong$it")
        }
        val lockedReq = RegisterDeviceRequest(deviceId = "devX", deviceName = "nX", setupKey = "wrongX")
        for (r in failing) {
            `when`(authService.registerDevice("default", r))
                .thenReturn(PairCompleteResponse(false, "Invalid setup key"))
        }
        `when`(authService.registerDevice("default", lockedReq))
            .thenReturn(PairCompleteResponse(false, "Invalid setup key"))
        val mvc = mockMvc(LoginRateLimiter(5, 60_000, true))

        failing.forEach { r ->
            mvc.perform(post("/api/v1/auth/register-device")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"deviceId":"${r.deviceId}","deviceName":"${r.deviceName}","setupKey":"${r.setupKey}"}"""))
                .andExpect(status().isBadRequest)
        }
        mvc.perform(post("/api/v1/auth/register-device")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"deviceId":"devX","deviceName":"nX","setupKey":"wrongX"}"""))
            .andExpect(status().isTooManyRequests)
    }

    @Test
    fun `successful pairing resets the failure bucket`() {
        val mvc = mockMvc(LoginRateLimiter(5, 60_000, true))
        // 3 次失败（显式逐值打桩，避免 Kotlin 非空参数与 any() 匹配器的坑）
        `when`(authService.completePairing(anyProbe("200000"))).thenReturn(pairFail())
        `when`(authService.completePairing(anyProbe("200001"))).thenReturn(pairFail())
        `when`(authService.completePairing(anyProbe("200002"))).thenReturn(pairFail())
        repeat(3) { i ->
            mvc.perform(post("/api/v1/auth/pair/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"20000$i","deviceId":"probe","deviceName":"probe"}"""))
                .andExpect(status().isBadRequest)
        }
        // 成功 → 清零
        val ok = PairCompleteRequest(code = "777777", deviceId = "ok", deviceName = "ok")
        `when`(authService.completePairing(ok)).thenReturn(PairCompleteResponse(true, "Paired", "tok", "default"))
        mvc.perform(post("/api/v1/auth/pair/complete")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"code":"777777","deviceId":"ok","deviceName":"ok"}"""))
            .andExpect(status().isOk)
        // 清零后再失败 4 次（< 阈值 5）→ 不应锁定
        `when`(authService.completePairing(anyPair("300000"))).thenReturn(pairFail())
        `when`(authService.completePairing(anyPair("300001"))).thenReturn(pairFail())
        `when`(authService.completePairing(anyPair("300002"))).thenReturn(pairFail())
        `when`(authService.completePairing(anyPair("300003"))).thenReturn(pairFail())
        repeat(4) { i ->
            mvc.perform(post("/api/v1/auth/pair/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"30000$i","deviceId":"e","deviceName":"n"}"""))
                .andExpect(status().isBadRequest)
        }
    }

    private fun pairFail() = PairCompleteResponse(false, "Pairing code is invalid or expired")

    /** 与第一段请求体一致（deviceId/deviceName = probe/probe）。 */
    private fun anyProbe(code: String) = PairCompleteRequest(code = code, deviceId = "probe", deviceName = "probe")

    /** 与第二段请求体一致（deviceId/deviceName = e/n）。 */
    private fun anyPair(code: String) = PairCompleteRequest(code = code, deviceId = "e", deviceName = "n")
}
