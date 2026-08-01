package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.web.dto.HistoryItem
import com.hippo.ehviewer.web.dto.HistoryListResponse
import com.hippo.ehviewer.web.entity.HistoryInfoEntity
import com.hippo.ehviewer.web.repository.HistoryInfoRepository
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
            val entities = historyRepository.findAllByOrderByTimeDesc()
            HistoryListResponse(history = entities.map { it.toItem() }, total = entities.size)
        } else {
            val pageable = PageRequest.of(page?.coerceAtLeast(0) ?: 0, (pageSize ?: DEFAULT_PAGE_SIZE).coerceIn(1, MAX_PAGE_SIZE))
            val result = historyRepository.findHistoryPaged(pageable)
            HistoryListResponse(history = result.content.map { it.toItem() }, total = result.totalElements.toInt())
        }
    }

    fun addHistory(gid: Long, token: String, title: String?, titleJpn: String?,
                   thumb: String?, category: Int, rating: Float) {
        val existing = historyRepository.findByGid(gid)
        if (existing != null) {
            existing.time = System.currentTimeMillis()
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
        category = category.toString(),
        rating = rating,
        mode = 0,
        time = time
    )

    companion object {
        private const val DEFAULT_PAGE_SIZE = 50
        private const val MAX_PAGE_SIZE = 200
    }
}
