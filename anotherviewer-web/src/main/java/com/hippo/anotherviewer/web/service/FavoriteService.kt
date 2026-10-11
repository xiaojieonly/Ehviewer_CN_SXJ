package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.dto.FavoriteItem
import com.hippo.anotherviewer.web.dto.FavoriteListResponse
import com.hippo.anotherviewer.web.config.CurrentUsernameProvider
import com.hippo.anotherviewer.web.config.PrivacyMaskFilter
import com.hippo.anotherviewer.web.entity.LocalFavoriteInfoEntity
import com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class FavoriteService(
    private val favoriteRepository: LocalFavoriteInfoRepository,
    private val historyRepository: com.hippo.anotherviewer.web.repository.HistoryInfoRepository,
    private val downloadRepository: com.hippo.anotherviewer.web.repository.DownloadInfoRepository,
    private val historyService: HistoryService,
    private val usernameProvider: CurrentUsernameProvider,
) {
    private val logger = LoggerFactory.getLogger(FavoriteService::class.java)

    /**
     * List favorites with 1-based pagination (contract: `page` starts at 1)
     * and folder-slot filtering aligned with the Android `FavoritesScene`
     * tabs (F-UX5):
     *
     *  - `slot == 0` → the default folder: rows with `favoriteSlot` in
     *    (-1, 0). -1 is "added with the default folder", 0 is explicit
     *    Favorites 0; the app's first tab shows both.
     *  - `slot > 0`  → exactly that custom folder (`favoriteSlot == slot`).
     *  - `slot < 0`  → all slots. The openapi contract only documents slots
     *    0-9 and only a 200 response for this endpoint, so out-of-domain
     *    negative values keep the legacy total mapping instead of gaining an
     *    undocumented 4xx; no first-party client sends them (WebUI tabs are
     *    0-9).
     *
     * P2: slot/q filtering and pagination are pushed down to the DB
     * (repository `findLiveBySlot*` JPQL also excludes tombstone rows) — no
     * unpaged full-table load anymore. The regex flavor keeps its in-memory
     * semantics but only runs over a DB pre-filtered set (slot push-down plus
     * a conservative literal-seed LIKE window; without a derivable seed it
     * falls back to the slot-filtered set). The response envelope stays
     * backward compatible: `favorites`/`totalPages`/`currentPage` unchanged,
     * new `page`/`pageSize`/`total` fields added (frontend switch lands in
     * W3-F4; legacy clients ignore them).
     */
    fun listFavorites(slot: Int, page: Int, pageSize: Int = 20, q: String? = null, regex: Boolean = false): FavoriteListResponse {
        val startPage = page.coerceAtLeast(1)
        // 旧实现 pageSize < 1 时 totalPages 计算除零（0）或产生负窗口（负数），
        // 夹到 1 只收紧崩溃面，不影响任何合法调用（控制器默认 20）。
        val size = pageSize.coerceAtLeast(1)
        val qFilter = q?.takeIf { it.isNotBlank() }
        // 非法正则在进 DB 之前抛出（→ 控制器 400 REGEX_INVALID，行为不变）。
        val matcher = if (qFilter != null && regex) {
            try {
                Regex(qFilter)
            } catch (e: Exception) {
                throw IllegalArgumentException("正则表达式无效: ${e.message}")
            }
        } else null
        val paged: List<LocalFavoriteInfoEntity>
        val total: Int
        when {
            qFilter == null -> {
                // P2: 无 q——slot 过滤 + 分页下沉 DB（墓碑行由 JPQL deleted = false 兜底，
                // 对齐 HistoryService：增量同步需要墓碑行落库（SyncService.mergeFavorite），
                // /favorite/list 只呈现存活收藏，total/分页也只按存活行计（R4-17））。
                val result = favoriteRepository.findLiveBySlotPaged(slot, PageRequest.of(startPage - 1, size))
                paged = result.content
                total = result.totalElements.toInt()
            }
            matcher == null -> {
                // P2: 纯子串 q 下沉 DB（LIKE，大小写不敏感），slot 过滤照旧先行。
                val result = favoriteRepository.findLiveBySlotAndTitlePaged(slot, qFilter, PageRequest.of(startPage - 1, size))
                paged = result.content
                total = result.totalElements.toInt()
            }
            else -> {
                // regex：语义保内存，但只跑在 DB 预过滤集上（slot 已下沉 + 字面
                // 种子 LIKE 窗口；种子推不出回退仅 slot 过滤集），total/分页按
                // regex 匹配后行数计（与旧行为一致）。
                val seed = regexLiteralSeed(qFilter)
                val candidates = if (seed != null) favoriteRepository.findLiveBySlotAndTitle(slot, seed)
                else favoriteRepository.findLiveBySlot(slot)
                val matched = candidates.filter {
                    matcher.containsMatchIn(it.title ?: "") || matcher.containsMatchIn(it.titleJpn ?: "")
                }
                paged = matched.drop((startPage - 1) * size).take(size)
                total = matched.size
            }
        }
        val totalPages = (total + size - 1) / size
        // 阅读进度批量查（findByGidIn 防 N+1）：同 gid 历史行的 page 即当前进度。
        val progressByGid = historyRepository.findByGidIn(paged.map { it.gid })
            .associate { it.gid to it.page }
        val items = paged.map { entity ->
            FavoriteItem(
                gid = entity.gid,
                token = entity.token,
                title = entity.title ?: "",
                titleJpn = entity.titleJpn ?: "",
                thumb = entity.thumb ?: "",
                category = entity.category,
                rating = entity.rating,
                uploader = entity.uploader,
                posted = entity.posted,
                // F-UX5: 条目真实 slot 随行下发——收藏页的 ♥ 徽章据此渲染真值，
                // 不再退化为当前页签号。
                favoriteSlot = entity.favoriteSlot,
                readProgress = progressByGid[entity.gid]
            )
        }
        // 信封向后兼容：favorites/totalPages/currentPage 原样保留，page/pageSize/total
        // 为新增字段（W3-F4 前端切换消费，旧客户端忽略不受影响）。
        return FavoriteListResponse(items, totalPages, startPage, page = startPage, pageSize = size, total = total)
    }

    /**
     * Android favoriteSlot contract (see `GalleryListParser.parseFavoriteSlot`):
     * -2 = not favorited, -1 = default folder, 0-9 = custom slots.
     * Values outside this range are never written by this service.
     */
    private companion object {
        const val SLOT_NOT_FAVORITED = -2
        const val SLOT_DEFAULT_FOLDER = -1
        const val SLOT_MAX = 9
    }

    fun addFavorite(
        gid: Long,
        token: String,
        title: String?,
        category: Int,
        slot: Int = SLOT_DEFAULT_FOLDER
    ): Boolean {
        val clampedSlot = slot.coerceIn(SLOT_NOT_FAVORITED, SLOT_MAX)
        val now = System.currentTimeMillis()
        // 打码边界（2026-10-11 用户裁决）：打码开关只应影响响应展示，绝不让脱敏
        // 数据落库——打码期间前端只持有 `#gid` 序列号标题，与历史回写同口径拒收
        // （置空，列表按 #gid 兜底渲染）；真标题待 App 推送/后续操作自然补全。
        val safeTitle = title?.takeIf { !PrivacyMaskFilter.isMaskedTitle(it) }
        // A7-3（P1-1）：List 化防同 gid 多行炸单实体派生查询；firstOrNull 与原
        // findByGid 等价（单行模型下语义不变，多行脏数据不再毒化请求）。
        val existing = favoriteRepository.findAllByGid(gid).firstOrNull()
        // A7-2 复活语义（F3，对齐 SyncService.mergeFavorite 的 incoming live 复活分支）：
        // 墓碑行不拒绝而是复活（deleted→false + 覆写业务字段 + stamp），否则 Web 永远
        // 无法重新收藏该 gid（列表已隐藏墓碑，重加是无声失败）；活行仍拒绝。
        val entity = when {
            existing == null -> LocalFavoriteInfoEntity().apply {
                this.gid = gid
                this.token = token
                this.title = safeTitle
                // category is a site bitmask (up to 512); it is written to its own
                // column only and must never leak into favoriteSlot.
                this.category = category
                this.favoriteSlot = clampedSlot
                this.time = now
                // A7-1 stamping：新行当场落属主与同步水位（adoptNullOwnership 已短路）。
                this.username = usernameProvider.currentUsername()
                this.lastModified = now
            }
            existing.deleted -> existing.apply {
                this.token = token
                this.title = safeTitle
                this.category = category
                this.favoriteSlot = clampedSlot
                this.time = now
                this.deleted = false
                if (this.username == null) this.username = usernameProvider.currentUsername()
                this.lastModified = now
            }
            else -> return false
        }
        favoriteRepository.save(entity)
        // 任务 D：回写来源历史行的 favoriteSlot（与收藏行一致，取夹紧后的值）。
        // 详情读取链（GalleryService 历史分支）优先历史行，不回写则重进详情
        // favoriteSlot 恒 -2——收藏按钮「只加不减」的根因。无历史行不新建
        // （收藏不凭空造历史），降级为日志。
        if (!historyService.updateFavoriteSlot(gid, entity.favoriteSlot)) {
            logger.info("addFavorite gid={} slot={}: no history row, favoriteSlot writeback skipped", gid, entity.favoriteSlot)
        }
        // 已下载画廊的详情读取链 download 分支优先于 history 分支，下载列表行
        // 同样以 download 行为 favoriteSlot 来源——来源行是 download 行时也要
        // 回写，否则重进详情/下载列表仍显示未收藏。D11：墓碑下载行跳过（回写
        // 不得复活删除）。A7-3：查找 List 化（见 addFavorite 顶部注释）。
        downloadRepository.findAllByGid(gid).firstOrNull()?.takeIf { !it.deleted }?.let {
            it.favoriteSlot = entity.favoriteSlot
            it.lastModified = System.currentTimeMillis()
            downloadRepository.save(it)
        }
        return true
    }

    /**
     * A7-2：取消收藏 = 墓碑化（deleted=true + lastModified bump），行保留。
     * 物理删会让删除永不传播（增量 pull 里只是「消失」，App 差分方向相反收不到
     * 信号），且 App 下次 push 同 gid 时 union-merge 会静默重建——Web 删除被撤销。
     * 已是墓碑的行幂等返回 true，不再重复 bump 水位制造无谓 pull 流量。
     */
    fun removeFavorite(gid: Long): Boolean {
        // A7-3（P1-1）：List 化防同 gid 多行炸单实体派生查询（firstOrNull 等价）。
        val existing = favoriteRepository.findAllByGid(gid).firstOrNull() ?: return false
        if (existing.deleted) return true
        existing.deleted = true
        existing.lastModified = System.currentTimeMillis()
        if (existing.username == null) existing.username = usernameProvider.currentUsername()
        favoriteRepository.save(existing)
        // 任务 D：对称清除来源历史行的 favoriteSlot（置回未收藏），重进详情
        // 不残留收藏态；无历史行则本就无状态可残留，降级为日志。
        if (!historyService.updateFavoriteSlot(gid, SLOT_NOT_FAVORITED)) {
            logger.debug("removeFavorite gid={}: no history row, favoriteSlot reset skipped", gid)
        }
        // D11：墓碑下载行跳过（回写不得复活删除）。A7-3：查找 List 化。
        downloadRepository.findAllByGid(gid).firstOrNull()?.takeIf { !it.deleted }?.let {
            it.favoriteSlot = SLOT_NOT_FAVORITED
            it.lastModified = System.currentTimeMillis()
            downloadRepository.save(it)
        }
        return true
    }
}
