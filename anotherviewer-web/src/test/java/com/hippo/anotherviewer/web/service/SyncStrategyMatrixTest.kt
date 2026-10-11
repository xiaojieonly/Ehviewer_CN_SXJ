package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.ConflictStrategy
import com.hippo.anotherviewer.web.dto.SyncBookmarkDto
import com.hippo.anotherviewer.web.dto.SyncDownloadDto
import com.hippo.anotherviewer.web.dto.SyncDownloadLabelDto
import com.hippo.anotherviewer.web.dto.SyncEntityCollection
import com.hippo.anotherviewer.web.dto.SyncFavoriteDto
import com.hippo.anotherviewer.web.dto.SyncFilterDto
import com.hippo.anotherviewer.web.dto.SyncHistoryDto
import com.hippo.anotherviewer.web.dto.SyncPolicyDto
import com.hippo.anotherviewer.web.dto.SyncPushRequest
import com.hippo.anotherviewer.web.dto.SyncQuickSearchDto
import com.hippo.anotherviewer.web.entity.BookmarkInfoEntity
import com.hippo.anotherviewer.web.entity.DownloadInfoEntity
import com.hippo.anotherviewer.web.entity.DownloadLabelEntity
import com.hippo.anotherviewer.web.entity.FilterEntity
import com.hippo.anotherviewer.web.entity.HistoryInfoEntity
import com.hippo.anotherviewer.web.entity.LocalFavoriteInfoEntity
import com.hippo.anotherviewer.web.entity.QuickSearchEntity
import com.hippo.anotherviewer.web.entity.ServerConfigEntity
import com.hippo.anotherviewer.web.entity.SyncDeviceEntity
import com.hippo.anotherviewer.web.entity.UserPreferenceEntity
import com.hippo.anotherviewer.web.repository.BookmarkInfoRepository
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import com.hippo.anotherviewer.web.repository.DownloadLabelRepository
import com.hippo.anotherviewer.web.repository.EhSessionRepository
import com.hippo.anotherviewer.web.repository.FilterRepository
import com.hippo.anotherviewer.web.repository.HistoryInfoRepository
import com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository
import com.hippo.anotherviewer.web.repository.QuickSearchRepository
import com.hippo.anotherviewer.web.repository.ServerConfigRepository
import com.hippo.anotherviewer.web.repository.SyncDeviceRepository
import com.hippo.anotherviewer.web.repository.UserPreferenceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream

/**
 * 契约 v4 行为矩阵（contracts/sync-conflict-rules.md §3.8/§5.1）：
 * 3 策略（device_priority A / lww B / web_priority C）× 7 实体 × 六行分歧逐案实测。
 *
 * v4 仲裁基线：push 入口把整批入参重打戳为服务器到达戳（§1.3，每请求一个），
 * 全部比较两侧都是服务器时钟域（客户端上送戳不落库、不参与仲裁）。因此：
 *  - 单线程内任何一次 push 对其键必然赢得与既有行的合并（到达戳 >= 既有戳）；
 *    只有「后到达的 push」能胜过「先到达的 push」——本类期望结果全部按到达序表达。
 *  - B（lww，v4 默认）无 skew 窗口、无实体 tie-break：删除/复活同样按到达序（§3.8）。
 *  - A/C 跨平台同键无条件平台序胜（不变）；A/C 同平台回退保留 v1 判据（±5s 窗口 +
 *    实体 tie-break），但窗口两侧同为到达戳——相邻两次 push 必落在窗口内。
 *
 * 六行分歧（v4 表达）：
 *  1. 同键同时编辑（双活）：A=android 记录胜 / B=后到者胜 / C=web 记录胜
 *  2. 优先端删 / 非优先端保留：墓碑后到=删除传播；非优先端 live 后到仅 LWW 下复活
 *  3. 非优先端删 / 优先端保留：A/C 保留；B=后到者胜
 *  4. 各删不同键·tombstone 实体（history/bookmark）：全策略双向传播
 *  5. 各删不同键·soft 实体：墓碑后到即传播（A/C 优先端删无条件传播；非优先端删在
 *     优先端不持有时传播；LWW 按到达序）
 *  6. 不相交新增：全策略 union
 *
 * 平台判定 = deviceId 前缀（§1.4）：`android-*` → android，其余 → web 侧。
 * 存量行的平台由 push 落库的行级来源（provenance）决定。
 */
class SyncStrategyMatrixTest {

    enum class Kind(val tombstoneClass: Boolean) {
        FAVORITE(false),
        HISTORY(true),
        DOWNLOAD(false),
        BOOKMARK(true),
        FILTER(false),
        QUICK_SEARCH(false),
        DOWNLOAD_LABEL(false),
    }

    private lateinit var favoriteRepo: LocalFavoriteInfoRepository
    private lateinit var historyRepo: HistoryInfoRepository
    private lateinit var downloadRepo: DownloadInfoRepository
    private lateinit var bookmarkRepo: BookmarkInfoRepository
    private lateinit var filterRepo: FilterRepository
    private lateinit var quickSearchRepo: QuickSearchRepository
    private lateinit var downloadLabelRepo: DownloadLabelRepository
    private lateinit var service: SyncService

    private val android1 = "android-phone-1"
    private val android2 = "android-phone-2"
    private val web1 = "web-browser-1"

