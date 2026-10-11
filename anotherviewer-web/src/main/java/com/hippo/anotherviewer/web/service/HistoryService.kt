package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.CurrentUsernameProvider
import com.hippo.anotherviewer.web.config.PrivacyMaskFilter
import com.hippo.anotherviewer.web.dto.HistoryItem
import com.hippo.anotherviewer.web.dto.HistoryListResponse
import com.hippo.anotherviewer.web.entity.HistoryInfoEntity
import com.hippo.anotherviewer.web.repository.HistoryInfoRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class HistoryService(
    private val historyRepository: HistoryInfoRepository,
    private val usernameProvider: CurrentUsernameProvider,
    // v4（WP2）：WebUI 直写路径与 sync 合并共用服务器打戳时钟（SyncClock.next()），
    // 保证落库水位全局单调、与 pull 的 since 语义一致。可空缺省仅为既有直构
    // 测试的源兼容保留——null 时回退墙钟。
    private val syncClock: SyncClock? = null,
) {

    private val logger = org.slf4j.LoggerFactory.getLogger(HistoryService::class.java)

    /** v4 打戳助手：有 SyncClock 走服务器单调时钟，直构测试场景回落墙钟。 */
    private fun stamp(): Long = syncClock?.next() ?: System.currentTimeMillis()

    /**
     * When both [page] and [pageSize] are absent, returns the full history
     * (newest first) to keep the legacy callers working unchanged. Otherwise
     * DB-paginates newest first with pageSize clamped to [MAX_PAGE_SIZE].
     *
     * With [q] set (and [regex] false), the substring filter is pushed down to
     * the DB (LIKE %q% over title/titleJpn, live rows only) and DB-paginated
     * with the same 0-based semantics as before (page defaults 0, pageSize
     * defaults 50, clamped 1..[MAX_PAGE_SIZE]); [HistoryListResponse.total] is
     * the DB match count.
     *
     * With [regex] true and a non-blank [q], a conservative literal seed is
     * derived from the pattern for a DB LIKE pre-filter and the regex only runs
     * over that pre-filtered superset (in-memory pagination preserved; total =
     * regex match count). When no seed can be derived (e.g. a top-level `|`)
     * the legacy full-set in-memory path is kept — semantics first. An invalid
     * regex throws [IllegalArgumentException] before any DB work, for the
     * controller to turn into a 400 REGEX_INVALID.
     */
    fun listHistory(page: Int? = null, pageSize: Int? = null, q: String? = null, regex: Boolean = false): HistoryListResponse {
        val qFilter = q?.takeIf { it.isNotBlank() }
        if (qFilter == null && !regex) {
            return if (page == null && pageSize == null) {
                // 墓碑行（deleted=true 的同步删除记录）不列进 REST 列表；分页路径由
                // findHistoryPaged 的 JPQL（where h.deleted = false）兜底。
                val entities = historyRepository.findAllByOrderByTimeDesc().filter { !it.deleted }
                HistoryListResponse(history = entities.map { it.toItem() }, total = entities.size)
            } else {
                val pageable = PageRequest.of(page?.coerceAtLeast(0) ?: 0, (pageSize ?: DEFAULT_PAGE_SIZE).coerceIn(1, MAX_PAGE_SIZE))
                val result = historyRepository.findHistoryPaged(pageable)
                HistoryListResponse(history = result.content.map { it.toItem() }, total = result.totalElements.toInt())
            }
        }
        val start = page?.coerceAtLeast(0) ?: 0
        val size = (pageSize ?: DEFAULT_PAGE_SIZE).coerceIn(1, MAX_PAGE_SIZE)
        if (qFilter == null) {
            // regex=true 但 q 为空：无有效过滤条件，保持旧行为——全量内存路径
            // （等价无过滤列表 + 内存分页），regex 无从应用。
            val entities = historyRepository.findAllByOrderByTimeDesc().filter { !it.deleted }
            return HistoryListResponse(
                history = entities.drop(start * size).take(size).map { it.toItem() },
                total = entities.size
            )
        }
        // 非法正则在进 DB 之前抛出（→ 400 REGEX_INVALID，与旧路径一致）。
        val matcher = if (regex) {
            try {
                Regex(qFilter)
            } catch (e: Exception) {
                throw IllegalArgumentException("正则表达式无效: ${e.message}")
            }
        } else null
        if (matcher == null) {
            // P2: 纯子串 q 下沉 DB（LIKE + DB 分页），万行级不再不分页全表载入；
            // total = DB 匹配总数，信封与内存时代完全一致。
            val result = historyRepository.findLiveByTitleOrTitleJpnContainingPaged(qFilter, PageRequest.of(start, size))
            return HistoryListResponse(history = result.content.map { it.toItem() }, total = result.totalElements.toInt())
        }
        // P2: regex=true——先推保守字面种子做 DB LIKE 预过滤（预过滤集 ⊇ 正则
        // 匹配集），内存 regex 只跑在该集合上，精确语义不变；种子推不出
        // （顶层交替等）回退存量全量路径，语义优先于性能。
        val seed = regexLiteralSeed(qFilter)
        val candidates = if (seed != null) {
            historyRepository.findLiveByTitleOrTitleJpnContaining(seed)
        } else {
            historyRepository.findAllByOrderByTimeDesc().filter { !it.deleted }
        }
        val matched = candidates.filter {
            matcher.containsMatchIn(it.title ?: "") || matcher.containsMatchIn(it.titleJpn ?: "")
        }
        return HistoryListResponse(
            history = matched.drop(start * size).take(size).map { it.toItem() },
            total = matched.size
        )
    }

    fun addHistory(gid: Long, token: String, title: String?, titleJpn: String?,
                   thumb: String?, category: Int, rating: Float, mode: Int = 0) {
        addHistory(gid, token, title, titleJpn, thumb, category, rating, mode, null)
    }

    /**
     * Upsert 一条历史。[page] 非 null 时写入阅读进度（0 起页索引）。
     * 进度只在调用方明确携带时改写——内部路径（站点详情拉取落历史等）
     * 传 null 保持已存值，避免被 0 清掉。
     */
    fun addHistory(gid: Long, token: String, title: String?, titleJpn: String?,
                   thumb: String?, category: Int, rating: Float, mode: Int = 0, page: Int? = null) {
        // A7-3（P1-1）：gid 查找 List 化——同 gid 多行（历史脏数据/属主并存）时单实体
        // findByGid 派生查询会抛 IncorrectResultSizeDataAccessException。属主行取
        // 自己的行或 NULL 行；唯一/全部行都属他人 → 跳过更新也不另起一行（保持
        // gid 全局单行模型，不跨用户改行；require_auth=false 下全员 "default" 无感知）。
        val user = usernameProvider.currentUsername()
        val rows = historyRepository.findAllByGid(gid)
        val existing = rows.firstOrNull { it.username == null || it.username == user }
        if (existing != null) {
            // time 是业务时间字段（浏览时刻），保留墙钟；lastModified 是同步水位，
            // v4 起走服务器打戳（SyncClock）。
            existing.time = System.currentTimeMillis()
            existing.mode = mode
            if (page != null) existing.page = page.coerceAtLeast(0)
            // A7-2：clearHistory 墓碑化后重读同一画廊必须复活历史行（对齐
            // SyncService.mergeHistory 的墓碑复活语义）——否则墓碑行被反复更新、
            // 行永远不出现在列表里，Web 端「清空后重读」功能性地丢失。
            existing.deleted = false
            // A7-1 stamping：更新不覆盖属主（行上 NULL 才落当前用户）；lastModified
            // 必须 bump——它是 App 增量 pull 看到 Web 侧阅读进度/模式变更的唯一信号。
            if (existing.username == null) existing.username = user
            existing.lastModified = stamp()
            historyRepository.save(existing)
        } else {
            if (rows.isNotEmpty()) {
                logger.info(
                    "addHistory gid={}: existing row owned by another user, update skipped",
                    gid,
                )
                return
            }
            val entity = HistoryInfoEntity().apply {
                this.gid = gid
                this.token = token
                // 打码期间首次浏览的画廊，前端只能送来脱敏标题（#gid）——
                // 拒绝入库（置空，列表侧本就按 #gid 兜底渲染），防污染存量。
                this.title = title?.takeIf { !PrivacyMaskFilter.isMaskedTitle(it) }
                this.titleJpn = titleJpn
                this.thumb = thumb
                this.category = category
                this.rating = rating
                this.mode = mode
                this.page = page?.coerceAtLeast(0) ?: 0
                this.time = System.currentTimeMillis()
                // A7-1 stamping：新行当场落属主与同步水位（adoptNullOwnership 已短路，
                // NULL 行不再有后续认养扫描）。v4：水位走 syncClock（time 保留墙钟）。
                this.username = user
                this.lastModified = stamp()
            }
            historyRepository.save(entity)
        }
    }

    /**
     * A7-2（§1.4）：清空历史 = 逐行墓碑化，而非 `deleteAll()` 物理删。
     *
     * 物理删后没有任何行满足 `lastModified > since`，App 增量 pull 收不到任何信号；
     * App 的快照差分只发现「本地删了、服务端还有」（push 方向），不存在反向差分；
     * 即使全量 pull 也是纯 upsert，App 重装后全量 push 反而会被 mergeHistory 逐条
     * 复活。墓碑行（deleted=true + lastModified bump）是服务端删除传播的唯一载体
     * （与 mergeHistory 落的墓碑完全同构）。
     *
     * 作用域：只墓碑化当前用户的活行；username 为 NULL 的存量行视作当次就地收养
     * 一并墓碑化（require_auth=false 下全员即 "default"，等同清库）。
     * 墓碑无 GC（与 mergeHistory 的墓碑一致，行永久留存——自动清理会令离线超过
     * 清理窗口的设备永久错过删除，如需引入必须单独立项）。
     */
    @Transactional
    fun clearHistory() {
        // v4：整批墓碑共用一次 syncClock.next()——同批严格同戳、跨批单调。
        val now = stamp()
        val user = usernameProvider.currentUsername()
        val mine = historyRepository.findByUsername(user) + historyRepository.findAllByUsernameIsNull()
        mine.filter { !it.deleted }.forEach { row ->
            row.deleted = true
            row.lastModified = now
            if (row.username == null) row.username = user
            historyRepository.save(row)
        }
    }

    /**
     * 收藏联动（任务 D，2026-09-05）：回写/清除历史行的 favoriteSlot
     * （Android 契约：-2=未收藏，-1=默认夹，0-9=自定义夹）。
     * 行不存在时返回 false——收藏绝不凭空造历史行，由调用方
     * （FavoriteService）降级为日志。
     */
    fun updateFavoriteSlot(gid: Long, slot: Int): Boolean {
        // A7-3（P1-1）：List 化 + 属主行选择（见 addHistory）；唯一/全部行都属他人
        // → 跳过写回并留痕，返回 false（调用方降级为日志，绝不跨用户改行）。
        val rows = historyRepository.findAllByGid(gid)
        val existing = rows.firstOrNull {
            it.username == null || it.username == usernameProvider.currentUsername()
        }
        if (existing == null) {
            if (rows.isNotEmpty()) {
                logger.info(
                    "updateFavoriteSlot gid={}: existing row owned by another user, writeback skipped",
                    gid,
                )
            }
            return false
        }
        // A7-1：favoriteSlot 是同步可见字段——只在值实际变化时写回并 bump lastModified，
        // 无变化不产生同步流量（增量 pull 以 lastModified > since 为信号）。
        if (existing.favoriteSlot == slot) return true
        existing.favoriteSlot = slot
        if (existing.username == null) existing.username = usernameProvider.currentUsername()
        existing.lastModified = stamp()
        historyRepository.save(existing)
        return true
    }

    private fun HistoryInfoEntity.toItem() = HistoryItem(
        gid = gid,
        token = token,
        title = title ?: "",
        titleJpn = titleJpn ?: "",
        thumb = thumb ?: "",
        category = category,
        rating = rating,
        mode = mode,
        page = page,
        time = time
    )

    companion object {
        private const val DEFAULT_PAGE_SIZE = 50
        private const val MAX_PAGE_SIZE = 200
    }
}

