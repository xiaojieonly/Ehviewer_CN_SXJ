package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.entity.GalleryInfoBase
import com.hippo.ehviewer.web.entity.HistoryInfoEntity
import com.hippo.ehviewer.web.entity.QuickSearchEntity
import com.hippo.ehviewer.web.repository.*
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class GalleryService(
    private val historyRepository: HistoryInfoRepository,
    private val quickSearchRepository: QuickSearchRepository,
    private val galleryTagsRepository: GalleryTagsRepository,
    private val localFavoriteInfoRepository: LocalFavoriteInfoRepository
) {
    fun searchGallery(
        keyword: String?,
        category: Int?,
        page: Int,
        pageSize: Int
    ): GalleryListResponse {
        val historyList = historyRepository.findAll()
        val filtered = historyList.filter { info ->
            (keyword.isNullOrBlank() || info.title?.contains(keyword, ignoreCase = true) == true
                    || info.titleJpn?.contains(keyword, ignoreCase = true) == true)
                    && (category == null || category == 0 || info.category == category)
        }
        val total = filtered.size
        val paged = filtered.drop(page * pageSize).take(pageSize)
        return GalleryListResponse(
            success = true,
            data = paged.map { it.toDto() },
            total = total
        )
    }

    fun getGalleryDetail(gid: Long): GalleryDetailDto? {
        val history = historyRepository.findByGid(gid) ?: return null
        val tags = galleryTagsRepository.findByGid(gid)
        return GalleryDetailDto(
            gid = history.gid,
            token = history.token,
            galleryUrl = EhUrl.getGalleryDetailUrl(history.gid, history.token),
            title = history.title,
            titleJpn = history.titleJpn,
            thumb = history.thumb,
            category = history.category,
            posted = history.posted,
            uploader = history.uploader,
            rating = history.rating,
            rated = history.rated,
            simpleLanguage = history.simpleLanguage,
            simpleTags = history.simpleTags?.split(",")?.map { it.trim() } ?: emptyList(),
            thumbWidth = history.thumbWidth,
            thumbHeight = history.thumbHeight,
            pages = history.pages,
            favoriteSlot = history.favoriteSlot,
            favoriteName = history.favoriteName,
            tags = tags.map { TagDto(it.tagNamespace, it.tag) },
            imageUrl = history.thumb
        )
    }

    fun addToHistory(gid: Long, token: String, title: String?) {
        val existing = historyRepository.findByGid(gid)
        if (existing != null) {
            existing.time = System.currentTimeMillis()
            historyRepository.save(existing)
        } else {
            val entity = HistoryInfoEntity().apply {
                this.gid = gid
                this.token = token
                this.title = title
                this.time = System.currentTimeMillis()
            }
            historyRepository.save(entity)
        }
    }

    fun getHistory(page: Int, pageSize: Int): GalleryListResponse {
        val all = historyRepository.findAllByOrderByTimeDesc()
        val total = all.size
        val paged = all.drop(page * pageSize).take(pageSize)
        return GalleryListResponse(
            success = true,
            data = paged.map { it.toDto() },
            total = total
        )
    }

    fun getLocalFavorites(): GalleryListResponse {
        val all = localFavoriteInfoRepository.findAllByOrderByTimeDesc()
        return GalleryListResponse(
            success = true,
            data = all.map { it.toDto() },
            total = all.size
        )
    }

    fun getQuickSearches(): QuickSearchListResponse {
        val all = quickSearchRepository.findAllByOrderById()
        return QuickSearchListResponse(
            success = true,
            data = all.map {
                QuickSearchDto(
                    id = it.id,
                    name = it.name,
                    mode = it.mode,
                    category = it.category,
                    keyword = it.keyword,
                    advanceSearch = it.advanceSearch,
                    minRating = it.minRating,
                    pageFrom = it.pageFrom,
                    pageTo = it.pageTo
                )
            }
        )
    }

    fun createQuickSearch(dto: QuickSearchDto): QuickSearchDto {
        val entity = QuickSearchEntity().apply {
            name = dto.name
            mode = dto.mode
            category = dto.category
            keyword = dto.keyword
            advanceSearch = dto.advanceSearch
            minRating = dto.minRating
            pageFrom = dto.pageFrom
            pageTo = dto.pageTo
        }
        val saved = quickSearchRepository.save(entity)
        return QuickSearchDto(
            id = saved.id,
            name = saved.name,
            mode = saved.mode,
            category = saved.category,
            keyword = saved.keyword,
            advanceSearch = saved.advanceSearch,
            minRating = saved.minRating,
            pageFrom = saved.pageFrom,
            pageTo = saved.pageTo
        )
    }

    fun deleteQuickSearch(id: Long) {
        quickSearchRepository.deleteById(id)
    }

    private fun GalleryInfoBase.toDto() = GalleryItemDto(
        gid = gid,
        token = token,
        galleryUrl = EhUrl.getGalleryDetailUrl(gid, token),
        title = title,
        titleJpn = titleJpn,
        thumb = thumb,
        category = category,
        posted = posted,
        uploader = uploader,
        rating = rating,
        rated = rated,
        simpleLanguage = simpleLanguage,
        simpleTags = simpleTags?.split(",")?.map { it.trim() } ?: emptyList(),
        thumbWidth = thumbWidth,
        thumbHeight = thumbHeight,
        pages = pages,
        favoriteSlot = favoriteSlot,
        favoriteName = favoriteName
    )
}