    @BeforeEach
    fun setUp() {
        favoriteRepo = fakeFavoriteRepo()
        historyRepo = fakeHistoryRepo()
        downloadRepo = fakeDownloadRepo()
        bookmarkRepo = fakeBookmarkRepo()
        filterRepo = fakeFilterRepo()
        quickSearchRepo = fakeQuickSearchRepo()
        downloadLabelRepo = fakeDownloadLabelRepo()
        val preferenceRepo = fakePreferenceRepo()
        service = SyncService(
            favoriteRepo, historyRepo, downloadRepo, bookmarkRepo, filterRepo,
            quickSearchRepo, downloadLabelRepo, fakeDeviceRepo(), preferenceRepo,
            UserPreferenceService(preferenceRepo, fakeServerConfig()), fakeServerConfig(),
            mock(EhSessionRepository::class.java),
            mock(SiteSessionManager::class.java),
            syncClock,
            eventPublisher,
        )
    }

    /** v4：服务器统一打戳时钟 + 事件通道（matrix 用例断言均不依赖事件）。 */
    private val syncClock = SyncClock()
    private val eventPublisher: org.springframework.context.ApplicationEventPublisher =
        mock(org.springframework.context.ApplicationEventPublisher::class.java)

    private fun setStrategy(strategy: ConflictStrategy) =
        service.updatePolicy(SyncPolicyDto(conflictStrategy = strategy))

    /**
     * v4：客户端上送戳不落库、不参与任何仲裁（push 入口以服务器到达戳覆盖全部入参），
     * 本工具的取值仅作为 fixtures 的传输元数据/诊断字段保留——「更新」与否都不改变胜负。
     */
    private fun newer(): Long = System.currentTimeMillis() + 1_000

    /** 陈旧客户端戳（同上：v4 下仅传输元数据——「旧副本」是否落败只看到达序）。 */
    private fun stale(): Long = System.currentTimeMillis() - 10_000

    // ==================== 行 1：同键同时编辑（双活） ====================

    @ParameterizedTest(name = "[{0}] {1}")
    @MethodSource("strategyKindMatrix")
    fun `row1 same-key simultaneous edit`(strategy: ConflictStrategy, kind: Kind) {
        setStrategy(strategy)
        // android 先写 v1，web 后写 v2（同时编辑；两戳都用真实时钟=后写晚于前写）
        pushLive(kind, device = android1, key = 1, lm = newer(), version = 1)
        pushLive(kind, device = web1, key = 1, lm = newer(), version = 2)

        val (deleted, version) = readState(kind, 1)
        assertFalse(deleted, "双活不应产生墓碑")
        val expected = when (strategy) {
            ConflictStrategy.DEVICE_PRIORITY -> 1 // android 记录无条件胜
            ConflictStrategy.WEB_PRIORITY -> 2    // web 记录无条件胜
            // v4：纯服务器戳 LWW——后到（web v2，戳不旧于存量）整行胜；
            // 旧 skew 窗内的实体 tie-break（time/page/enabled bias）已移除。
            ConflictStrategy.LWW -> 2
        }
        assertEquals(expected, version, "strategy=$strategy kind=$kind")
    }

    // ==================== 行 2：优先端删 / 非优先端保留 ====================

    @ParameterizedTest(name = "[{0}] {1}")
    @MethodSource("strategyKindMatrix")
    fun `row2 priority side deletes, non-priority keeps`(strategy: ConflictStrategy, kind: Kind) {
        setStrategy(strategy)
        val prio = if (strategy == ConflictStrategy.WEB_PRIORITY) web1 else android1
        val other = if (prio == web1) android1 else web1

        // v4 到达戳：删除是否传播只看到达序（客户端上送戳不参与仲裁）。两个键覆盖两个到达序：
        // K1 —— 墓碑后到（优先端删除落在最后）→ 删除传播（三策略一致）。
        pushLive(kind, device = prio, key = 1, lm = newer(), version = 1)   // 优先端持有该键
        pushTomb(kind, device = prio, key = 1, lm = newer())                // 优先端删除（后到）
        assertTrue(readState(kind, 1).first, "墓碑后到 → 删除传播: strategy=$strategy kind=$kind")

        // K2 —— 非优先端仍持有旧副本并后到推送 live：
        //  LWW：到达戳胜 → 复活（保留）——「旧副本」在 v4 下同样凭到达序胜出；
        //  A/C：优先端删除无条件传播，非优先端（后到）live 也不复活。
        pushLive(kind, device = prio, key = 2, lm = newer(), version = 1)   // 优先端持有该键
        pushTomb(kind, device = prio, key = 2, lm = newer())                // 优先端删除
        pushLive(kind, device = other, key = 2, lm = stale(), version = 1)  // 非优先端旧副本后到
        val deletedK2 = readState(kind, 2).first
        if (strategy == ConflictStrategy.LWW) {
            assertFalse(deletedK2, "lww：后到的 live 复活墓碑（到达序胜）: kind=$kind")
        } else {
            assertTrue(deletedK2, "A/C：优先端删除无条件传播（非优先端 live 不复活）: strategy=$strategy kind=$kind")
        }
    }

    // ==================== 行 3：非优先端删 / 优先端保留 ====================

    @ParameterizedTest(name = "[{0}] {1}")
    @MethodSource("strategyKindMatrix")
    fun `row3 non-priority side deletes, priority keeps`(strategy: ConflictStrategy, kind: Kind) {
        setStrategy(strategy)
        val keeper = if (strategy == ConflictStrategy.WEB_PRIORITY) web1 else android1
        val deleter = if (keeper == web1) android1 else web1

        pushLive(kind, device = keeper, key = 1, lm = newer(), version = 1) // 优先端持有该键
        pushTomb(kind, device = deleter, key = 1, lm = newer())             // 非优先端删除（戳晚于持有）

        val (deleted, version) = readState(kind, 1)
        when {
            kind.tombstoneClass ->
                assertTrue(deleted, "tombstone 实体删除任何策略下传播: strategy=$strategy kind=$kind")
            strategy == ConflictStrategy.LWW ->
                // v4：LWW 无平台序，新墓碑按戳胜旧活行（删除传播）。
                assertTrue(deleted, "lww：新墓碑按戳胜旧活行: kind=$kind")
            else -> {
                assertFalse(deleted, "非优先端删除 vs 优先端持有 → 保留: strategy=$strategy kind=$kind")
                assertEquals(1, version)
            }
        }
    }

