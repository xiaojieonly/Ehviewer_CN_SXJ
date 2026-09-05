package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.captureK
import com.hippo.anotherviewer.web.entity.LocalFavoriteInfoEntity
import com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

/**
 * Contract tests for [FavoriteService], per audit item N-5:
 *
 *  - `POST /favorite/add` must never write an out-of-range favoriteSlot.
 *  - `category` (a site bitmask, up to 512) is written to its own column only;
 *    it is independent from the folder slot.
 *  - The optional `slot` argument follows the Android contract (-2 = not
 *    favorited, -1 = default folder, 0-9 = custom slots) and is clamped.
 *  - `listFavorites` tab semantics follow the Android FavoritesScene (F-UX5):
 *    slot 0 = default folder (favoriteSlot in -1, 0), slot N>0 = exactly N,
 *    slot < 0 keeps the legacy "all rows" total mapping.
 *  - Response items carry the row's real favoriteSlot for the ♥ badge.
 */
class FavoriteServiceTest {

    private lateinit var repository: LocalFavoriteInfoRepository
    private lateinit var historyRepository: com.hippo.anotherviewer.web.repository.HistoryInfoRepository
    private lateinit var downloadRepository: com.hippo.anotherviewer.web.repository.DownloadInfoRepository
    private lateinit var service: FavoriteService

    @BeforeEach
    fun setUp() {
        repository = mock(LocalFavoriteInfoRepository::class.java)
        historyRepository = mock(com.hippo.anotherviewer.web.repository.HistoryInfoRepository::class.java)
        downloadRepository = mock(com.hippo.anotherviewer.web.repository.DownloadInfoRepository::class.java)
        service = FavoriteService(repository, historyRepository, downloadRepository, HistoryService(historyRepository))
    }

    private fun savedEntity(): LocalFavoriteInfoEntity {
        val captor = ArgumentCaptor.forClass(LocalFavoriteInfoEntity::class.java)
        verify(repository).save(captureK<LocalFavoriteInfoEntity>(captor))
        return captor.value
    }

    @Test
    fun `category bitmask is never written to favoriteSlot`() {
        `when`(repository.findByGid(42L)).thenReturn(null)

        service.addFavorite(42L, "token", "Title", 512)

        val entity = savedEntity()
        assertEquals(-1, entity.favoriteSlot)
        assertEquals(512, entity.category)
    }

    @Test
    fun `in-range slot is stored as-is`() {
        `when`(repository.findByGid(42L)).thenReturn(null)

        service.addFavorite(42L, "token", "Title", 512, slot = 3)

        val entity = savedEntity()
        assertEquals(3, entity.favoriteSlot)
        assertEquals(512, entity.category)
    }

    @Test
    fun `slot above 9 is clamped to 9`() {
        `when`(repository.findByGid(42L)).thenReturn(null)

        service.addFavorite(42L, "token", "Title", 0, slot = 999)

        assertEquals(9, savedEntity().favoriteSlot)
    }

    @Test
    fun `slot below -2 is clamped to -2`() {
        `when`(repository.findByGid(42L)).thenReturn(null)

        service.addFavorite(42L, "token", "Title", 0, slot = -10)

        assertEquals(-2, savedEntity().favoriteSlot)
    }

    @Test
    fun `duplicate gid is rejected without save`() {
        `when`(repository.findByGid(42L)).thenReturn(LocalFavoriteInfoEntity())

        assertFalse(service.addFavorite(42L, "token", "Title", 1))

        verify(repository, never()).save(any(LocalFavoriteInfoEntity::class.java))
    }

    // ── 任务 D：favoriteSlot 回写来源历史行（详情页收藏态数据源） ──

