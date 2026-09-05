package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.PrivacyMaskFilter
import com.hippo.anotherviewer.web.dto.HistoryItem
import com.hippo.anotherviewer.web.dto.HistoryListResponse
import com.hippo.anotherviewer.web.entity.HistoryInfoEntity
import com.hippo.anotherviewer.web.repository.HistoryInfoRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class HistoryService(private val historyRepository: HistoryInfoRepository) {

    /**
     * When both [page] and [pageSize] are absent, returns the full history
     * (newest first) to keep the legacy callers working unchanged. Otherwise
     * DB-paginates newest first with pageSize clamped to [MAX_PAGE_SIZE].
     *
     * With [q] (or [regex]) set, the query always runs over the full in-memory
     * row set (`findAllByOrderByTimeDesc` minus tombstones), matches
     * title/titleJpn — `q` as a case-insensitive substring, or as a regex
     * when [regex] is true — then paginates with the same 0-based drop/take
     * semantics as the DB path (page defaults 0, pageSize defaults 50,
     * clamped 1..[MAX_PAGE_SIZE]); [HistoryListResponse.total] is the match
     * count. An invalid regex throws [IllegalArgumentException] for the
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
        val matched = historyRepository.findAllByOrderByTimeDesc()
            .filter { !it.deleted }
            .let { rows ->
                if (qFilter == null) rows
                else {
                    val matcher = if (regex) {
                        try {
                            Regex(qFilter)
                        } catch (e: Exception) {
                            throw IllegalArgumentException("正则表达式无效: ${e.message}")
                        }
                    } else null
                    rows.filter { entity ->
                        val t = entity.title ?: ""
                        val tj = entity.titleJpn ?: ""
                        if (matcher != null) matcher.containsMatchIn(t) || matcher.containsMatchIn(tj)
                        else t.contains(qFilter, ignoreCase = true) || tj.contains(qFilter, ignoreCase = true)
                    }
                }
            }
        val start = page?.coerceAtLeast(0) ?: 0
        val size = (pageSize ?: DEFAULT_PAGE_SIZE).coerceIn(1, MAX_PAGE_SIZE)
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
        val existing = historyRepository.findByGid(gid)
        if (existing != null) {
            existing.time = System.currentTimeMillis()
            existing.mode = mode
            if (page != null) existing.page = page.coerceAtLeast(0)
            historyRepository.save(existing)
        } else {
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
            }
            historyRepository.save(entity)
        }
    }

    fun clearHistory() {
        historyRepository.deleteAll()
    }

    /**
     * 收藏联动（任务 D，2026-09-05）：回写/清除历史行的 favoriteSlot
     * （Android 契约：-2=未收藏，-1=默认夹，0-9=自定义夹）。
     * 行不存在时返回 false——收藏绝不凭空造历史行，由调用方
     * （FavoriteService）降级为日志。
     */
    fun updateFavoriteSlot(gid: Long, slot: Int): Boolean {
        val existing = historyRepository.findByGid(gid) ?: return false
        existing.favoriteSlot = slot
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