    // ==================== 行 4：各删不同键·tombstone 实体 ====================

    @ParameterizedTest(name = "[{0}] {1}")
    @MethodSource("strategyTombstoneMatrix")
    fun `row4 each deletes a different key, tombstone entities propagate both ways`(
        strategy: ConflictStrategy,
        kind: Kind,
    ) {
        setStrategy(strategy)
        pushLive(kind, device = android1, key = 1, lm = newer(), version = 1)
        pushLive(kind, device = android1, key = 2, lm = newer(), version = 1)

        pushTomb(kind, device = android1, key = 1, lm = newer()) // android 删 K1
        pushTomb(kind, device = web1, key = 2, lm = newer())     // web 删 K2

        assertTrue(readState(kind, 1).first, "K1 墓碑应传播: strategy=$strategy kind=$kind")
        assertTrue(readState(kind, 2).first, "K2 墓碑应传播: strategy=$strategy kind=$kind")
        // 增量 pull 能取到两个墓碑（bump lastModified 的意义）
        val pulled = service.pull(1_500, "A", android1)
        assertEquals(setOf(1L, 2L), pulledGids(pulled.entities, kind), "增量 pull 应含两个墓碑")
    }

    // ==================== 行 5：各删不同键·soft 实体 ====================

    @ParameterizedTest(name = "[{0}] {1}")
    @MethodSource("strategySoftMatrix")
    fun `row5 each deletes a different key, soft entities`(strategy: ConflictStrategy, kind: Kind) {
        setStrategy(strategy)
        // P0 细化（契约 §3.8）：优先端删除无条件传播；非优先端删除在优先端不持有该键时传播。
        // 构造：K1 = 优先端持有（由优先端写入）；K2 = 仅非优先端持有（优先端不持有）。
        val prio = if (strategy == ConflictStrategy.WEB_PRIORITY) web1 else android1
        val other = if (prio == web1) android1 else web1

        pushLive(kind, device = prio, key = 1, lm = newer(), version = 1)
        pushLive(kind, device = other, key = 2, lm = newer(), version = 1)

        // v4：LWW 下墓碑按戳参与仲裁（新墓碑胜旧活行）→ 两键删除都传播；
        // A/C：优先端删自己持有的 K1 → 传播；非优先端删优先端不持有的 K2 → 传播。
        // 三策略结论一致：双向传播。
        pushTomb(kind, device = prio, key = 1, lm = newer())
        pushTomb(kind, device = other, key = 2, lm = newer())
        assertTrue(readState(kind, 1).first, "K1 删除应传播: strategy=$strategy kind=$kind")
        assertTrue(readState(kind, 2).first, "K2 删除应传播: strategy=$strategy kind=$kind")
    }

    // ==================== 行 6：不相交新增 ====================

    @ParameterizedTest(name = "[{0}] {1}")
    @MethodSource("strategyKindMatrix")
    fun `row6 disjoint adds union`(strategy: ConflictStrategy, kind: Kind) {
        setStrategy(strategy)
        pushLive(kind, device = android1, key = 1, lm = 1_000, version = 1)
        pushLive(kind, device = web1, key = 2, lm = 1_100, version = 2)

        val k1 = readState(kind, 1)
        val k2 = readState(kind, 2)
        assertFalse(k1.first, "K1 应存在")
        assertFalse(k2.first, "K2 应存在")
        assertEquals(1, k1.second)
        assertEquals(2, k2.second)
    }

    // ==================== §3.8 镜像复活 & 同平台回退 & 切换语义 ====================

    @ParameterizedTest(name = "[{0}] {1}")
    @MethodSource("strategySoftMatrix")
    fun `resurrection mirror for soft entities`(strategy: ConflictStrategy, kind: Kind) {
        setStrategy(strategy)
        val prio = if (strategy == ConflictStrategy.WEB_PRIORITY) web1 else android1
        val other = if (prio == web1) android1 else web1

        // 优先端 live 胜非优先端 tomb
        pushTomb(kind, device = other, key = 1, lm = newer())               // 非优先端先删（落墓碑）
        pushLive(kind, device = prio, key = 1, lm = newer(), version = 1)   // 优先端 live（戳不旧于墓碑）
        val afterPrioLive = readState(kind, 1)
        if (strategy == ConflictStrategy.LWW) {
            assertFalse(afterPrioLive.first, "lww：戳更新的 live 复活墓碑")
        } else {
            assertFalse(afterPrioLive.first, "优先端 live 胜非优先端 tomb: strategy=$strategy kind=$kind")
        }

        // 非优先端 live vs 优先端 tomb：A/C 下 tomb 胜（保留删除）；LWW 下按戳（live 更新即复活）
        pushTomb(kind, device = prio, key = 2, lm = newer())                // 优先端先删（落墓碑）
        pushLive(kind, device = other, key = 2, lm = newer(), version = 1)  // 非优先端 live（戳不旧于墓碑）
        val afterOtherLive = readState(kind, 2)
        if (strategy == ConflictStrategy.LWW) {
            assertFalse(afterOtherLive.first, "lww：戳更新的 live 复活墓碑")
        } else {
            assertTrue(afterOtherLive.first, "非优先端 live vs 优先端 tomb → tomb 胜: strategy=$strategy kind=$kind")
        }
    }

