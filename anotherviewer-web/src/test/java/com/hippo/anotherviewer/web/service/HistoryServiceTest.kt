package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.entity.HistoryInfoEntity
import com.hippo.anotherviewer.web.repository.HistoryInfoRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.argThatK
import com.hippo.anotherviewer.web.captureK
import com.hippo.anotherviewer.web.eq
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

class HistoryServiceTest {

    private lateinit var historyRepository: HistoryInfoRepository
    private lateinit var historyService: HistoryService

    @BeforeEach
    fun setUp() {
        historyRepository = mock(HistoryInfoRepository::class.java)
        historyService = HistoryService(historyRepository)
    }

    private fun entity(gid: Long, time: Long): HistoryInfoEntity {
        val e = HistoryInfoEntity()
        e.gid = gid
        e.token = "token$gid"
        e.title = "Title $gid"
        e.time = time
        return e
    }

    @Test
    fun `absent params returns the full history list`() {
        `when`(historyRepository.findAllByOrderByTimeDesc())
            .thenReturn(listOf(entity(3, 3000), entity(2, 2000), entity(1, 1000)))

        val response = historyService.listHistory()

        assertEquals(3, response.history.size)
        assertEquals(3, response.total)
        assertEquals(listOf(3L, 2L, 1L), response.history.map { it.gid })
        verify(historyRepository).findAllByOrderByTimeDesc()
        verify(historyRepository, never()).findHistoryPaged(any(Pageable::class.java))
    }

    @Test
    fun `provided params returns paged slice with total`() {
        val paged = PageImpl(listOf(entity(2, 2000), entity(1, 1000)), Pageable.unpaged(), 10)
        `when`(historyRepository.findHistoryPaged(any(Pageable::class.java))).thenReturn(paged)

        val response = historyService.listHistory(page = 1, pageSize = 50)

        assertEquals(listOf(2L, 1L), response.history.map { it.gid })
        assertEquals(10, response.total)
        verify(historyRepository).findHistoryPaged(argThatK { it.pageNumber == 1 && it.pageSize == 50 })
        verify(historyRepository, never()).findAllByOrderByTimeDesc()
    }

    @Test
    fun `pageSize is clamped to 200`() {
        `when`(historyRepository.findHistoryPaged(any(Pageable::class.java)))
            .thenReturn(PageImpl(emptyList<HistoryInfoEntity>(), Pageable.unpaged(), 0))

        historyService.listHistory(page = 0, pageSize = 500)

        val captor = ArgumentCaptor.forClass(Pageable::class.java)
        verify(historyRepository).findHistoryPaged(captureK<Pageable>(captor))
        assertEquals(0, captor.value.pageNumber)
        assertEquals(200, captor.value.pageSize)
    }

    @Test
    fun `page only defaults pageSize to 50`() {
        `when`(historyRepository.findHistoryPaged(any(Pageable::class.java)))
            .thenReturn(PageImpl(emptyList<HistoryInfoEntity>(), Pageable.unpaged(), 0))

        historyService.listHistory(page = 2)

        val captor = ArgumentCaptor.forClass(Pageable::class.java)
        verify(historyRepository).findHistoryPaged(captureK<Pageable>(captor))
        assertEquals(2, captor.value.pageNumber)
        assertEquals(50, captor.value.pageSize)
    }

    @Test
    fun `pageSize only defaults page to 0`() {
        `when`(historyRepository.findHistoryPaged(any(Pageable::class.java)))
            .thenReturn(PageImpl(emptyList<HistoryInfoEntity>(), Pageable.unpaged(), 0))

        historyService.listHistory(pageSize = 10)

        val captor = ArgumentCaptor.forClass(Pageable::class.java)
        verify(historyRepository).findHistoryPaged(captureK<Pageable>(captor))
        assertEquals(0, captor.value.pageNumber)
        assertEquals(10, captor.value.pageSize)
    }

    @Test
    fun `addHistory with mode persists it`() {
        `when`(historyRepository.findByGid(7L)).thenReturn(null)

        historyService.addHistory(7L, "t7", "Title 7", null, null, 1, 5.0f, mode = 9)

        verify(historyRepository).save(argThatK { it.gid == 7L && it.mode == 9 })
    }

    @Test
    fun `addHistory without mode defaults to 0`() {
        `when`(historyRepository.findByGid(8L)).thenReturn(null)

        historyService.addHistory(8L, "t8", "Title 8", null, null, 1, 5.0f)

        verify(historyRepository).save(argThatK { it.gid == 8L && it.mode == 0 })
    }

    @Test
    fun `addHistory on existing row updates mode`() {
        val existing = entity(9L, 1000).apply { mode = 5 }
        `when`(historyRepository.findByGid(9L)).thenReturn(existing)

        historyService.addHistory(9L, "t9", "Title 9", null, null, 1, 5.0f, mode = 7)

        assertEquals(7, existing.mode)
        verify(historyRepository).save(existing)
    }

    @Test
    fun `toItem returns the entity mode in list response`() {
        val e = entity(5L, 5000).apply { mode = 5 }
        `when`(historyRepository.findAllByOrderByTimeDesc()).thenReturn(listOf(e))

        val response = historyService.listHistory()

        assertEquals(5, response.history.single().mode)
    }

    @Test
    fun `listHistory hides tombstone rows deleted by sync`() {
        val live = entity(1L, 3000)
        val tombstone = entity(2L, 2000).apply { deleted = true }
        `when`(historyRepository.findAllByOrderByTimeDesc()).thenReturn(listOf(live, tombstone))

        val response = historyService.listHistory()

        assertEquals(listOf(1L), response.history.map { it.gid })
        assertEquals(1, response.total)
    }

    @Test
    fun `listHistory with only tombstones returns an empty list`() {
        val tombstone = entity(2L, 2000).apply { deleted = true }
        `when`(historyRepository.findAllByOrderByTimeDesc()).thenReturn(listOf(tombstone))

        val response = historyService.listHistory()

        assertTrue(response.history.isEmpty())
        assertEquals(0, response.total)
    }
}
