package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.captureK
import com.hippo.anotherviewer.web.config.CurrentUsernameProvider
import com.hippo.anotherviewer.web.config.SecurityConfig
import com.hippo.anotherviewer.web.entity.LocalFavoriteInfoEntity
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import com.hippo.anotherviewer.web.repository.HistoryInfoRepository
import com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository
import com.hippo.anotherviewer.web.service.FavoriteService
import com.hippo.anotherviewer.web.service.HistoryService
import com.hippo.anotherviewer.web.service.ServerConfigService
import com.hippo.anotherviewer.web.service.SiteAuthService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * A7-1 端到端验收（@WebMvcTest + 真实 SecurityConfig 过滤链，同 SyncControllerTest 模式）：
 * require_auth=false（ServerConfigService mock 缺省 false）下 AuthTokenFilter 每请求置
 * "default" 主体 → CurrentUsernameProvider.currentUsername()=="default" →
 * POST /favorite/add 落库行 username=="default" 且 lastModified>0（stamping 经真实
 * FavoriteService/HistoryService 生效，仓储 mock 捕获实体断言）。
 */
@WebMvcTest(FavoriteController::class)
@Import(SecurityConfig::class, FavoriteAddStampingTest.StampingConfig::class)
class FavoriteAddStampingTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockBean
    lateinit var authService: SiteAuthService

    @MockBean
    lateinit var serverConfigService: ServerConfigService

    @MockBean
    lateinit var favoriteRepository: LocalFavoriteInfoRepository

    @MockBean
    lateinit var historyRepository: HistoryInfoRepository

    @MockBean
    lateinit var downloadRepository: DownloadInfoRepository

    @Test
    fun `favorite add under require_auth=false stamps username default`() {
        mockMvc.perform(
            post("/api/v1/favorite/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gid":42,"token":"a1b2c3d4e5","category":1,"slot":-1}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        val captor = ArgumentCaptor.forClass(LocalFavoriteInfoEntity::class.java)
        verify(favoriteRepository).save(captureK<LocalFavoriteInfoEntity>(captor))
        assertEquals(CurrentUsernameProvider.DEFAULT_USERNAME, captor.value.username)
        assertTrue(captor.value.lastModified > 0)
        assertTrue(captor.value.time > 0)
    }

    /** 真实 service 链（FavoriteService → HistoryService → Provider），仓储用 mock。 */
    @TestConfiguration
    class StampingConfig {
        /** v4：WebUI 直写路径与 sync 合并共用服务器打戳时钟（补 bean 让切片里也走 clock）。 */
        @Bean
        fun syncClock(): com.hippo.anotherviewer.web.service.SyncClock =
            com.hippo.anotherviewer.web.service.SyncClock()

        @Bean
        fun currentUsernameProvider(): CurrentUsernameProvider = CurrentUsernameProvider()

        @Bean
        fun historyService(
            historyRepository: HistoryInfoRepository,
            provider: CurrentUsernameProvider,
            syncClock: com.hippo.anotherviewer.web.service.SyncClock,
        ): HistoryService = HistoryService(historyRepository, provider, syncClock)

        @Bean
        fun favoriteService(
            favoriteRepository: LocalFavoriteInfoRepository,
            historyRepository: HistoryInfoRepository,
            downloadRepository: DownloadInfoRepository,
            historyService: HistoryService,
            provider: CurrentUsernameProvider,
            syncClock: com.hippo.anotherviewer.web.service.SyncClock,
        ): FavoriteService = FavoriteService(favoriteRepository, historyRepository, downloadRepository, historyService, provider, syncClock)
    }
}
