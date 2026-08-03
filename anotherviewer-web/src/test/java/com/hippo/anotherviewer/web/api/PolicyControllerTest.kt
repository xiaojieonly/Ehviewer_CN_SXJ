package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.argThatK
import com.hippo.anotherviewer.web.config.SecurityConfig
import com.hippo.anotherviewer.web.dto.ConflictStrategy
import com.hippo.anotherviewer.web.dto.SyncPolicyDto
import com.hippo.anotherviewer.web.service.SiteAuthService
import com.hippo.anotherviewer.web.service.ServerConfigService
import com.hippo.anotherviewer.web.service.SyncService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * HTTP-layer tests for [PolicyController]（/api/v1/sync/policy，契约 v2 §8 / openapi 冻结）：
 * GET 返回当前策略、PUT 持久化、非法值 400（统一错误信封）、未认证 401。
 * merge 层面的策略行为见 SyncStrategyMatrixTest。
 */
@WebMvcTest(PolicyController::class)
@Import(SecurityConfig::class)
class PolicyControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockBean
    lateinit var syncService: SyncService

    @MockBean
    lateinit var authService: SiteAuthService

    @MockBean
    lateinit var serverConfigService: ServerConfigService

    @BeforeEach
    fun setUp() {
        `when`(serverConfigService.getBoolean(ServerConfigService.KEY_REQUIRE_AUTH, false)).thenReturn(true)
        `when`(authService.validateToken("valid-token")).thenReturn("alice")
    }

    @Test
    fun `GET policy returns the current policy with contract wire values`() {
        `when`(syncService.currentPolicy()).thenReturn(
            SyncPolicyDto(ConflictStrategy.WEB_PRIORITY, clientTier = 2, autoSyncIntervalSec = 60)
        )

        mockMvc.perform(get("/api/v1/sync/policy").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.conflictStrategy").value("web_priority"))
            .andExpect(jsonPath("$.clientTier").value(2))
            .andExpect(jsonPath("$.autoSyncIntervalSec").value(60))
    }

    @Test
    fun `GET policy defaults to device_priority`() {
        `when`(syncService.currentPolicy()).thenReturn(SyncPolicyDto())

        mockMvc.perform(get("/api/v1/sync/policy").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.conflictStrategy").value("device_priority"))
            .andExpect(jsonPath("$.clientTier").value(1))
            .andExpect(jsonPath("$.autoSyncIntervalSec").value(900))
    }

    @Test
    fun `PUT policy persists the validated policy and echoes it back`() {
        `when`(syncService.validatePolicy(any(SyncPolicyDto::class.java))).thenReturn(null)
        `when`(syncService.updatePolicy(any(SyncPolicyDto::class.java))).thenAnswer { inv -> inv.getArgument<SyncPolicyDto>(0) }

        mockMvc.perform(
            put("/api/v1/sync/policy")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"conflictStrategy":"lww","clientTier":0,"autoSyncIntervalSec":0}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.conflictStrategy").value("lww"))
            .andExpect(jsonPath("$.clientTier").value(0))
            .andExpect(jsonPath("$.autoSyncIntervalSec").value(0))

        verify(syncService).updatePolicy(
            argThatK<SyncPolicyDto> {
                it.conflictStrategy == ConflictStrategy.LWW && it.clientTier == 0 && it.autoSyncIntervalSec == 0
            }
        )
    }

    @Test
    fun `PUT policy with out-of-range clientTier is a 400 validation error`() {
        `when`(syncService.validatePolicy(any(SyncPolicyDto::class.java)))
            .thenReturn("clientTier must be one of 0..3")

        mockMvc.perform(
            put("/api/v1/sync/policy")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"conflictStrategy":"device_priority","clientTier":9,"autoSyncIntervalSec":900}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("clientTier must be one of 0..3"))

        verify(syncService, never()).updatePolicy(any(SyncPolicyDto::class.java))
    }

    @Test
    fun `PUT policy with negative autoSyncIntervalSec is a 400 validation error`() {
        `when`(syncService.validatePolicy(any(SyncPolicyDto::class.java)))
            .thenReturn("autoSyncIntervalSec must be >= 0")

        mockMvc.perform(
            put("/api/v1/sync/policy")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"conflictStrategy":"lww","clientTier":1,"autoSyncIntervalSec":-1}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))

        verify(syncService, never()).updatePolicy(any(SyncPolicyDto::class.java))
    }

    @Test
    fun `PUT policy with unknown conflictStrategy is rejected with 400`() {
        // Jackson 反序列化未知枚举值 → HttpMessageNotReadableException → GlobalExceptionHandler 400
        mockMvc.perform(
            put("/api/v1/sync/policy")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"conflictStrategy":"banana_priority","clientTier":1,"autoSyncIntervalSec":900}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))

        verify(syncService, never()).updatePolicy(any(SyncPolicyDto::class.java))
    }

    @Test
    fun `PUT policy with malformed body is rejected with 400`() {
        mockMvc.perform(
            put("/api/v1/sync/policy")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"conflictStrategy":""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET policy without bearer token is rejected with 401`() {
        mockMvc.perform(get("/api/v1/sync/policy"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `PUT policy without bearer token is rejected with 401`() {
        mockMvc.perform(
            put("/api/v1/sync/policy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"conflictStrategy":"lww","clientTier":1,"autoSyncIntervalSec":900}""")
        )
            .andExpect(status().isUnauthorized)

        verify(syncService, never()).updatePolicy(any(SyncPolicyDto::class.java))
    }
}