    @ParameterizedTest(name = "[{0}] {1}")
    @MethodSource("strategyTombstoneMatrix")
    fun `tombstone entity resurrection follows the arrival order (v4)`(strategy: ConflictStrategy, kind: Kind) {
        setStrategy(strategy)
        // v4 到达戳：复活/删除只由 push 先后决定（客户端上送戳不参与仲裁）。
        // ① 墓碑先到、live 后到 → 复活（任何策略）；后到的 live 客户戳陈旧（stale）同样复活
        //    ——旧语义「明显更旧的 live 不复活」废除，唯一变量是到达序。
        pushTomb(kind, device = android1, key = 1, lm = newer())
        pushLive(kind, device = web1, key = 1, lm = newer(), version = 2)
        assertFalse(readState(kind, 1).first, "后到 live 应复活 tombstone 实体: strategy=$strategy kind=$kind")

        pushTomb(kind, device = android1, key = 2, lm = newer())
        pushLive(kind, device = web1, key = 2, lm = stale(), version = 1) // 客户戳陈旧不阻后到复活
        assertFalse(readState(kind, 2).first, "v4：stale 客户戳不阻后到 live 复活: strategy=$strategy kind=$kind")

        // ② 到达序对照（交换先后）：live 先到、墓碑后到 → 删除传播（保持墓碑）。
        pushLive(kind, device = web1, key = 3, lm = stale(), version = 1)
        pushTomb(kind, device = android1, key = 3, lm = newer())
        assertTrue(readState(kind, 3).first, "后到墓碑压 live（删除传播）: strategy=$strategy kind=$kind")
    }

    @ParameterizedTest(name = "[{0}] {1}")
    @MethodSource("strategyACPairs")
    fun `same platform same key falls back to LWW under A and C`(strategy: ConflictStrategy, kind: Kind) {
        setStrategy(strategy)
        // 两个 android 设备同键（同平台）：A/C 的平台序不适用 → 回退判据。
        // v4：两侧 lastModified 均为服务器到达戳（相邻两次 push 的到达戳必落在 ±5s 回退窗口内），
        // 客户戳（newer()/+61_000）只是传输元数据——旧用例「客户戳超出 skew 即胜」在 v4 不可达，
        // 改为到达序对照。
        pushLive(kind, device = android1, key = 1, lm = newer(), version = 1)
        pushLive(kind, device = android2, key = 1, lm = newer() + 61_000, version = 2)
        // 窗口内回退：union 实体保留先到记录（先到者胜，不做无条件平台覆盖）；
        // history/bookmark/filter 走实体 tie-break（time/page/enabled 更高者胜）。
        val expectedFirstArrival = when (kind) {
            Kind.HISTORY, Kind.BOOKMARK, Kind.FILTER -> 2
            else -> 1
        }
        assertEquals(expectedFirstArrival, readState(kind, 1).second, "同平台回退（v1 先到）: strategy=$strategy kind=$kind")

        // 到达序对照（交换先后，key=3 由 v2 先到）：union 实体仍保留先到记录（v2）；
        // tie-break 实体内容更高者（v2）保留——结论随后续到达序/内容，而非「后到即胜」。
        pushLive(kind, device = android2, key = 3, lm = newer() + 61_000, version = 2)
        pushLive(kind, device = android1, key = 3, lm = newer(), version = 1)
        assertEquals(2, readState(kind, 3).second, "同平台回退（v2 先到）: strategy=$strategy kind=$kind")
    }

    @ParameterizedTest(name = "[{0}]")
    @MethodSource("softKinds")
    fun `strategy switch applies to subsequent merges without retroactive re-merge`(kind: Kind) {
        // LWW（v4 默认）：android 写活行后 web 写墓碑（戳更新）→ 新墓碑胜旧活行，删除传播。
        setStrategy(ConflictStrategy.LWW)
        pushLive(kind, device = android1, key = 1, lm = newer(), version = 1)
        pushTomb(kind, device = web1, key = 1, lm = newer())
        assertTrue(readState(kind, 1).first, "lww：新墓碑胜旧活行（删除传播）")

        // 切换到 A：即时生效于后续 merge，但不追溯重合并（已收敛的墓碑不会自动回滚成活行）
        setStrategy(ConflictStrategy.DEVICE_PRIORITY)
        assertTrue(readState(kind, 1).first, "切换不追溯：存量墓碑保持墓碑")

        // 权威端（android）live push 复活墓碑（优先端 live 胜非优先端 tomb，MASTER §4.2 切换语义）
        pushLive(kind, device = android1, key = 1, lm = newer(), version = 1)
        assertFalse(readState(kind, 1).first, "A：权威端 live 复活墓碑")
    }

    // ==================== policy 存取 / D2 / 兼容 ====================

    @Test
    fun `default policy is lww with contract defaults (v4)`() {
        val policy = service.currentPolicy()
        // v4：缺省（无存储值时的回退）从 device_priority 改为 lww；节拍缺省 10s。
        assertEquals(ConflictStrategy.LWW, policy.conflictStrategy)
        assertEquals(1, policy.clientTier)
        assertEquals(10, policy.autoSyncIntervalSec)
    }

    @Test
    fun `policy persists across updatePolicy round trips (restart-safe via server_config KV)`() {
        service.updatePolicy(SyncPolicyDto(ConflictStrategy.WEB_PRIORITY, clientTier = 2, autoSyncIntervalSec = 60))
        val p = service.currentPolicy()
        assertEquals(ConflictStrategy.WEB_PRIORITY, p.conflictStrategy)
        assertEquals(2, p.clientTier)
        assertEquals(60, p.autoSyncIntervalSec)
        service.updatePolicy(SyncPolicyDto(ConflictStrategy.LWW))
        assertEquals(ConflictStrategy.LWW, service.currentPolicy().conflictStrategy)
    }