    @Test
    fun `addFavorite writes the slot back to the existing history row`() {
        // 详情读取链（GalleryService 历史分支）优先历史行，不回写则重进详情
        // favoriteSlot 恒 -2。回写值须与收藏行一致（含夹紧后的 slot）。
        `when`(repository.findByGid(42L)).thenReturn(null)
        `when`(historyRepository.findByGid(42L))
            .thenReturn(com.hippo.anotherviewer.web.entity.HistoryInfoEntity().apply { gid = 42L })

        service.addFavorite(42L, "token", "Title", 512, slot = 999)

        val captor = ArgumentCaptor.forClass(com.hippo.anotherviewer.web.entity.HistoryInfoEntity::class.java)
        verify(historyRepository).save(captureK<com.hippo.anotherviewer.web.entity.HistoryInfoEntity>(captor))
        assertEquals(9, captor.value.favoriteSlot) // 999 夹紧到 9，与收藏行一致
    }

    @Test
    fun `removeFavorite resets the history row favoriteSlot to -2`() {
        // 对称清除：取消收藏后重进详情不残留收藏态（置回未收藏）。
        `when`(repository.findByGid(42L)).thenReturn(LocalFavoriteInfoEntity())
        `when`(historyRepository.findByGid(42L))
            .thenReturn(com.hippo.anotherviewer.web.entity.HistoryInfoEntity().apply { gid = 42L; favoriteSlot = 3 })

        assertTrue(service.removeFavorite(42L))

        val captor = ArgumentCaptor.forClass(com.hippo.anotherviewer.web.entity.HistoryInfoEntity::class.java)
        verify(historyRepository).save(captureK<com.hippo.anotherviewer.web.entity.HistoryInfoEntity>(captor))
        assertEquals(-2, captor.value.favoriteSlot)
    }

    @Test
    fun `addFavorite without a history row does not create one`() {
        // 收藏不凭空造历史：无历史行仅记日志，historyRepository.save 不发生。
        `when`(repository.findByGid(42L)).thenReturn(null)
        `when`(historyRepository.findByGid(42L)).thenReturn(null)

        assertTrue(service.addFavorite(42L, "token", "Title", 512))

        verify(historyRepository, never()).save(any(com.hippo.anotherviewer.web.entity.HistoryInfoEntity::class.java))
        verify(repository).save(any(LocalFavoriteInfoEntity::class.java)) // 收藏行本身照常落库
    }

    @Test
    fun `addFavorite writes the slot back to an existing download row`() {
        // 详情读取链 download 分支优先于 history 分支：已下载画廊的详情/下载
        // 列表以 download 行为 favoriteSlot 来源，同样必须回写。
        `when`(repository.findByGid(42L)).thenReturn(null)
        `when`(historyRepository.findByGid(42L)).thenReturn(null)
        `when`(downloadRepository.findByGid(42L))
            .thenReturn(com.hippo.anotherviewer.web.entity.DownloadInfoEntity().apply { gid = 42L })

        service.addFavorite(42L, "token", "Title", 512, slot = 999)

        val captor = ArgumentCaptor.forClass(com.hippo.anotherviewer.web.entity.DownloadInfoEntity::class.java)
        verify(downloadRepository).save(captureK<com.hippo.anotherviewer.web.entity.DownloadInfoEntity>(captor))
        assertEquals(9, captor.value.favoriteSlot) // 999 夹紧到 9，与收藏行一致
    }

    @Test
    fun `removeFavorite resets the download row favoriteSlot to -2`() {
        `when`(repository.findByGid(42L)).thenReturn(LocalFavoriteInfoEntity())
        `when`(historyRepository.findByGid(42L)).thenReturn(null)
        `when`(downloadRepository.findByGid(42L))
            .thenReturn(com.hippo.anotherviewer.web.entity.DownloadInfoEntity().apply { gid = 42L; favoriteSlot = 3 })

        assertTrue(service.removeFavorite(42L))

        val captor = ArgumentCaptor.forClass(com.hippo.anotherviewer.web.entity.DownloadInfoEntity::class.java)
        verify(downloadRepository).save(captureK<com.hippo.anotherviewer.web.entity.DownloadInfoEntity>(captor))
        assertEquals(-2, captor.value.favoriteSlot)
    }