/**
 * P2: 从正则推导「任何匹配串都必然包含」的最长连续字面子串（保守超集种子），
 * 供 DB LIKE 预过滤——预过滤集 ⊇ 正则匹配集，随后的内存 regex 保持精确语义。
 * 推导不出非空种子（顶层 `|` 交替、全元字符等）返回 null，调用方回退全量路径。
 * 供 [HistoryService] 与 FavoriteService 的 regex 检索路径共用。
 *
 * 保守规则（宁可种子偏短/为 null，绝不偏长——偏长会漏配）：
 *  - 元字符 `.` `^` `$` `)` 终结当前字面段；
 *  - 顶层 `|` 两侧分支无公共保证字面 → 直接 null（组内 `|` 已随组跳过）；
 *  - 量词 `? * + {m,n}` 丢弃段尾字面并断段（前一原子可能缺席/重复，{m≥1} 保守同待）；
 *  - `\d` `\w` 等字母数字转义按字符类/零宽断言断段；标点转义取其字面字符；
 *  - 分组 `(…)` 与字符类 `[…]` 整体跳过，不取其内容（组内可为多分支/多选一）。
 */
internal fun regexLiteralSeed(pattern: String): String? {
    var best = ""
    val run = StringBuilder()
    var i = 0
    val n = pattern.length

    fun endRun() {
        if (run.length > best.length) best = run.toString()
        run.setLength(0)
    }

    fun dropLast() {
        if (run.isNotEmpty()) run.deleteCharAt(run.length - 1)
    }

    while (i < n) {
        when (val c = pattern[i]) {
            '\\' -> {
                // 尾部孤立反斜杠：Regex() 会抛，调用方已先行校验，这里安全止步。
                if (i + 1 >= n) { i = n; continue }
                val next = pattern[i + 1]
                if (next.isLetterOrDigit()) endRun() else run.append(next)
                i += 2
            }
            '.', '^', '$', ')' -> { endRun(); i++ }
            '|' -> return null
            '(', '[' -> { endRun(); i = skipSeedAtom(pattern, i) }
            '*', '+', '?' -> { dropLast(); endRun(); i++ }
            '{' -> { dropLast(); endRun(); i = skipSeedCurly(pattern, i) }
            else -> { run.append(c); i++ }
        }
    }
    endRun()
    return best.takeIf { it.isNotEmpty() }
}

