package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.web.dto.HistoryItem
import com.hippo.ehviewer.web.dto.HistoryListResponse
import com.hippo.ehviewer.web.entity.HistoryInfoEntity
import com.hippo.ehviewer.web.repository.HistoryInfoRepository
import org.springframework.stereotype.Service

@Service
class HistoryService(private val historyRepository: HistoryInfoRepository) {

    fun listHistory(): HistoryListResponse {
        val entities = historyRepository.findAllByOrderByTimeDesc()
        val items = entities.map { entity ->
            HistoryItem(
                gid = entity.gid,
                token = entity.token,
                title = entity.title ?: "",
                titleJpn = entity.titleJpn ?: "",
                thumb = entity.thumb ?: "",
                category = entity.category.toString(),
                rating = entity.rating,
                mode = 0,
                time = entity.time
            )
        }
        return HistoryListResponse(items)
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
}