    @Test
    fun `addFavorite without download row does not touch download repository`() {
        `when`(repository.findByGid(42L)).thenReturn(null)
        `when`(historyRepository.findByGid(42L)).thenReturn(null)
        `when`(downloadRepository.findByGid(42L)).thenReturn(null)

        assertTrue(service.addFavorite(42L, "token", "Title", 512))

        verify(downloadRepository, never()).save(any(com.hippo.anotherviewer.web.entity.DownloadInfoEntity::class.java))
    }

    @Test
    fun `listFavorites with slot 0 returns only the default folder (-1 and 0)`() {
        // F-UX5: tab 0 对齐 app FavoritesScene 首签——默认夹（-1）与显式
        // Favorites 0（0）同列，自定义夹（5）不再混入。
        `when`(repository.findAllByOrderByTimeDesc())
            .thenReturn(listOf(favEntity(1, 0), favEntity(2, -1), favEntity(3, 5)))

        val response = service.listFavorites(0, 1, 20)

        assertEquals(listOf(1L, 2L), response.favorites.map { it.gid })
        assertEquals(2, response.favorites.size)
    }

    @Test
    fun `listFavorites items carry the row's real favoriteSlot`() {
        // F-UX5: ♥ 徽章数据源——条目随行携带真实 slot，前端不再退回页签号。
        `when`(repository.findAllByOrderByTimeDesc())
            .thenReturn(listOf(favEntity(1, 0), favEntity(2, -1), favEntity(3, 5)))

        val response = service.listFavorites(0, 1, 20)

        assertEquals(listOf(0, -1), response.favorites.map { it.favoriteSlot })
        assertEquals(5, service.listFavorites(5, 1, 20).favorites.single().favoriteSlot)
    }

    @Test
    fun `listFavorites items carry readProgress from history rows`() {
        // 阅读进度角标数据源：同 gid 历史行的 page 批量填充（findByGidIn 单次），
        // 无历史行的条目为 null。
        `when`(repository.findAllByOrderByTimeDesc())
            .thenReturn(listOf(favEntity(1, 0), favEntity(2, -1)))
        val history1 = com.hippo.anotherviewer.web.entity.HistoryInfoEntity().apply {
            gid = 1L
            page = 37
        }
        `when`(historyRepository.findByGidIn(listOf(1L, 2L))).thenReturn(listOf(history1))

        val response = service.listFavorites(-1, 1, 20)

        assertEquals(37, response.favorites.first { it.gid == 1L }.readProgress)
        assertNull(response.favorites.first { it.gid == 2L }.readProgress)
        verify(historyRepository).findByGidIn(listOf(1L, 2L))
    }

    @Test
    fun `listFavorites filters by slot`() {
        `when`(repository.findAllByOrderByTimeDesc())
            .thenReturn(listOf(favEntity(1, 0), favEntity(2, -1), favEntity(3, 5)))

        val response = service.listFavorites(5, 1, 20)

        assertEquals(listOf(3L), response.favorites.map { it.gid })
    }

    @Test
    fun `listFavorites with negative slot returns all slots`() {
        `when`(repository.findAllByOrderByTimeDesc())
            .thenReturn(listOf(favEntity(1, 0), favEntity(2, -1), favEntity(3, 5)))

        val response = service.listFavorites(-1, 1, 20)

        assertEquals(3, response.favorites.size)
    }

    @Test
    fun `listFavorites hides tombstone rows deleted by sync`() {
        val live = favEntity(1, 0)
        val tombstone = favEntity(2, 3).apply { deleted = true }
        `when`(repository.findAllByOrderByTimeDesc()).thenReturn(listOf(live, tombstone))

        val response = service.listFavorites(0, 1, 20)

        assertEquals(listOf(1L), response.favorites.map { it.gid })
        assertEquals(1, response.favorites.size)
    }

    @Test
    fun `listFavorites tombstones do not count into pagination`() {
        val live = favEntity(1, 0)
        val tombstone = favEntity(2, 3).apply { deleted = true }
        `when`(repository.findAllByOrderByTimeDesc()).thenReturn(listOf(live, tombstone))

        val response = service.listFavorites(3, 1, 20)

        assertTrue(response.favorites.isEmpty())
        assertEquals(0, response.totalPages)
    }

