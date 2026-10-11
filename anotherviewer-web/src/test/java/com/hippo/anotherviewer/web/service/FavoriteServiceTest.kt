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
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

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
        service = FavoriteService(
            repository,
            historyRepository,
            downloadRepository,
            HistoryService(historyRepository, stubProvider("test-user")),
            stubProvider("test-user"),
        )
    }

    /** A7-1: 纯 Mockito 单测不碰 SecurityContext——注入固定用户的 Provider stub。 */
    private fun stubProvider(name: String): com.hippo.anotherviewer.web.config.CurrentUsernameProvider =
        mock(com.hippo.anotherviewer.web.config.CurrentUsernameProvider::class.java)
            .apply { `when`(currentUsername()).thenReturn(name) }

    private fun savedEntity(): LocalFavoriteInfoEntity {
        val captor = ArgumentCaptor.forClass(LocalFavoriteInfoEntity::class.java)
        verify(repository).save(captureK<LocalFavoriteInfoEntity>(captor))
        return captor.value
    }

    @Test
    fun `category bitmask is never written to favoriteSlot`() {
        `when`(repository.findAllByGid(42L)).thenReturn(emptyList())

        service.addFavorite(42L, "token", "Title", 512)

        val entity = savedEntity()
        assertEquals(-1, entity.favoriteSlot)
        assertEquals(512, entity.category)
    }

    @Test
    fun `in-range slot is stored as-is`() {
        `when`(repository.findAllByGid(42L)).thenReturn(emptyList())

        service.addFavorite(42L, "token", "Title", 512, slot = 3)

        val entity = savedEntity()
        assertEquals(3, entity.favoriteSlot)
        assertEquals(512, entity.category)
    }

    @Test
    fun `slot above 9 is clamped to 9`() {
        `when`(repository.findAllByGid(42L)).thenReturn(emptyList())

        service.addFavorite(42L, "token", "Title", 0, slot = 999)

        assertEquals(9, savedEntity().favoriteSlot)
    }

    @Test
    fun `slot below -2 is clamped to -2`() {
        `when`(repository.findAllByGid(42L)).thenReturn(emptyList())

        service.addFavorite(42L, "token", "Title", 0, slot = -10)

        assertEquals(-2, savedEntity().favoriteSlot)
    }

    @Test
    fun `duplicate gid is rejected without save`() {
        `when`(repository.findAllByGid(42L)).thenReturn(listOf(LocalFavoriteInfoEntity()))

        assertFalse(service.addFavorite(42L, "token", "Title", 1))

        verify(repository, never()).save(any(LocalFavoriteInfoEntity::class.java))
    }

    // ── A7-1: 统一 stamping ──

    @Test
    fun `addFavorite rejects masked serial titles (privacy boundary)`() {
        // 打码开关只应影响响应展示，绝不让脱敏序列号落库（与历史回写同口径）：
        // 打码期间前端只持有 `#gid` 标题——拒收置空，列表按 #gid 兜底渲染。
        `when`(repository.findAllByGid(42L)).thenReturn(emptyList())
        `when`(historyRepository.findAllByGid(42L)).thenReturn(emptyList())
        `when`(downloadRepository.findAllByGid(42L)).thenReturn(emptyList())

        service.addFavorite(42L, "token", "#42", 512)

        val entity = savedEntity()
        assertNull(entity.title)
    }

    @Test
    fun `addFavorite stamps username and lastModified`() {
        `when`(repository.findAllByGid(42L)).thenReturn(emptyList())
        `when`(historyRepository.findAllByGid(42L)).thenReturn(emptyList())
        `when`(downloadRepository.findAllByGid(42L)).thenReturn(emptyList())

        service.addFavorite(42L, "token", "Title", 512)

        val entity = savedEntity()
        assertEquals("test-user", entity.username)
        assertTrue(entity.lastModified > 0)
        assertFalse(entity.deleted)
    }

    // ── A7-2: 软删墓碑化 + 复活语义 ──

    @Test
    fun `removeFavorite soft-deletes with lastModified bump and resets favoriteSlot`() {
        val row = LocalFavoriteInfoEntity().apply { gid = 42L; username = "test-user"; lastModified = 5L }
        `when`(repository.findAllByGid(42L)).thenReturn(listOf(row))
        `when`(historyRepository.findAllByGid(42L)).thenReturn(emptyList())
        `when`(downloadRepository.findAllByGid(42L)).thenReturn(emptyList())

        assertTrue(service.removeFavorite(42L))

        // 行仍在库（不再物理删），deleted=true + 水位 bump——增量 pull 才能把删除
        // 传播给 App（物理删会让 App 下次 push 静默重建）。
        assertTrue(row.deleted)
        assertTrue(row.lastModified > 5L)
        verify(repository, never()).delete(any(LocalFavoriteInfoEntity::class.java))
        verify(repository).save(row)
    }

    @Test
    fun `removeFavorite on an already tombstoned row is idempotent without another bump`() {
        val tombstone = LocalFavoriteInfoEntity().apply { gid = 42L; deleted = true; lastModified = 5L }
        `when`(repository.findAllByGid(42L)).thenReturn(listOf(tombstone))

        assertTrue(service.removeFavorite(42L))

        assertEquals(5L, tombstone.lastModified)
        verify(repository, never()).save(any(LocalFavoriteInfoEntity::class.java))
    }

    @Test
    fun `addFavorite resurrects a tombstoned row instead of rejecting`() {
        val tombstone = LocalFavoriteInfoEntity().apply {
            gid = 42L; token = "old"; title = "Old"; deleted = true; lastModified = 5L
        }
        `when`(repository.findAllByGid(42L)).thenReturn(listOf(tombstone))
        `when`(historyRepository.findAllByGid(42L)).thenReturn(emptyList())
        `when`(downloadRepository.findAllByGid(42L)).thenReturn(emptyList())

        assertTrue(service.addFavorite(42L, "new-token", "New Title", 512, slot = 3))

        // 复活语义（F3，对齐 mergeFavorite 的 incoming live 复活分支）：deleted→false
        // + 覆写业务字段 + stamp；活行仍拒绝（duplicate gid 测试钉住）。
        assertFalse(tombstone.deleted)
        assertEquals("new-token", tombstone.token)
        assertEquals("New Title", tombstone.title)
        assertEquals(3, tombstone.favoriteSlot)
        assertEquals("test-user", tombstone.username)
        assertTrue(tombstone.lastModified > 5L)
        verify(repository, never()).delete(any(LocalFavoriteInfoEntity::class.java))
    }

    @Test
    fun `favoriteSlot writeback skips tombstoned download rows`() {
        // D11：回写不得复活墓碑下载行。
        `when`(repository.findAllByGid(42L)).thenReturn(emptyList())
        `when`(historyRepository.findAllByGid(42L)).thenReturn(emptyList())
        `when`(downloadRepository.findAllByGid(42L))
            .thenReturn(listOf(com.hippo.anotherviewer.web.entity.DownloadInfoEntity().apply { gid = 42L; deleted = true }))

        assertTrue(service.addFavorite(42L, "token", "Title", 512))

        verify(downloadRepository, never()).save(any(com.hippo.anotherviewer.web.entity.DownloadInfoEntity::class.java))
    }

    // ── 任务 D：favoriteSlot 回写来源历史行（详情页收藏态数据源） ──

    @Test
    fun `addFavorite writes the slot back to the existing history row`() {
        // 详情读取链（GalleryService 历史分支）优先历史行，不回写则重进详情
        // favoriteSlot 恒 -2。回写值须与收藏行一致（含夹紧后的 slot）。
        `when`(repository.findAllByGid(42L)).thenReturn(emptyList())
        `when`(historyRepository.findAllByGid(42L))
            .thenReturn(listOf(com.hippo.anotherviewer.web.entity.HistoryInfoEntity().apply { gid = 42L }))

        service.addFavorite(42L, "token", "Title", 512, slot = 999)

        val captor = ArgumentCaptor.forClass(com.hippo.anotherviewer.web.entity.HistoryInfoEntity::class.java)
        verify(historyRepository).save(captureK<com.hippo.anotherviewer.web.entity.HistoryInfoEntity>(captor))
        assertEquals(9, captor.value.favoriteSlot) // 999 夹紧到 9，与收藏行一致
    }

    @Test
    fun `removeFavorite resets the history row favoriteSlot to -2`() {
        // 对称清除：取消收藏后重进详情不残留收藏态（置回未收藏）。
        `when`(repository.findAllByGid(42L)).thenReturn(listOf(LocalFavoriteInfoEntity()))
        `when`(historyRepository.findAllByGid(42L))
            .thenReturn(listOf(com.hippo.anotherviewer.web.entity.HistoryInfoEntity().apply { gid = 42L; favoriteSlot = 3 }))

        assertTrue(service.removeFavorite(42L))

        val captor = ArgumentCaptor.forClass(com.hippo.anotherviewer.web.entity.HistoryInfoEntity::class.java)
        verify(historyRepository).save(captureK<com.hippo.anotherviewer.web.entity.HistoryInfoEntity>(captor))
        assertEquals(-2, captor.value.favoriteSlot)
    }

    @Test
    fun `addFavorite without a history row does not create one`() {
        // 收藏不凭空造历史：无历史行仅记日志，historyRepository.save 不发生。
        `when`(repository.findAllByGid(42L)).thenReturn(emptyList())
        `when`(historyRepository.findAllByGid(42L)).thenReturn(emptyList())

        assertTrue(service.addFavorite(42L, "token", "Title", 512))

        verify(historyRepository, never()).save(any(com.hippo.anotherviewer.web.entity.HistoryInfoEntity::class.java))
        verify(repository).save(any(LocalFavoriteInfoEntity::class.java)) // 收藏行本身照常落库
    }

    @Test
    fun `addFavorite writes the slot back to an existing download row`() {
        // 详情读取链 download 分支优先于 history 分支：已下载画廊的详情/下载
        // 列表以 download 行为 favoriteSlot 来源，同样必须回写。
        `when`(repository.findAllByGid(42L)).thenReturn(emptyList())
        `when`(historyRepository.findAllByGid(42L)).thenReturn(emptyList())
        `when`(downloadRepository.findAllByGid(42L))
            .thenReturn(listOf(com.hippo.anotherviewer.web.entity.DownloadInfoEntity().apply { gid = 42L }))

        service.addFavorite(42L, "token", "Title", 512, slot = 999)

        val captor = ArgumentCaptor.forClass(com.hippo.anotherviewer.web.entity.DownloadInfoEntity::class.java)
        verify(downloadRepository).save(captureK<com.hippo.anotherviewer.web.entity.DownloadInfoEntity>(captor))
        assertEquals(9, captor.value.favoriteSlot) // 999 夹紧到 9，与收藏行一致
    }

    @Test
    fun `removeFavorite resets the download row favoriteSlot to -2`() {
        `when`(repository.findAllByGid(42L)).thenReturn(listOf(LocalFavoriteInfoEntity()))
        `when`(historyRepository.findAllByGid(42L)).thenReturn(emptyList())
        `when`(downloadRepository.findAllByGid(42L))
            .thenReturn(listOf(com.hippo.anotherviewer.web.entity.DownloadInfoEntity().apply { gid = 42L; favoriteSlot = 3 }))

        assertTrue(service.removeFavorite(42L))

        val captor = ArgumentCaptor.forClass(com.hippo.anotherviewer.web.entity.DownloadInfoEntity::class.java)
        verify(downloadRepository).save(captureK<com.hippo.anotherviewer.web.entity.DownloadInfoEntity>(captor))
        assertEquals(-2, captor.value.favoriteSlot)
    }

    @Test
    fun `addFavorite without download row does not touch download repository`() {
        `when`(repository.findAllByGid(42L)).thenReturn(emptyList())
        `when`(historyRepository.findAllByGid(42L)).thenReturn(emptyList())
        `when`(downloadRepository.findAllByGid(42L)).thenReturn(emptyList())

        assertTrue(service.addFavorite(42L, "token", "Title", 512))

        verify(downloadRepository, never()).save(any(com.hippo.anotherviewer.web.entity.DownloadInfoEntity::class.java))
    }

    // ── listFavorites（P2: slot/q 过滤 + 分页下沉 DB，不再不分页全表载入）──
    // 槽位/墓碑/q 的筛选语义现由仓储 JPQL（findLiveBySlot*）承载；单元层钉住
    // 参数转发、条目映射与信封兼容，JPQL 语义由 SqliteIndexDdlTest 的 JPA 切片
    // 启动做语法校验。

    @Test
    fun `listFavorites with slot 0 returns only the default folder (-1 and 0)`() {
        // F-UX5: tab 0 对齐 app FavoritesScene 首签——默认夹（-1）与显式
        // Favorites 0（0）同列，自定义夹（5）不再混入（JPQL in (-1,0) 筛选）。
        val pageable = PageRequest.of(0, 20)
        `when`(repository.findLiveBySlotPaged(0, pageable))
            .thenReturn(PageImpl(listOf(favEntity(1, 0), favEntity(2, -1)), pageable, 2))

        val response = service.listFavorites(0, 1, 20)

        assertEquals(listOf(1L, 2L), response.favorites.map { it.gid })
        assertEquals(2, response.favorites.size)
    }

    @Test
    fun `listFavorites items carry the row's real favoriteSlot`() {
        // F-UX5: ♥ 徽章数据源——条目随行携带真实 slot，前端不再退回页签号。
        val pageable = PageRequest.of(0, 20)
        `when`(repository.findLiveBySlotPaged(0, pageable))
            .thenReturn(PageImpl(listOf(favEntity(1, 0), favEntity(2, -1)), pageable, 2))
        `when`(repository.findLiveBySlotPaged(5, pageable))
            .thenReturn(PageImpl(listOf(favEntity(3, 5)), pageable, 1))

        val response = service.listFavorites(0, 1, 20)

        assertEquals(listOf(0, -1), response.favorites.map { it.favoriteSlot })
        assertEquals(5, service.listFavorites(5, 1, 20).favorites.single().favoriteSlot)
    }

    @Test
    fun `listFavorites items carry readProgress from history rows`() {
        // 阅读进度角标数据源：同 gid 历史行的 page 批量填充（findByGidIn 单次），
        // 无历史行的条目为 null。
        val pageable = PageRequest.of(0, 20)
        `when`(repository.findLiveBySlotPaged(-1, pageable))
            .thenReturn(PageImpl(listOf(favEntity(1, 0), favEntity(2, -1)), pageable, 2))
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
        val pageable = PageRequest.of(0, 20)
        `when`(repository.findLiveBySlotPaged(5, pageable))
            .thenReturn(PageImpl(listOf(favEntity(3, 5)), pageable, 1))

        val response = service.listFavorites(5, 1, 20)

        assertEquals(listOf(3L), response.favorites.map { it.gid })
    }

    @Test
    fun `listFavorites with negative slot returns all slots`() {
        val pageable = PageRequest.of(0, 20)
        `when`(repository.findLiveBySlotPaged(-1, pageable))
            .thenReturn(PageImpl(listOf(favEntity(1, 0), favEntity(2, -1), favEntity(3, 5)), pageable, 3))

        val response = service.listFavorites(-1, 1, 20)

        assertEquals(3, response.favorites.size)
    }

    @Test
    fun `listFavorites hides tombstone rows deleted by sync`() {
        // 墓碑行（deleted=true 的同步删除记录）由 JPQL deleted = false 排除——
        // mock 只回存活行，钉住「墓碑不进 REST 列表」契约（对齐 HistoryService）。
        val pageable = PageRequest.of(0, 20)
        `when`(repository.findLiveBySlotPaged(0, pageable))
            .thenReturn(PageImpl(listOf(favEntity(1, 0)), pageable, 1))

        val response = service.listFavorites(0, 1, 20)

        assertEquals(listOf(1L), response.favorites.map { it.gid })
        assertEquals(1, response.favorites.size)
    }

    @Test
    fun `listFavorites tombstones do not count into pagination`() {
        // total/分页只按存活行计（R4-17）：全夹墓碑 → 空页 + totalPages 0。
        val pageable = PageRequest.of(0, 20)
        `when`(repository.findLiveBySlotPaged(3, pageable))
            .thenReturn(PageImpl(emptyList(), pageable, 0))

        val response = service.listFavorites(3, 1, 20)

        assertTrue(response.favorites.isEmpty())
        assertEquals(0, response.totalPages)
    }

    @Test
    fun `listFavorites with only tombstones returns an empty list`() {
        val pageable = PageRequest.of(0, 20)
        `when`(repository.findLiveBySlotPaged(0, pageable))
            .thenReturn(PageImpl(emptyList(), pageable, 0))

        val response = service.listFavorites(0, 1, 20)

        assertTrue(response.favorites.isEmpty())
        assertEquals(0, response.totalPages)
    }

    @Test
    fun `listFavorites q filters by case-insensitive substring on title`() {
        // q 下沉 DB（LIKE）：total/分页按匹配后行数计。
        val pageable = PageRequest.of(0, 20)
        `when`(repository.findLiveBySlotAndTitlePaged(0, "futa", pageable))
            .thenReturn(
                PageImpl(
                    listOf(favEntity(1, 0, "Futanari Story"), favEntity(3, 0, "Futa and More")),
                    pageable,
                    2,
                )
            )

        val response = service.listFavorites(0, 1, 20, q = "futa")

        assertEquals(listOf(1L, 3L), response.favorites.map { it.gid })
        assertEquals(2, response.favorites.size)
        assertEquals(1, response.totalPages)
    }

    @Test
    fun `listFavorites q matches titleJpn and keeps slot filter`() {
        // slot 过滤先行（与 q 同一条 JPQL）：仅默认夹（0）→ q 命中 titleJpn。
        val pageable = PageRequest.of(0, 20)
        `when`(repository.findLiveBySlotAndTitlePaged(0, "フタナリ", pageable))
            .thenReturn(PageImpl(listOf(favEntity(1, 0, "Plain", "フタナリ")), pageable, 1))

        val response = service.listFavorites(0, 1, 20, q = "フタナリ")

        assertEquals(listOf(1L), response.favorites.map { it.gid })
        assertEquals(1, response.totalPages)
    }

    @Test
    fun `listFavorites regex matches title`() {
        // regex 保内存语义，但只跑在 DB 预过滤集上：slot 已下沉 + 字面种子
        // "futa"（来自 (?i)^futa）LIKE 窗口——不触发不分页全表载入。
        `when`(repository.findLiveBySlotAndTitle(-1, "futa"))
            .thenReturn(listOf(favEntity(1, 0, "Futanari Story"), favEntity(2, 0, "Plain")))

        val response = service.listFavorites(-1, 1, 20, q = "(?i)^futa", regex = true)

        assertEquals(listOf(1L), response.favorites.map { it.gid })
        assertEquals(1, response.totalPages)
        verify(repository, never()).findAllByOrderByTimeDesc()
    }

    @Test
    fun `listFavorites invalid regex throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.listFavorites(0, 1, 20, q = "(", regex = true)
        }
        // 非法正则在进 DB 之前抛出，不触发任何查询。
        verify(repository, never()).findAllByOrderByTimeDesc()
        verify(repository, never()).findLiveBySlot(0)
    }

    @Test
    fun `listFavorites regex takes precedence over substring when regex is true`() {
        // 种子 "tle"（来自 T.tle）做 DB 预过滤；内存 regex 精确过滤——
        // "T.tle" 作为子串不匹配任何 title，作为正则命中 "Title 1"。
        `when`(repository.findLiveBySlotAndTitle(0, "tle"))
            .thenReturn(listOf(favEntity(1, 0, "Title 1"), favEntity(2, 0, "Plain")))

        val response = service.listFavorites(0, 1, 20, q = "T.tle", regex = true)

        assertEquals(listOf(1L), response.favorites.map { it.gid })
    }

    @Test
    fun `listFavorites blank q falls back to unfiltered list`() {
        val pageable = PageRequest.of(0, 20)
        `when`(repository.findLiveBySlotPaged(0, pageable))
            .thenReturn(PageImpl(listOf(favEntity(1, 0), favEntity(2, 0)), pageable, 2))

        val response = service.listFavorites(0, 1, 20, q = "   ")

        assertEquals(listOf(1L, 2L), response.favorites.map { it.gid })
        assertEquals(1, response.totalPages)
    }

    @Test
    fun `listFavorites envelope stays backward compatible and gains page pageSize total`() {
        // W2-B2: 信封向后兼容——favorites/totalPages/currentPage 原样保留，
        // 新增 page/pageSize/total（W3-F4 前端切换消费，旧客户端忽略不受影响）。
        // 页大小 2 + 2 条内容 + total 7：PageImpl 对「页装得下全集」的声明会
        // 把 total 钳到 offset+content.size，故取部分页让 7 语义成立。
        val pageable = PageRequest.of(0, 2)
        `when`(repository.findLiveBySlotPaged(-1, pageable))
            .thenReturn(PageImpl(listOf(favEntity(1, 0), favEntity(2, 0)), pageable, 7))

        val response = service.listFavorites(-1, 1, 2)

        assertEquals(2, response.favorites.size) // data 数组保留
        assertEquals(4, response.totalPages)     // 既有字段语义不变
        assertEquals(1, response.currentPage)
        assertEquals(1, response.page)           // 新增
        assertEquals(2, response.pageSize)
        assertEquals(7, response.total)
    }

    @Test
    fun `listFavorites beyond page one uses a 0-based DB page index`() {
        // 1 起契约页码 → PageRequest.of(page - 1, size)（page=2 → 索引 1）。
        val pageable = PageRequest.of(1, 2)
        `when`(repository.findLiveBySlotPaged(-1, pageable))
            .thenReturn(PageImpl(listOf(favEntity(2, 0), favEntity(3, 5)), pageable, 5))

        val response = service.listFavorites(-1, 2, 2)

        assertEquals(listOf(2L, 3L), response.favorites.map { it.gid })
        assertEquals(2, response.currentPage)
        assertEquals(3, response.totalPages)
        assertEquals(2, response.page)
        assertEquals(2, response.pageSize)
    }

    @Test
    fun `listFavorites never loads the unpaged full table`() {
        // P2 结构性断言：万行级场景下不允许出现不分页全表载入——
        // 无 q 路径必须走 DB 分页查询（findLiveBySlotPaged）。
        val pageable = PageRequest.of(0, 20)
        `when`(repository.findLiveBySlotPaged(-1, pageable))
            .thenReturn(PageImpl(emptyList(), pageable, 0))

        service.listFavorites(-1, 1, 20)

        verify(repository, never()).findAllByOrderByTimeDesc()
        verify(repository).findLiveBySlotPaged(-1, pageable)
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
