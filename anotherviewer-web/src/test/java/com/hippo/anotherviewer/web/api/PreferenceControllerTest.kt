package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.entity.UserPreferenceEntity
import com.hippo.anotherviewer.web.repository.UserPreferenceRepository
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
        service = UserPreferenceService(repo)
        mvc = MockMvcBuilders.standaloneSetup(PreferenceController(service)).build()
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

    private fun push(username: String, json: String, lastModified: Long = System.currentTimeMillis() + 60_000) {
        // 走真实 service 的 replace, 等价于一次 sync push 写入偏好
        service.replace(username, json, "android-1", lastModified)
    }
}
