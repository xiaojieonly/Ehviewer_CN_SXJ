package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.dto.FavoriteItem
import com.hippo.anotherviewer.web.dto.FavoriteListResponse
import com.hippo.anotherviewer.web.entity.LocalFavoriteInfoEntity
import com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository
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
                category = entity.category,
                rating = entity.rating,
                uploader = entity.uploader,
                posted = entity.posted
            )
        }
        return FavoriteListResponse(items, totalPages, startPage)
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
        val existing = favoriteRepository.findByGid(gid)
        if (existing != null) return false
        val entity = LocalFavoriteInfoEntity().apply {
            this.gid = gid
            this.token = token
            this.title = title
            // category is a site bitmask (up to 512); it is written to its own
            // column only and must never leak into favoriteSlot.
            this.category = category
            // Clamp so a bitmask-style value can never be persisted as a slot;
            // an out-of-range slot would break listFavorites' slot filter.
            // Legacy rows with favoriteSlot=512 (pre-N-5) are not migrated here.
            this.favoriteSlot = slot.coerceIn(SLOT_NOT_FAVORITED, SLOT_MAX)
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
