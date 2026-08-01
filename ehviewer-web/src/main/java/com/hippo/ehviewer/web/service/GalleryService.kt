package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.data.GalleryInfo
import com.hippo.ehviewer.client.data.ListUrlBuilder
import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.entity.GalleryInfoBase
import com.hippo.ehviewer.web.entity.HistoryInfoEntity
import com.hippo.ehviewer.web.entity.QuickSearchEntity
import com.hippo.ehviewer.web.repository.*
import com.hippo.network.UrlBuilder
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.net.URLEncoder

@Service
class GalleryService(
    private val historyRepository: HistoryInfoRepository,
    private val quickSearchRepository: QuickSearchRepository,
    private val galleryTagsRepository: GalleryTagsRepository,
    private val localFavoriteInfoRepository: LocalFavoriteInfoRepository,
    private val sessionManager: EhSessionManager
) {
    private val logger = LoggerFactory.getLogger(GalleryService::class.java)
    private val client get() = sessionManager.okHttpClient

    /**
     * Real E-Hentai search via the core list parser (EhEngine.getGalleryList).
     * The history-based in-memory filter is kept only as a fallback for empty
     * keywords, where there is nothing to search upstream for.
     *
     * `category` is the f_cats exclusion bitmask exactly as the frontend sends
     * it (see web-frontend SearchView `categoryParam()`), so it is passed
     * through untouched. `page` is 0-based, matching the EH page parameter.
     */
    fun searchGallery(
        keyword: String?,
        category: Int?,
        page: Int,
        pageSize: Int
    ): GalleryListResponse {
        if (keyword.isNullOrBlank()) {
            return searchLocalHistory(keyword, category, page, pageSize)
        }

        return try {
            val builder = UrlBuilder(EhUrl.getHost())
            if (category != null && category != 0) {
                builder.addQuery("f_cats", category)
            }
            builder.addQuery("f_search", URLEncoder.encode(keyword.trim(), "UTF-8"))
            if (page > 0) {
                builder.addQuery("page", page)
            }

            val result = EhEngine.getGalleryList(null, client, builder.build(), ListUrlBuilder.MODE_NORMAL)
            val items = result.galleryInfoList.map { it.toDto() }
            // EH lists 25 results per page; `result.pages` is the number of
            // result pages when the pager was parseable.
            val total = if (result.pages > 0) result.pages * 25 else items.size
            GalleryListResponse(
                success = true,
                data = items,
                total = total
            )
        } catch (e: Exception) {
            logger.warn("E-Hentai search failed for keyword={}", keyword, e)
            GalleryListResponse(success = false, data = emptyList(), total = 0)
        }
    }

    /** Local history fallback (empty keyword): DB-paginated, newest first. */
    private fun searchLocalHistory(keyword: String?, category: Int?, page: Int, pageSize: Int): GalleryListResponse {
        val pageable = PageRequest.of(page.coerceAtLeast(0), pageSize.coerceAtLeast(1))
        val result = when {
            // Defensive: a non-blank keyword routed to the fallback is matched
            // against local history (title/titleJpn LIKE) instead of a full scan.
            !keyword.isNullOrBlank() ->
                historyRepository.findByTitleContainingIgnoreCaseOrTitleJpnContainingIgnoreCaseOrderByTimeDesc(keyword.trim(), pageable)
            category == null || category == 0 ->
                historyRepository.findHistoryPaged(pageable)
            else ->
                historyRepository.findByCategoryOrderByTimeDesc(category, pageable)
        }
        return GalleryListResponse(
            success = true,
            data = result.content.map { it.toDto() },
            total = result.totalElements.toInt()
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

    private fun GalleryInfo.toDto() = GalleryItemDto(
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
        simpleTags = simpleTags?.map { it.trim() } ?: emptyList(),
        thumbWidth = thumbWidth,
        thumbHeight = thumbHeight,
        pages = pages,
        favoriteSlot = favoriteSlot,
        favoriteName = favoriteName
    )
}
