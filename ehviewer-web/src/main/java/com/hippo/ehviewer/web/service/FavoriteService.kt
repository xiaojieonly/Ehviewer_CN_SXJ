package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.web.dto.FavoriteItem
import com.hippo.ehviewer.web.dto.FavoriteListResponse
import com.hippo.ehviewer.web.entity.LocalFavoriteInfoEntity
import com.hippo.ehviewer.web.repository.LocalFavoriteInfoRepository
import org.springframework.stereotype.Service

@Service
class FavoriteService(private val favoriteRepository: LocalFavoriteInfoRepository) {

    fun listFavorites(slot: Int, page: Int, pageSize: Int = 20): FavoriteListResponse {
        val all = favoriteRepository.findAllByOrderByTimeDesc()
        val total = all.size
        val totalPages = (total + pageSize - 1) / pageSize
        val paged = all.drop(page * pageSize).take(pageSize)
        val items = paged.map { entity ->
            FavoriteItem(
                gid = entity.gid,
                token = entity.token,
                title = entity.title ?: "",
                titleJpn = entity.titleJpn ?: "",
                thumb = entity.thumb ?: "",
                category = entity.category.toString(),
                rating = entity.rating,
                uploader = entity.uploader,
                posted = entity.posted
            )
        }
        return FavoriteListResponse(items, totalPages, page)
    }

    fun addFavorite(gid: Long, token: String, title: String?, category: Int): Boolean {
        val existing = favoriteRepository.findByGid(gid)
        if (existing != null) return false
        val entity = LocalFavoriteInfoEntity().apply {
            this.gid = gid
            this.token = token
            this.title = title
            this.category = category
            this.time = System.currentTimeMillis()
        }
        favoriteRepository.save(entity)
        return true
    }

    fun removeFavorite(gid: Long): Boolean {
        val existing = favoriteRepository.findByGid(gid) ?: return false
        favoriteRepository.delete(existing)
        return true
    }
}