    @Test
    fun `listFavorites with only tombstones returns an empty list`() {
        val tombstone = favEntity(2, 3).apply { deleted = true }
        `when`(repository.findAllByOrderByTimeDesc()).thenReturn(listOf(tombstone))

        val response = service.listFavorites(0, 1, 20)

        assertTrue(response.favorites.isEmpty())
        assertEquals(0, response.totalPages)
    }

    @Test
    fun `listFavorites q filters by case-insensitive substring on title`() {
        `when`(repository.findAllByOrderByTimeDesc())
            .thenReturn(listOf(
                favEntity(1, 0, "Futanari Story"),
                favEntity(2, 0, "Plain"),
                favEntity(3, 0, "Futa and More"),
            ))

        val response = service.listFavorites(0, 1, 20, q = "futa")

        assertEquals(listOf(1L, 3L), response.favorites.map { it.gid })
        assertEquals(2, response.favorites.size)
        // total/分页按匹配后行数计。
        assertEquals(1, response.totalPages)
    }

    @Test
    fun `listFavorites q matches titleJpn and keeps slot filter`() {
        `when`(repository.findAllByOrderByTimeDesc())
            .thenReturn(listOf(
                favEntity(1, 0, "Plain", "フタナリ"),
                favEntity(2, 5, "Futanari Story"),
            ))

        // slot 过滤先行：仅默认夹（0）→ q 命中 titleJpn。
        val response = service.listFavorites(0, 1, 20, q = "フタナリ")

        assertEquals(listOf(1L), response.favorites.map { it.gid })
        assertEquals(1, response.totalPages)
    }

    @Test
    fun `listFavorites regex matches title`() {
        `when`(repository.findAllByOrderByTimeDesc())
            .thenReturn(listOf(
                favEntity(1, 0, "Futanari Story"),
                favEntity(2, 0, "Plain"),
            ))

        val response = service.listFavorites(-1, 1, 20, q = "(?i)^futa", regex = true)

        assertEquals(listOf(1L), response.favorites.map { it.gid })
        assertEquals(1, response.totalPages)
    }

    @Test
    fun `listFavorites invalid regex throws IllegalArgumentException`() {
        `when`(repository.findAllByOrderByTimeDesc()).thenReturn(listOf(favEntity(1, 0)))

        assertThrows(IllegalArgumentException::class.java) {
            service.listFavorites(0, 1, 20, q = "(", regex = true)
        }
    }

    @Test
    fun `listFavorites regex takes precedence over substring when regex is true`() {
        `when`(repository.findAllByOrderByTimeDesc())
            .thenReturn(listOf(
                favEntity(1, 0, "Title 1"),
                favEntity(2, 0, "Plain"),
            ))

        // "T.tle" 作为子串不匹配任何 title；作为正则 T+任意字符+tle 命中 "Title 1"。
        val response = service.listFavorites(0, 1, 20, q = "T.tle", regex = true)

        assertEquals(listOf(1L), response.favorites.map { it.gid })
    }

    @Test
    fun `listFavorites blank q falls back to unfiltered list`() {
        `when`(repository.findAllByOrderByTimeDesc())
            .thenReturn(listOf(favEntity(1, 0), favEntity(2, 0)))

        val response = service.listFavorites(0, 1, 20, q = "   ")

        assertEquals(listOf(1L, 2L), response.favorites.map { it.gid })
        assertEquals(1, response.totalPages)
    }

    private fun favEntity(gid: Long, slot: Int, title: String, titleJpn: String? = null): LocalFavoriteInfoEntity {
        val e = favEntity(gid, slot)
        e.title = title
        e.titleJpn = titleJpn
        return e
    }

    private fun favEntity(gid: Long, slot: Int): LocalFavoriteInfoEntity {
        val e = LocalFavoriteInfoEntity()
        e.gid = gid
        e.token = "token$gid"
        e.title = "Title $gid"
        e.category = 512
        e.favoriteSlot = slot
        return e
    }
}
