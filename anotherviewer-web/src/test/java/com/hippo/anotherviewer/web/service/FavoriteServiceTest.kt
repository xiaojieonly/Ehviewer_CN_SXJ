package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.captureK
import com.hippo.anotherviewer.web.entity.LocalFavoriteInfoEntity
import com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
 *  - `listFavorites` keeps `slot <= 0` returning all rows.
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
    fun `listFavorites with slot 0 returns all slots`() {
        `when`(repository.findAllByOrderByTimeDesc())
            .thenReturn(listOf(favEntity(1, 0), favEntity(2, -1), favEntity(3, 5)))

        val response = service.listFavorites(0, 1, 20)

        assertEquals(listOf(1L, 2L, 3L), response.favorites.map { it.gid })
        assertEquals(3, response.favorites.size)
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
