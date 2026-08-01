package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.web.dto.FavoriteItem
import com.hippo.ehviewer.web.dto.FavoriteListResponse
import com.hippo.ehviewer.web.entity.LocalFavoriteInfoEntity
import com.hippo.ehviewer.web.repository.LocalFavoriteInfoRepository
import org.springframework.stereotype.Service

@Service
class FavoriteService(private val favoriteRepository: LocalFavoriteInfoRepository) {

    /**
     * List favorites with 1-based pagination (contract: `page` starts at 1)
     * and optional folder-slot filtering (`slot <= 0` returns all slots).
     */
    fun listFavorites(slot: Int, page: Int, pageSize: Int = 20): FavoriteListResponse {
        val startPage = page.coerceAtLeast(1)
        val all = favoriteRepository.findAllByOrderByTimeDesc()
        val filtered = if (slot <= 0) all else all.filter { it.favoriteSlot == slot }
        val total = filtered.size
        val totalPages = (total + pageSize - 1) / pageSize
        val paged = filtered.drop((startPage - 1) * pageSize).take(pageSize)
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
        return FavoriteListResponse(items, totalPages, startPage)
    }

    fun addFavorite(gid: Long, token: String, title: String?, category: Int): Boolean {
        val existing = favoriteRepository.findByGid(gid)
        if (existing != null) return false
        val entity = LocalFavoriteInfoEntity().apply {
            this.gid = gid
            this.token = token
            this.title = title
            this.category = category
            // Android dstCat semantics: -1 = default folder, 0-9 = custom slots.
            // Stored so the list endpoint can filter by slot.
            this.favoriteSlot = category
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
