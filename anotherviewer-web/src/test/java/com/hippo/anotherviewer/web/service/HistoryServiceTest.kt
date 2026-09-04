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
    fun `addHistory rejects masked serial title on insert (PrivacyMask 脱敏防污染)`() {
        `when`(historyRepository.findByGid(12L)).thenReturn(null)

        historyService.addHistory(12L, "t12", "#12", null, null, 1, 5.0f)

        verify(historyRepository).save(argThatK { it.gid == 12L && it.title == null })
    }

    @Test
    fun `addHistory keeps real title on insert`() {
        `when`(historyRepository.findByGid(13L)).thenReturn(null)

        historyService.addHistory(13L, "t13", "(C99) Real Title", null, null, 1, 5.0f)

        verify(historyRepository).save(argThatK { it.gid == 13L && it.title == "(C99) Real Title" })
    }

    // ── S3: addHistory page 语义——null 保持已存值，非 null 原样写入（0=重读） ──

    @Test
    fun `addHistory with page persists it on a new row`() {
        `when`(historyRepository.findByGid(11L)).thenReturn(null)

        historyService.addHistory(11L, "t11", "Title 11", null, null, 1, 5.0f, page = 37)

        verify(historyRepository).save(argThatK { it.gid == 11L && it.page == 37 })
    }

    @Test
    fun `addHistory without page keeps the stored progress`() {
        val existing = entity(12L, 1000).apply { page = 42 }
        `when`(historyRepository.findByGid(12L)).thenReturn(existing)

        historyService.addHistory(12L, "t12", "Title 12", null, null, 1, 5.0f)

        assertEquals(42, existing.page)
        verify(historyRepository).save(existing)
    }

    @Test
    fun `addHistory with explicit page 0 rewrites the stored progress to zero`() {
        val existing = entity(13L, 1000).apply { page = 42 }
        `when`(historyRepository.findByGid(13L)).thenReturn(existing)

        historyService.addHistory(13L, "t13", "Title 13", null, null, 1, 5.0f, page = 0)

        assertEquals(0, existing.page)
        verify(historyRepository).save(existing)
    }

    @Test
    fun `addHistory clamps a negative page to zero`() {
        `when`(historyRepository.findByGid(14L)).thenReturn(null)

        historyService.addHistory(14L, "t14", "Title 14", null, null, 1, 5.0f, page = -3)

        verify(historyRepository).save(argThatK { it.gid == 14L && it.page == 0 })
    }

    @Test
    fun `toItem returns the entity page in list response`() {
        val e = entity(15L, 1000).apply { page = 9 }
        `when`(historyRepository.findAllByOrderByTimeDesc()).thenReturn(listOf(e))

        val response = historyService.listHistory()

        assertEquals(9, response.history.single().page)
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

    @Test
    fun `q filter runs in memory without pagination params`() {
        `when`(historyRepository.findAllByOrderByTimeDesc())
            .thenReturn(listOf(entity(3, 3000, "Futanari Story"), entity(2, 2000, "Plain"), entity(1, 1000, "Futa and More")))

        val response = historyService.listHistory(q = "futa")

        assertEquals(listOf(3L, 1L), response.history.map { it.gid })
        assertEquals(2, response.total)
        verify(historyRepository).findAllByOrderByTimeDesc()
        verify(historyRepository, never()).findHistoryPaged(any(Pageable::class.java))
    }

    @Test
    fun `q filter honours memory pagination with match count as total`() {
        // 仓库按时间倒序返回（最新在前），内存路径保持该顺序。
        `when`(historyRepository.findAllByOrderByTimeDesc())
            .thenReturn((5L downTo 1L).map { entity(it, it * 1000) })

        val response = historyService.listHistory(page = 1, pageSize = 2, q = "Title")

        // 0 基页码：drop(2).take(2)，total = 匹配数 5。
        assertEquals(listOf(3L, 2L), response.history.map { it.gid })
        assertEquals(5, response.total)
        verify(historyRepository, never()).findHistoryPaged(any(Pageable::class.java))
    }

    @Test
    fun `regex filter matches title or titleJpn`() {
        `when`(historyRepository.findAllByOrderByTimeDesc())
            .thenReturn(listOf(entity(1, 3000, "Plain", "フタナリ"), entity(2, 2000, "Futanari Story")))

        val response = historyService.listHistory(q = "(?i)^futa", regex = true)

        assertEquals(listOf(2L), response.history.map { it.gid })
        assertEquals(1, response.total)
    }

    @Test
    fun `invalid regex throws IllegalArgumentException`() {
        `when`(historyRepository.findAllByOrderByTimeDesc()).thenReturn(listOf(entity(1, 1000)))

        assertThrows(IllegalArgumentException::class.java) {
            historyService.listHistory(q = "(", regex = true)
        }
    }

    @Test
    fun `regex takes precedence over substring when regex is true`() {
        `when`(historyRepository.findAllByOrderByTimeDesc())
            .thenReturn(listOf(entity(1, 3000, "Title 1"), entity(2, 2000, "Plain")))

        // "T.tle" 作为子串不匹配任何 title；作为正则 T+任意字符+tle 命中 "Title 1"。
        val response = historyService.listHistory(q = "T.tle", regex = true)

        assertEquals(listOf(1L), response.history.map { it.gid })
        assertEquals(1, response.total)
    }

    @Test
    fun `regex true with blank q returns the full in-memory list`() {
        `when`(historyRepository.findAllByOrderByTimeDesc())
            .thenReturn(listOf(entity(3, 3000), entity(2, 2000), entity(1, 1000)))

        val response = historyService.listHistory(q = "  ", regex = true)

        assertEquals(listOf(3L, 2L, 1L), response.history.map { it.gid })
        assertEquals(3, response.total)
        verify(historyRepository, never()).findHistoryPaged(any(Pageable::class.java))
    }

    private fun entity(gid: Long, time: Long, title: String, titleJpn: String? = null): HistoryInfoEntity {
        val e = entity(gid, time)
        e.title = title
        e.titleJpn = titleJpn
        return e
    }
}
