package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.config.SecurityConfig
import com.hippo.anotherviewer.web.service.ServerConfigService
import com.hippo.anotherviewer.web.service.SiteAuthService
import com.hippo.anotherviewer.web.service.Tier2PageService
import com.hippo.anotherviewer.web.service.Tier2PageService.Tier2PageResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Tier-2 page API auth semantics under the real [SecurityConfig] filter chain
 * (same slice as SyncControllerTest): the endpoint inherits the sync endpoints'
 * bearer-token auth — missing/invalid token → 401 with the entry-point JSON
 * body, a valid device token → through to the controller.
 */
@WebMvcTest(Tier2PageController::class)
@Import(SecurityConfig::class)
class Tier2PageAuthTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockBean
    lateinit var tier2PageService: Tier2PageService

    @MockBean
    lateinit var authService: SiteAuthService

    @MockBean
    lateinit var serverConfigService: ServerConfigService

    @BeforeEach
    fun setUp() {
        // Auth enforced like production with require_auth=true.
        `when`(serverConfigService.getBoolean(ServerConfigService.KEY_REQUIRE_AUTH, false)).thenReturn(true)
        `when`(authService.validateToken("device-token")).thenReturn("alice")
        `when`(tier2PageService.fetchPage(anyString()))
            .thenReturn(Tier2PageResult.Served("<html>page</html>", "text/html; charset=UTF-8", stale = false))
    }

    @Test
    fun `missing bearer token is 401 with the entry-point body`() {
        mockMvc.perform(get("/api/v1/tier2/page").param("url", "https://e-hentai.org/"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().json("""{"success":false,"message":"Authentication required"}"""))
    }

    @Test
    fun `invalid bearer token is 401`() {
        mockMvc.perform(
            get("/api/v1/tier2/page")
                .param("url", "https://e-hentai.org/")
                .header("Authorization", "Bearer wrong-token")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `valid device token reaches the endpoint`() {
        mockMvc.perform(
            get("/api/v1/tier2/page")
                .param("url", "https://e-hentai.org/")
                .header("Authorization", "Bearer device-token")
        )
            .andExpect(status().isOk)
            .andExpect(header().string("X-Cache", "fresh"))
            .andExpect(content().string("<html>page</html>"))
    }
}