    @Test
    fun `policy validation rejects out-of-range tier and negative interval`() {
        assertNotNull(service.validatePolicy(SyncPolicyDto(clientTier = 4)))
        assertNotNull(service.validatePolicy(SyncPolicyDto(clientTier = -1)))
        assertNotNull(service.validatePolicy(SyncPolicyDto(autoSyncIntervalSec = -5)))
        assertEquals(null, service.validatePolicy(SyncPolicyDto(clientTier = 0, autoSyncIntervalSec = 0)))
        assertEquals(null, service.validatePolicy(SyncPolicyDto(clientTier = 3)))
        assertThrows(IllegalArgumentException::class.java) {
            service.updatePolicy(SyncPolicyDto(clientTier = 9))
        }
    }

    @Test
    fun `pull carries the current policy`() {
        service.updatePolicy(SyncPolicyDto(ConflictStrategy.WEB_PRIORITY, clientTier = 2, autoSyncIntervalSec = 30))
        val pulled = service.pull(0, "A", android1)
        assertEquals(SyncPolicyDto(ConflictStrategy.WEB_PRIORITY, 2, 30), pulled.policy)
    }

    @Test
    fun `android push carrying policy is an authoritative override (D2)`() {
        push(
            deviceId = android1,
            policy = SyncPolicyDto(ConflictStrategy.LWW, clientTier = 2, autoSyncIntervalSec = 120),
            favorites = listOf(SyncFavoriteDto(gid = 1, token = "t", title = "F", lastModified = 1_000)),
        )
        val p = service.currentPolicy()
        assertEquals(ConflictStrategy.LWW, p.conflictStrategy)
        assertEquals(2, p.clientTier)
        assertEquals(120, p.autoSyncIntervalSec)
        // D2 即时生效：pull 回显新策略
        assertEquals(ConflictStrategy.LWW, service.pull(0, "A", android1).policy!!.conflictStrategy)
    }

    @Test
    fun `policy carried by an android push governs that same push's merges (immediate effect)`() {
        // 同一载荷（android live 对存量 android 墓碑）在两种策略下结论相反——用于证明
        // 「本次 push 携带的 policy 立即作用于本次 merge」：
        //  · 切换前策略 A：优先端（android）墓碑无条件压制 android live → 保持删除；
        //  · 本次 push 携带 policy=lww：立即生效——live 后到，到达戳胜 → 复活。
        setStrategy(ConflictStrategy.DEVICE_PRIORITY)
        pushTomb(Kind.FAVORITE, device = android1, key = 1, lm = newer()) // android 删除落墓碑
        // 对照（不带 policy，当前策略 A）：同一 android live 载荷被优先端墓碑压制，仍删除。
        pushLive(Kind.FAVORITE, device = android1, key = 1, lm = newer(), version = 1)
        assertTrue(readState(Kind.FAVORITE, 1).first, "A：优先端墓碑压制 android live，保持删除")

        // 本次 push 携带 policy=lww → 本次 merge 即按 lww 到达戳仲裁：live 后到 → 复活
        //（客户戳在入口被重打戳，仅传输元数据）。
        push(
            deviceId = android1,
            policy = SyncPolicyDto(ConflictStrategy.LWW),
            favorites = listOf(
                SyncFavoriteDto(gid = 1, token = "t1", title = "revived", lastModified = stale(), deviceId = android1, deleted = false)
            ),
        )
        assertFalse(readState(Kind.FAVORITE, 1).first, "本次 push 携带的 lww 立即生效：live 后到复活墓碑")

        // 对照：恢复缺省 device_priority 后，同样的墓碑 push 不带 policy → 删除传播
        setStrategy(ConflictStrategy.DEVICE_PRIORITY)
        pushLive(Kind.FAVORITE, device = web1, key = 2, lm = newer(), version = 1)
        pushTomb(Kind.FAVORITE, device = android1, key = 2, lm = newer())
        assertTrue(readState(Kind.FAVORITE, 2).first, "缺省 device_priority：优先端删除传播")
    }

    @Test
    fun `web push carrying policy is ignored (policy channel for web is PUT)`() {
        service.updatePolicy(SyncPolicyDto(ConflictStrategy.DEVICE_PRIORITY))
        push(
            deviceId = web1,
            policy = SyncPolicyDto(ConflictStrategy.LWW, clientTier = 3, autoSyncIntervalSec = 5),
        )
        val p = service.currentPolicy()
        assertEquals(ConflictStrategy.DEVICE_PRIORITY, p.conflictStrategy, "web push 的 policy 字段应被忽略")
        assertEquals(1, p.clientTier)
        assertEquals(10, p.autoSyncIntervalSec)
    }

    @Test
    fun `android push with invalid policy values fails before any merge`() {
        assertThrows(IllegalArgumentException::class.java) {
            push(
                deviceId = android1,
                policy = SyncPolicyDto(clientTier = 7),
                favorites = listOf(SyncFavoriteDto(gid = 1, token = "t", title = "F", lastModified = 1_000)),
            )
        }
        assertEquals(0, favoriteRepo.findAll().size, "非法 policy 的 push 不应落库任何实体")
    }

