package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.client.SiteConfig
import com.hippo.anotherviewer.client.SiteEngine
import com.hippo.anotherviewer.client.SiteUrl
import com.hippo.anotherviewer.client.SiteUtils
import com.hippo.anotherviewer.client.data.GalleryInfo
import com.hippo.anotherviewer.client.data.ListUrlBuilder
import com.hippo.anotherviewer.web.dto.*
import com.hippo.anotherviewer.web.entity.GalleryInfoBase
import com.hippo.anotherviewer.web.entity.HistoryInfoEntity
import com.hippo.anotherviewer.web.entity.QuickSearchEntity
import com.hippo.anotherviewer.web.repository.*
import com.hippo.anotherviewer.widget.AdvanceSearchTable
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class GalleryService(
    private val historyRepository: HistoryInfoRepository,
    private val quickSearchRepository: QuickSearchRepository,
    private val galleryTagsRepository: GalleryTagsRepository,
    private val localFavoriteInfoRepository: LocalFavoriteInfoRepository,
    private val sessionManager: SiteSessionManager
) {
    private val logger = LoggerFactory.getLogger(GalleryService::class.java)
    private val client get() = sessionManager.okHttpClient

    /**
     * Real Gallery Site search via the core list parser (SiteEngine.getGalleryList).
     * The history-based in-memory filter is kept only as a fallback for empty
     * keywords, where there is nothing to search upstream for.
     *
     * `category` is the f_cats exclusion bitmask exactly as the frontend sends
     * it (see web-frontend SearchView `categoryParam()`), so it reaches the
     * site URL unchanged (see [buildSearchUrl]). `page` is 0-based, matching
     * the EH page parameter.
     *
     * Extended params (contracts/openapi.yaml GET /api/v1/gallery/search,
     * all optional, absent = unchanged behavior):
     *  - [sort]        0..3 -> site `f_order` (0/absent = default order)
     *  - [pageMin]/[pageMax] -> `f_sp=on` + `f_spf` / `f_spt` (absent bound omitted)
     *  - [minRating]   1..5 -> `f_sr=on&f_srdd=N` (0 = disabled)
     *  - [searchName]/[searchTags]/[searchDesc]/[searchTorrents]
     *                  -> `advsearch=1` + `f_sname/f_stags/f_sdesc/f_storr=on`
     *
     * E2E-6 failure semantics preserved: an unreachable site surfaces
     * `success=false` with empty data — never fabricated fallback results.
     */
    fun searchGallery(
        keyword: String?,
        category: Int?,
        page: Int,
        pageSize: Int,
        sort: Int = 0,
        pageMin: Int? = null,
        pageMax: Int? = null,
        minRating: Int = 0,
        searchName: Boolean = false,
        searchTags: Boolean = false,
        searchDesc: Boolean = false,
        searchTorrents: Boolean = false
    ): GalleryListResponse {
        if (keyword.isNullOrBlank()) {
            return searchLocalHistory(keyword, category, page, pageSize)
        }

        return try {
            val url = buildSearchUrl(
                keyword, category, page, sort, pageMin, pageMax, minRating,
                searchName, searchTags, searchDesc, searchTorrents
            )
            val result = SiteEngine.getGalleryList(null, client, url, ListUrlBuilder.MODE_NORMAL)
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
            logger.warn("Gallery Site search failed for keyword={}", keyword, e)
            GalleryListResponse(success = false, data = emptyList(), total = 0)
        }
    }

    /**
     * Builds the upstream Gallery Site list URL through the shared Android
     * core [ListUrlBuilder], so the WebUI sends exactly the parameter shape
     * the app produces.
     *
     * [category] is the f_cats *exclusion* bitmask as the frontend sends it;
     * [ListUrlBuilder] stores the *selected* categories and re-inverts when
     * emitting, so invert once here and the original value reaches the URL
     * untouched. 0 (nothing excluded) maps to [SiteUtils.NONE], which omits
     * f_cats entirely — the default behavior. Degenerate edge: 0x3ff
     * ("exclude everything") also collapses to [SiteUtils.NONE] and yields
     * no f_cats — identical to the Android app, where all-deselected
     * categories cannot be expressed.
     *
     * `advsearch=1` is the carrier for `f_sr*` / `f_sp*` on the site, so it
     * is emitted whenever any advanced field is set, even with no scope flag.
     */
    internal fun buildSearchUrl(
        keyword: String,
        category: Int?,
        page: Int,
        sort: Int,
        pageMin: Int?,
        pageMax: Int?,
        minRating: Int,
        searchName: Boolean,
        searchTags: Boolean,
        searchDesc: Boolean,
        searchTorrents: Boolean
    ): String {
        val builder = ListUrlBuilder()
        builder.mode = ListUrlBuilder.MODE_NORMAL
        builder.keyword = keyword.trim()
        builder.category = if (category != null && category != 0) {
            category.inv() and SiteConfig.ALL_CATEGORY
        } else {
            SiteUtils.NONE
        }
        builder.pageIndex = page
        builder.order = sort

        var advance = 0
        if (searchName) advance = advance or AdvanceSearchTable.SNAME
        if (searchTags) advance = advance or AdvanceSearchTable.STAGS
        if (searchDesc) advance = advance or AdvanceSearchTable.SDESC
        if (searchTorrents) advance = advance or AdvanceSearchTable.STORR
        if (advance != 0 || minRating > 0 || pageMin != null || pageMax != null) {
            builder.advanceSearch = advance
            if (minRating > 0) {
                builder.minRating = minRating
            }
            if (pageMin != null) {
                builder.pageFrom = pageMin
            }
            if (pageMax != null) {
                builder.pageTo = pageMax
            }
        }
        return builder.build()
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
        val dto = GalleryDetailDto(
            gid = history.gid,
            token = history.token,
            galleryUrl = SiteUrl.getGalleryDetailUrl(history.gid, history.token),
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
        if (dto.pages > 0) return dto
        // History rows seeded via REST carry no page count; fetch the site
        // detail so the WebUI reader can open. E2E-6 semantics preserved: an
        // unreachable site keeps the (pageless) history-based dto.
        return try {
            val detail = SiteEngine.getGalleryDetail(
                null, client, SiteUrl.getGalleryDetailUrl(history.gid, history.token)
            )
            dto.copy(
                title = dto.title ?: detail.title,
                titleJpn = dto.titleJpn ?: detail.titleJpn,
                thumb = dto.thumb ?: detail.thumb,
                category = if (dto.category != 0) dto.category else detail.category,
                posted = dto.posted ?: detail.posted,
                uploader = dto.uploader ?: detail.uploader,
                rating = if (dto.rating != 0f) dto.rating else detail.rating,
                simpleTags = dto.simpleTags.ifEmpty { detail.simpleTags?.toList() ?: emptyList() },
                pages = detail.pages,
                imageUrl = dto.imageUrl ?: detail.thumb,
            )
        } catch (e: Exception) {
            logger.warn("Site detail fallback failed for gid={}: {}", gid, e.message)
            dto
        }
    }

    fun addToHistory(gid: Long, token: String, title: String?, mode: Int) {
        val existing = historyRepository.findByGid(gid)
        if (existing != null) {
            existing.time = System.currentTimeMillis()
            // R4-4: mode 透传写入 history 行（缺省 0 即回退默认值，与实体列默认一致）。
            existing.mode = mode
            historyRepository.save(existing)
        } else {
            val entity = HistoryInfoEntity().apply {
                this.gid = gid
                this.token = token
                this.title = title
                this.mode = mode
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
        galleryUrl = SiteUrl.getGalleryDetailUrl(gid, token),
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
        galleryUrl = SiteUrl.getGalleryDetailUrl(gid, token),
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