/** 跳过分组 `(…)` / 字符类 `[…]`（含转义与嵌套分组），返回其结束符之后的下标。 */
private fun skipSeedAtom(pattern: String, start: Int): Int {
    if (pattern[start] == '[') {
        // 字符类内容是单字符多选一，不是序列——整体跳过；首位 `^` 与 `]`（字面）特殊。
        var i = start + 1
        if (i < pattern.length && pattern[i] == '^') i++
        if (i < pattern.length && pattern[i] == ']') i++
        while (i < pattern.length) {
            when (pattern[i]) {
                '\\' -> i += 2
                ']' -> return i + 1
                else -> i++
            }
        }
        return i
    }
    var depth = 1
    var i = start + 1
    while (i < pattern.length) {
        when (pattern[i]) {
            '\\' -> i += 2
            '(' -> { depth++; i++ }
            ')' -> { depth--; i++; if (depth == 0) return i }
            '[' -> i = skipSeedAtom(pattern, i)
            else -> i++
        }
    }
    return i
}

/** 跳过量词 `{m,n}`（至未转义的 `}`），返回其后下标。 */
private fun skipSeedCurly(pattern: String, start: Int): Int {
    var i = start + 1
    while (i < pattern.length && pattern[i] != '}') i++
    return if (i < pattern.length) i + 1 else i
}
