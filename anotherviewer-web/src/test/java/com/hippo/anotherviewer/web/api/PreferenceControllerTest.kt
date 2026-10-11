package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.entity.ServerConfigEntity
import com.hippo.anotherviewer.web.entity.UserPreferenceEntity
import com.hippo.anotherviewer.web.repository.ServerConfigRepository
import com.hippo.anotherviewer.web.repository.UserPreferenceRepository
import com.hippo.anotherviewer.web.service.EncryptionService
import com.hippo.anotherviewer.web.service.ServerConfigService
import com.hippo.anotherviewer.web.service.UserPreferenceService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * HTTP 契约测试（E2E-1）: 异构偏好串经同步 replace 写入后，
 * GET/PUT /api/v1/preferences 仍返回 200，web 设置页可加载。
 */
class PreferenceControllerTest {

    private lateinit var service: UserPreferenceService
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        val repo = mock(UserPreferenceRepository::class.java)
        val store = ConcurrentHashMap<String, UserPreferenceEntity>()
        `when`(repo.save(any(UserPreferenceEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<UserPreferenceEntity>(0)
            store[e.username] = e
            e
        }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv -> store[inv.getArgument<String>(0)] }
        service = UserPreferenceService(repo, fakeServerConfig())
        mvc = MockMvcBuilders.standaloneSetup(PreferenceController(service))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    private fun principal(name: String): RequestPostProcessor = RequestPostProcessor { request ->
        request.userPrincipal = UsernamePasswordAuthenticationToken(
            name, null, listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
        request
    }

    @Test
    fun `GET returns 200 after a heterogeneous preference push`() {
        mvc.perform(get("/api/v1/preferences").with(principal("alice")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.general.theme").value("light"))

        push("alice", """{"theme":"dark"}""")

        mvc.perform(get("/api/v1/preferences").with(principal("alice")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.general.theme").value("light"))
            .andExpect(jsonPath("$.reader.autoPlayIntervalSec").value(2))
    }

    @Test
    fun `GET returns 200 when nested unknown keys are present`() {
        push("alice", """{"general":{"foreignKey":true,"theme":"dark"},"reader":{"unknown":1}}""")

        mvc.perform(get("/api/v1/preferences").with(principal("alice")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.general.theme").value("dark"))
    }

    @Test
    fun `PUT returns 200 and persists after a heterogeneous preference push`() {
        push("alice", """{"theme":"dark"}""")

        mvc.perform(
            put("/api/v1/preferences")
                .with(principal("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"general":{"theme":"blue"}}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.general.theme").value("blue"))

        mvc.perform(get("/api/v1/preferences").with(principal("alice")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.general.theme").value("blue"))
    }

    @Test
    fun `GET keeps the stored value when a stale push is rejected`() {
        push("alice", """{"general":{"theme":"dark"}}""", System.currentTimeMillis() + 60_000)
        // 旧设备的 push 被 LWW 拒绝
        push("alice", """{"general":{"theme":"blue"}}""", System.currentTimeMillis() - 60_000)

        mvc.perform(get("/api/v1/preferences").with(principal("alice")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.general.theme").value("dark"))
    }

    // ---- Wave-1 新键（1b general +6 / 1c reader +9） ----

    @Test
    fun `GET exposes the wave-1 defaults for the new keys`() {
        mvc.perform(get("/api/v1/preferences").with(principal("alice")))
            .andExpect(status().isOk)
            // general +6
            .andExpect(jsonPath("$.general.listMode").value("grid"))
            .andExpect(jsonPath("$.general.showUploader").value(false))
            .andExpect(jsonPath("$.general.showPostedTime").value(false))
            .andExpect(jsonPath("$.general.defaultFavoriteSlot").value(0))
            .andExpect(jsonPath("$.general.favoriteSlotNames").value(""))
            .andExpect(jsonPath("$.general.recentSearchMax").value(10))
            // reader +9
            .andExpect(jsonPath("$.reader.backgroundColor").value("black"))
            .andExpect(jsonPath("$.reader.tapZoneScheme").value("threeZone"))
            .andExpect(jsonPath("$.reader.keyboardPaging").value(true))
            .andExpect(jsonPath("$.reader.zoomStep").value(0.25))
            .andExpect(jsonPath("$.reader.maxZoom").value(3.0))
            .andExpect(jsonPath("$.reader.dualPageGap").value(8))
            .andExpect(jsonPath("$.reader.splitWidePages").value(false))
            .andExpect(jsonPath("$.reader.preloadCount").value(2))
            .andExpect(jsonPath("$.reader.pageTransition").value("slide"))
    }

    @Test
    fun `PUT persists the new keys`() {
        mvc.perform(
            put("/api/v1/preferences")
                .with(principal("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "general": {"showUploader": true, "defaultFavoriteSlot": 3, "favoriteSlotNames": "主用|备用", "recentSearchMax": 0},
                      "reader": {"backgroundColor": "white", "tapZoneScheme": "edgeOnly", "zoomStep": 0.5, "pageTransition": "none"}
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.general.showUploader").value(true))
            .andExpect(jsonPath("$.general.defaultFavoriteSlot").value(3))
            .andExpect(jsonPath("$.general.favoriteSlotNames").value("主用|备用"))
            .andExpect(jsonPath("$.general.recentSearchMax").value(0))
            .andExpect(jsonPath("$.reader.backgroundColor").value("white"))
            .andExpect(jsonPath("$.reader.tapZoneScheme").value("edgeOnly"))
            .andExpect(jsonPath("$.reader.zoomStep").value(0.5))
            .andExpect(jsonPath("$.reader.pageTransition").value("none"))

        mvc.perform(get("/api/v1/preferences").with(principal("alice")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.general.defaultFavoriteSlot").value(3))
            .andExpect(jsonPath("$.reader.zoomStep").value(0.5))
    }

    @Test
    fun `PUT keeps section-level deep merge — untouched sections survive`() {
        // 先写 reader 新键
        mvc.perform(
            put("/api/v1/preferences")
                .with(principal("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reader":{"pageTransition":"fade","preloadCount":5}}""")
        ).andExpect(status().isOk)

        // 再只更新 general —— reader 节（含新键）不受影响
        mvc.perform(
            put("/api/v1/preferences")
                .with(principal("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"general":{"showPostedTime":true}}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.general.showPostedTime").value(true))
            .andExpect(jsonPath("$.reader.pageTransition").value("fade"))
            .andExpect(jsonPath("$.reader.preloadCount").value(5))

        mvc.perform(get("/api/v1/preferences").with(principal("alice")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reader.pageTransition").value("fade"))
    }

    @Test
    fun `PUT rejects out-of-range new keys with 400`() {
        mvc.perform(
            put("/api/v1/preferences")
                .with(principal("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reader":{"zoomStep":1.5}}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("zoomStep must be between 0.05 and 1.0"))

        mvc.perform(
            put("/api/v1/preferences")
                .with(principal("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"general":{"defaultFavoriteSlot":10}}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))

        mvc.perform(
            put("/api/v1/preferences")
                .with(principal("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"general":{"recentSearchMax":-1}}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))

        mvc.perform(
            put("/api/v1/preferences")
                .with(principal("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reader":{"brightness":101}}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("brightness must be between 0 and 100"))

        mvc.perform(
            put("/api/v1/preferences")
                .with(principal("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reader":{"preloadCount":21}}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))

        // 全部被拒，缺省仍是原值
        mvc.perform(get("/api/v1/preferences").with(principal("alice")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reader.zoomStep").value(0.25))
            .andExpect(jsonPath("$.reader.brightness").value(0))
            .andExpect(jsonPath("$.reader.preloadCount").value(2))
            .andExpect(jsonPath("$.general.defaultFavoriteSlot").value(0))
            .andExpect(jsonPath("$.general.recentSearchMax").value(10))
    }

    @Test
    fun `GET tolerates unknown keys next to the new keys after a sync push`() {
        push(
            "alice",
            """{"general":{"listMode":"list","futureKey":1},"reader":{"pageTransition":"fade","unknown":2},"mystery":42}""",
        )

        mvc.perform(get("/api/v1/preferences").with(principal("alice")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.general.listMode").value("list"))
            .andExpect(jsonPath("$.reader.pageTransition").value("fade"))
            // 未知键不打挂，也不影响其余新键缺省
            .andExpect(jsonPath("$.general.recentSearchMax").value(10))
            .andExpect(jsonPath("$.reader.zoomStep").value(0.25))
    }

    private fun push(username: String, json: String, lastModified: Long = System.currentTimeMillis() + 60_000) {
        // 走真实 service 的 replace, 等价于一次 sync push 写入偏好
        service.replace(username, json, "android-1", lastModified)
    }

    /** Real [ServerConfigService] over an in-memory [ServerConfigRepository] fake. */
    private fun fakeServerConfig(): ServerConfigService {
        val repo = mock(ServerConfigRepository::class.java)
        val store = ConcurrentHashMap<String, ServerConfigEntity>()
        `when`(repo.findById(anyString())).thenAnswer { inv ->
            Optional.ofNullable(store[inv.getArgument<String>(0)])
        }
        `when`(repo.save(any(ServerConfigEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<ServerConfigEntity>(0)
            store[e.key] = e
            e
        }
        return ServerConfigService(repo, EncryptionService(), SiteCoreConfigProperties())
    }

    @Test
    fun `privacyMask projects the live switch and an app push writes it through`() {
        val base = System.currentTimeMillis()
        // 存量文档没有 privacyMask —— GET 投影权威缺省 false
        push("alice", """{"general":{"theme":"dark"}}""", base + 60_000)
        mvc.perform(get("/api/v1/preferences").with(principal("alice")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.privacy.privacyMask").value(false))

        // App push 携带 privacyMask=true —— 写穿透权威开关，GET 投影回 true
        push("alice", """{"privacy":{"enableAnalytics":false,"privacyMask":true}}""", base + 120_000)
        mvc.perform(get("/api/v1/preferences").with(principal("alice")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.privacy.privacyMask").value(true))

        // 旧版 App（文档无该字段）push —— 不触碰打码开关
        push("alice", """{"general":{"theme":"blue"}}""", base + 180_000)
        mvc.perform(get("/api/v1/preferences").with(principal("alice")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.general.theme").value("blue"))
            .andExpect(jsonPath("$.privacy.privacyMask").value(true))
    }
}
