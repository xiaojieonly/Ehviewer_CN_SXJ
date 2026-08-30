package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.entity.HistoryInfoEntity
import com.hippo.anotherviewer.web.repository.GalleryTagsRepository
import com.hippo.anotherviewer.web.repository.HistoryInfoRepository
import com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository
import com.hippo.anotherviewer.web.repository.QuickSearchRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

/**
 * R4-4: AddHistoryRequest.mode 透传——POST(history) 把 body.mode 写入 history 行
 * mode 列；GET history（HistoryService.listHistory → HistoryItem.mode）读回同一值。
 *
 * 写路径 = GalleryService.addToHistory（GalleryController.addToHistory 的服务层），
 * 读路径 = HistoryService.listHistory（HistoryController 的服务层），二者共享同一
 * HistoryInfoRepository（此处为内存 fake），故可端到端验证 POST mode=5 → GET 见 mode=5。
 */
class HistoryModePassthroughTest {

    private lateinit var historyRepository: HistoryInfoRepository
    private lateinit var galleryService: GalleryService
    private lateinit var historyService: HistoryService

    @BeforeEach
    fun setUp() {
        historyRepository = inMemoryHistoryRepo()
        val sessionManager = mock(SiteSessionManager::class.java)
        galleryService = GalleryService(
            historyRepository,
            mock(QuickSearchRepository::class.java),
            mock(GalleryTagsRepository::class.java),
            mock(LocalFavoriteInfoRepository::class.java),
            sessionManager,
            mock(com.hippo.anotherviewer.web.repository.DownloadInfoRepository::class.java),
            com.hippo.anotherviewer.web.config.SiteCoreConfigProperties(),
            mock(GalleryLookupService::class.java),
            EhAvailabilityService("https://e-hentai.org", 5000),
            mock(DownloadDirIndex::class.java),
        )
        historyService = HistoryService(historyRepository)
    }

    @Test
    fun `post history with mode then get history returns that mode`() {
        // POST /gallery/history/{gid} body.mode = 5
        galleryService.addToHistory(42L, "tok42", "Gallery 42", mode = 5)

        // GET /history -> HistoryItem.mode
        val history = historyService.listHistory().history
        assertEquals(1, history.size)
        assertEquals(42L, history.single().gid)
        assertEquals(5, history.single().mode)
    }

    @Test
    fun `post history without mode falls back to the default 0`() {
        galleryService.addToHistory(43L, "tok43", "Gallery 43", mode = 0)

        assertEquals(0, historyService.listHistory().history.single().mode)
    }

    @Test
    fun `re-adding an existing history updates its mode`() {
        galleryService.addToHistory(44L, "tok44", "Gallery 44", mode = 1)
        galleryService.addToHistory(44L, "tok44", "Gallery 44", mode = 7)

        val history = historyService.listHistory().history
        assertEquals(1, history.size)
        assertEquals(7, history.single().mode)
    }

    @Test
    fun `mode does not leak into unrelated history rows`() {
        galleryService.addToHistory(45L, "tok45", "A", mode = 3)
        galleryService.addToHistory(46L, "tok46", "B", mode = 0)

        val byGid = historyService.listHistory().history.associateBy { it.gid }
        assertEquals(3, byGid[45L]!!.mode)
        assertEquals(0, byGid[46L]!!.mode)
        assertTrue(byGid.size == 2)
    }

    /** Map-backed HistoryInfoRepository covering the methods the two services call. */
    private fun inMemoryHistoryRepo(): HistoryInfoRepository {
        val repo = mock(HistoryInfoRepository::class.java)
        val store = LinkedHashMap<Long, HistoryInfoEntity>()
        `when`(repo.save(any(HistoryInfoEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<HistoryInfoEntity>(0)
            store[e.gid] = e
            e
        }
        `when`(repo.findByGid(anyLong())).thenAnswer { inv -> store[inv.getArgument<Long>(0)] }
        `when`(repo.findAllByOrderByTimeDesc()).thenAnswer { store.values.sortedByDescending { it.time } }
        return repo
    }
}
