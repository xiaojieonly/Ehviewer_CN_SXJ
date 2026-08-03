package com.hippo.anotherviewer.web.service

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
     */
    fun listHistory(page: Int? = null, pageSize: Int? = null): HistoryListResponse {
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

    fun addHistory(gid: Long, token: String, title: String?, titleJpn: String?,
                   thumb: String?, category: Int, rating: Float, mode: Int = 0) {
        val existing = historyRepository.findByGid(gid)
        if (existing != null) {
            existing.time = System.currentTimeMillis()
            existing.mode = mode
            historyRepository.save(existing)
        } else {
            val entity = HistoryInfoEntity().apply {
                this.gid = gid
                this.token = token
                this.title = title
                this.titleJpn = titleJpn
                this.thumb = thumb
                this.category = category
                this.rating = rating
                this.mode = mode
                this.time = System.currentTimeMillis()
            }
            historyRepository.save(entity)
        }
    }

    fun clearHistory() {
        historyRepository.deleteAll()
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
        time = time
    )

    companion object {
        private const val DEFAULT_PAGE_SIZE = 50
        private const val MAX_PAGE_SIZE = 200
    }
}