    @Test
    fun `legacy client push without policy keeps working (compat)`() {
        // 旧客户端：push 无 policy 字段 → 不报错，服务器按当前策略（缺省 lww）合并
        val response = push(
            deviceId = android1,
            favorites = listOf(SyncFavoriteDto(gid = 1, token = "t", title = "F", lastModified = 1_000)),
        )
        assertTrue(response.success)
        assertEquals(1, favoriteRepo.findAll().size)
        // pull 附 policy 供新客户端使用；旧客户端忽略该字段（v4 缺省 = lww）
        assertEquals(ConflictStrategy.LWW, service.pull(0, "A", android1).policy!!.conflictStrategy)
    }

    @Test
    fun `pull echoes the last-writer deviceId so clients can merge under the strategy`() {
        setStrategy(ConflictStrategy.DEVICE_PRIORITY)
        pushLive(Kind.FAVORITE, device = android1, key = 1, lm = 1_000, version = 1)
        pushLive(Kind.FAVORITE, device = web1, key = 2, lm = 1_000, version = 1)

        val pulled = service.pull(0, "A", android1).entities.favorites.associateBy { it.gid }
        assertEquals(android1, pulled[1L]!!.deviceId, "android 写入的行回显 android deviceId")
        assertEquals(web1, pulled[2L]!!.deviceId, "web 写入的行回显 web deviceId")
    }

    // ==================== push / read 适配层 ====================

    private fun push(
        deviceId: String,
        policy: SyncPolicyDto? = null,
        favorites: List<SyncFavoriteDto> = emptyList(),
        history: List<SyncHistoryDto> = emptyList(),
        downloads: List<SyncDownloadDto> = emptyList(),
        bookmarks: List<SyncBookmarkDto> = emptyList(),
        filters: List<SyncFilterDto> = emptyList(),
        quickSearches: List<SyncQuickSearchDto> = emptyList(),
        downloadLabels: List<SyncDownloadLabelDto> = emptyList(),
    ) = service.push(
        SyncPushRequest(
            entities = SyncEntityCollection(
                favorites = favorites,
                history = history,
                downloads = downloads,
                bookmarks = bookmarks,
                filters = filters,
                quickSearches = quickSearches,
                downloadLabels = downloadLabels,
            ),
            deviceId = deviceId,
            timestamp = System.currentTimeMillis(),
            policy = policy,
        ),
        "A",
    )

    /** 以 device 身份推送 key 的 live 记录；version 映射为实体的标记字段。 */
    private fun pushLive(kind: Kind, device: String, key: Long, lm: Long, version: Int) {
        when (kind) {
            Kind.FAVORITE -> push(device, favorites = listOf(
                SyncFavoriteDto(gid = key, token = "t$key", title = "v$version", lastModified = lm, deviceId = device),
            ))
            Kind.HISTORY -> push(device, history = listOf(
                SyncHistoryDto(gid = key, token = "t$key", title = "v$version", time = version * 1_000L, lastModified = lm, deviceId = device),
            ))
            Kind.DOWNLOAD -> push(device, downloads = listOf(
                SyncDownloadDto(gid = key, token = "t$key", title = "v$version", state = version, lastModified = lm, deviceId = device),
            ))
            Kind.BOOKMARK -> push(device, bookmarks = listOf(
                SyncBookmarkDto(gid = key, token = "t$key", page = version * 10, lastModified = lm, deviceId = device),
            ))
            Kind.FILTER -> push(device, filters = listOf(
                SyncFilterDto(mode = 0, text = "filter-$key", enabled = version == 2, lastModified = lm, deviceId = device),
            ))
            Kind.QUICK_SEARCH -> push(device, quickSearches = listOf(
                SyncQuickSearchDto(name = "qs-$key", keyword = "v$version", lastModified = lm, deviceId = device),
            ))
            Kind.DOWNLOAD_LABEL -> push(device, downloadLabels = listOf(
                SyncDownloadLabelDto(label = "label-$key", time = version * 111L, lastModified = lm, deviceId = device),
            ))
        }
    }

    private fun pushTomb(kind: Kind, device: String, key: Long, lm: Long) {
        when (kind) {
            Kind.FAVORITE -> push(device, favorites = listOf(
                SyncFavoriteDto(gid = key, token = "t$key", title = "gone", lastModified = lm, deviceId = device, deleted = true),
            ))
            Kind.HISTORY -> push(device, history = listOf(
                SyncHistoryDto(gid = key, token = "t$key", title = "gone", lastModified = lm, deviceId = device, deleted = true),
            ))
            Kind.DOWNLOAD -> push(device, downloads = listOf(
                SyncDownloadDto(gid = key, token = "t$key", title = "gone", lastModified = lm, deviceId = device, deleted = true),
            ))
            Kind.BOOKMARK -> push(device, bookmarks = listOf(
                SyncBookmarkDto(gid = key, token = "t$key", lastModified = lm, deviceId = device, deleted = true),
            ))
            Kind.FILTER -> push(device, filters = listOf(
                SyncFilterDto(mode = 0, text = "filter-$key", lastModified = lm, deviceId = device, deleted = true),
            ))
            Kind.QUICK_SEARCH -> push(device, quickSearches = listOf(
                SyncQuickSearchDto(name = "qs-$key", lastModified = lm, deviceId = device, deleted = true),
            ))
            Kind.DOWNLOAD_LABEL -> push(device, downloadLabels = listOf(
                SyncDownloadLabelDto(label = "label-$key", lastModified = lm, deviceId = device, deleted = true),
            ))
        }
    }

