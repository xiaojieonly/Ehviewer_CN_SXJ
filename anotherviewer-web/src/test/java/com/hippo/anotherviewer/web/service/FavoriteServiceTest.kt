package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.captureK
import com.hippo.anotherviewer.web.entity.LocalFavoriteInfoEntity
import com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    private lateinit var service: FavoriteService

    @BeforeEach
    fun setUp() {
        repository = mock(LocalFavoriteInfoRepository::class.java)
        service = FavoriteService(repository)
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