    /** 读回 (deleted, version)；version 从实体标记字段反推（与 pushLive 对应）。 */
    private fun readState(kind: Kind, key: Long): Pair<Boolean, Int> = when (kind) {
        Kind.FAVORITE -> {
            val e = favoriteRepo.findByGid(key)!!
            e.deleted to versionOfTitle(e.title)
        }
        Kind.HISTORY -> {
            val e = historyRepo.findByGid(key)!!
            e.deleted to versionOfTitle(e.title)
        }
        Kind.DOWNLOAD -> {
            val e = downloadRepo.findByGid(key)!!
            e.deleted to e.state
        }
        Kind.BOOKMARK -> {
            val e = bookmarkRepo.findByGid(key)!!
            e.deleted to ((e.note?.toIntOrNull() ?: 0) / 10)
        }
        Kind.FILTER -> {
            val e = filterRepo.findAll().single { it.type == 0 && it.text == "filter-$key" }
            e.deleted to if (e.enabled) 2 else 1
        }
        Kind.QUICK_SEARCH -> {
            val e = quickSearchRepo.findByName("qs-$key")!!
            e.deleted to versionOfTitle(e.keyword)
        }
        Kind.DOWNLOAD_LABEL -> {
            val e = downloadLabelRepo.findByLabel("label-$key")!!
            e.deleted to (e.time / 111).toInt()
        }
    }

    private fun versionOfTitle(title: String?): Int = when {
        title == "v2" -> 2
        title == "v1" -> 1
        else -> 0 // 墓碑占（"gone"）或未知
    }

    private fun pulledGids(entities: SyncEntityCollection, kind: Kind): Set<Long> = when (kind) {
        Kind.HISTORY -> entities.history.filter { it.deleted }.map { it.gid }.toSet()
        Kind.BOOKMARK -> entities.bookmarks.filter { it.deleted }.map { it.gid }.toSet()
        else -> error("row4 仅适用 tombstone 实体")
    }

    // ==================== 参数矩阵 ====================

    companion object {
        @JvmStatic
        fun strategyKindMatrix(): Stream<Arguments> = cartesian(Kind.entries.toList())

        @JvmStatic
        fun strategyTombstoneMatrix(): Stream<Arguments> =
            cartesian(Kind.entries.filter { it.tombstoneClass })

        @JvmStatic
        fun strategySoftMatrix(): Stream<Arguments> =
            cartesian(Kind.entries.filter { !it.tombstoneClass })

        @JvmStatic
        fun strategyACPairs(): Stream<Arguments> = cartesian(
            Kind.entries.toList(),
            listOf(ConflictStrategy.DEVICE_PRIORITY, ConflictStrategy.WEB_PRIORITY),
        )

        @JvmStatic
        fun softKinds(): Stream<Arguments> =
            Kind.entries.filter { !it.tombstoneClass }.map { Arguments.of(it) }.stream()

        private fun cartesian(
            kinds: List<Kind>,
            strategies: List<ConflictStrategy> = ConflictStrategy.entries,
        ): Stream<Arguments> =
            strategies.flatMap { s -> kinds.map { k -> Arguments.of(s, k) } }.stream()
    }

    // ==================== in-memory repository fakes（与 SyncServiceTest 同款） ====================

    private fun fakeServerConfig(): ServerConfigService {
        val repo = mock(ServerConfigRepository::class.java)
        val store = ConcurrentHashMap<String, ServerConfigEntity>()
        `when`(repo.findById(anyString())).thenAnswer { inv ->
            Optional.ofNullable(store[inv.getArgument<String>(0)])
        }
        `when`(repo.save(any(ServerConfigEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<ServerConfigEntity>(0)
            store[e.key] = e
            e
        }
        `when`(repo.existsById(anyString())).thenAnswer { inv ->
            store.containsKey(inv.getArgument<String>(0))
        }
        return ServerConfigService(repo, EncryptionService(), SiteCoreConfigProperties())
    }

    private fun fakeFavoriteRepo(): LocalFavoriteInfoRepository {
        val repo = mock(LocalFavoriteInfoRepository::class.java)
        val store = ConcurrentHashMap<Long, LocalFavoriteInfoEntity>()
        `when`(repo.save(any(LocalFavoriteInfoEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<LocalFavoriteInfoEntity>(0); store[e.gid] = e; e
        }
        `when`(repo.findByGid(anyLong())).thenAnswer { inv -> store[inv.getArgument<Long>(0)] }
        `when`(repo.findAllByGid(anyLong())).thenAnswer { inv ->
            store[inv.getArgument<Long>(0)]?.let { listOf(it) } ?: emptyList<LocalFavoriteInfoEntity>()
        }
        `when`(repo.findAllByUsernameIsNull()).thenAnswer { store.values.filter { it.username == null } }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) }
        }
        `when`(repo.findByUsernameAndLastModifiedGreaterThan(anyString(), anyLong())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.lastModified > inv.getArgument<Long>(1) }
        }
        return repo
    }

    private fun fakeHistoryRepo(): HistoryInfoRepository {
        val repo = mock(HistoryInfoRepository::class.java)
        val store = ConcurrentHashMap<Long, HistoryInfoEntity>()
        `when`(repo.save(any(HistoryInfoEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<HistoryInfoEntity>(0); store[e.gid] = e; e
        }
        `when`(repo.findByGid(anyLong())).thenAnswer { inv -> store[inv.getArgument<Long>(0)] }
        `when`(repo.findAllByGid(anyLong())).thenAnswer { inv ->
            store[inv.getArgument<Long>(0)]?.let { listOf(it) } ?: emptyList<HistoryInfoEntity>()
        }
        `when`(repo.findAllByUsernameIsNull()).thenAnswer { store.values.filter { it.username == null } }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) }
        }
        `when`(repo.findByUsernameAndLastModifiedGreaterThan(anyString(), anyLong())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.lastModified > inv.getArgument<Long>(1) }
        }
        return repo
    }

    private fun fakeDownloadRepo(): DownloadInfoRepository {
        val repo = mock(DownloadInfoRepository::class.java)
        val store = ConcurrentHashMap<Long, DownloadInfoEntity>()
        `when`(repo.save(any(DownloadInfoEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<DownloadInfoEntity>(0); store[e.gid] = e; e
        }
        `when`(repo.findByGid(anyLong())).thenAnswer { inv -> store[inv.getArgument<Long>(0)] }
        `when`(repo.findAllByGid(anyLong())).thenAnswer { inv ->
            store[inv.getArgument<Long>(0)]?.let { listOf(it) } ?: emptyList<DownloadInfoEntity>()
        }
        `when`(repo.findAllByUsernameIsNull()).thenAnswer { store.values.filter { it.username == null } }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) }
        }
        `when`(repo.findByUsernameAndLastModifiedGreaterThan(anyString(), anyLong())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.lastModified > inv.getArgument<Long>(1) }
        }
        return repo
    }

    private fun fakeBookmarkRepo(): BookmarkInfoRepository {
        val repo = mock(BookmarkInfoRepository::class.java)
        val store = ConcurrentHashMap<Long, BookmarkInfoEntity>()
        `when`(repo.save(any(BookmarkInfoEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<BookmarkInfoEntity>(0); store[e.gid] = e; e
        }
        `when`(repo.findByGid(anyLong())).thenAnswer { inv -> store[inv.getArgument<Long>(0)] }
        `when`(repo.findAllByGid(anyLong())).thenAnswer { inv ->
            store[inv.getArgument<Long>(0)]?.let { listOf(it) } ?: emptyList<BookmarkInfoEntity>()
        }
        `when`(repo.findAllByUsernameIsNull()).thenAnswer { store.values.filter { it.username == null } }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) }
        }
        `when`(repo.findByUsernameAndLastModifiedGreaterThan(anyString(), anyLong())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.lastModified > inv.getArgument<Long>(1) }
        }
        return repo
    }

    private fun fakeFilterRepo(): FilterRepository {
        val repo = mock(FilterRepository::class.java)
        val store = ConcurrentHashMap<String, FilterEntity>()
        `when`(repo.save(any(FilterEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<FilterEntity>(0); store["${e.type}:${e.text}"] = e; e
        }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        `when`(repo.findByTypeAndTextIgnoreCaseOrderByLastModifiedDesc(anyInt(), anyString())).thenAnswer { inv ->
            store.values
                .filter { it.type == inv.getArgument<Int>(0) && it.text == inv.getArgument<String>(1) }
                .sortedByDescending { it.lastModified }
        }
        `when`(repo.findAllByUsernameIsNull()).thenAnswer { store.values.filter { it.username == null } }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) }
        }
        `when`(repo.findByUsernameAndLastModifiedGreaterThan(anyString(), anyLong())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.lastModified > inv.getArgument<Long>(1) }
        }
        return repo
    }

    private fun fakeQuickSearchRepo(): QuickSearchRepository {
        val repo = mock(QuickSearchRepository::class.java)
        val store = ConcurrentHashMap<String, QuickSearchEntity>()
        `when`(repo.save(any(QuickSearchEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<QuickSearchEntity>(0); store[e.name] = e; e
        }
        `when`(repo.findByName(anyString())).thenAnswer { inv -> store[inv.getArgument<String>(0)] }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        `when`(repo.findAllByUsernameIsNull()).thenAnswer { store.values.filter { it.username == null } }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) }
        }
        `when`(repo.findByUsernameAndLastModifiedGreaterThan(anyString(), anyLong())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.lastModified > inv.getArgument<Long>(1) }
        }
        return repo
    }

    private fun fakeDownloadLabelRepo(): DownloadLabelRepository {
        val repo = mock(DownloadLabelRepository::class.java)
        val store = ConcurrentHashMap<String, DownloadLabelEntity>()
        val idCounter = java.util.concurrent.atomic.AtomicLong(1)
        `when`(repo.save(any(DownloadLabelEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<DownloadLabelEntity>(0)
            if (e.id == 0L) e.id = idCounter.getAndIncrement()
            store[e.label] = e
            e
        }
        `when`(repo.findByLabel(anyString())).thenAnswer { inv -> store[inv.getArgument<String>(0)] }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        `when`(repo.findAllByUsernameIsNull()).thenAnswer { store.values.filter { it.username == null } }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) }
        }
        `when`(repo.findByUsernameAndLastModifiedGreaterThan(anyString(), anyLong())).thenAnswer { inv ->
            store.values.filter { it.username == inv.getArgument<String>(0) && it.lastModified > inv.getArgument<Long>(1) }
        }
        return repo
    }

    private fun fakeDeviceRepo(): SyncDeviceRepository {
        val repo = mock(SyncDeviceRepository::class.java)
        val store = ConcurrentHashMap<String, SyncDeviceEntity>()
        `when`(repo.save(any(SyncDeviceEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<SyncDeviceEntity>(0); store[e.deviceId] = e; e
        }
        `when`(repo.findByDeviceId(anyString())).thenAnswer { inv -> store[inv.getArgument<String>(0)] }
        `when`(repo.findAll()).thenAnswer { store.values.toList() }
        return repo
    }

    private fun fakePreferenceRepo(): UserPreferenceRepository {
        val repo = mock(UserPreferenceRepository::class.java)
        val store = ConcurrentHashMap<String, UserPreferenceEntity>()
        `when`(repo.save(any(UserPreferenceEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<UserPreferenceEntity>(0); store[e.username] = e; e
        }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv -> store[inv.getArgument<String>(0)] }
        return repo
    }
}
