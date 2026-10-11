package com.hippo.anotherviewer.web.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.hippo.anotherviewer.client.SiteConfig
import com.hippo.anotherviewer.client.SiteEngine
import com.hippo.anotherviewer.client.SiteUrl
import com.hippo.anotherviewer.client.SiteUtils
import com.hippo.anotherviewer.client.data.GalleryComment
import com.hippo.anotherviewer.client.data.GalleryDetail
import com.hippo.anotherviewer.client.data.GalleryInfo
import com.hippo.anotherviewer.client.data.ListUrlBuilder
import com.hippo.anotherviewer.client.data.topList.TopListItem
import com.hippo.anotherviewer.web.dto.*
import com.hippo.anotherviewer.web.entity.GalleryInfoBase
import com.hippo.anotherviewer.web.entity.HistoryInfoEntity
import com.hippo.anotherviewer.web.entity.QuickSearchEntity
import com.hippo.anotherviewer.web.repository.*
import com.hippo.anotherviewer.widget.AdvanceSearchTable
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class GalleryService(
    private val historyRepository: HistoryInfoRepository,
    private val quickSearchRepository: QuickSearchRepository,
    private val galleryTagsRepository: GalleryTagsRepository,
    private val localFavoriteInfoRepository: LocalFavoriteInfoRepository,
    private val sessionManager: SiteSessionManager,
    private val downloadRepository: com.hippo.anotherviewer.web.repository.DownloadInfoRepository,
    private val config: com.hippo.anotherviewer.web.config.SiteCoreConfigProperties,
    private val galleryLookupService: GalleryLookupService,
    private val availability: EhAvailabilityService,
    private val downloadDirIndex: DownloadDirIndex,
    private val serverConfig: ServerConfigService,
    private val usernameProvider: com.hippo.anotherviewer.web.config.CurrentUsernameProvider,
    private val historyService: HistoryService,
    // v4（WP2）：快搜直写路径与 sync 合并共用服务器打戳时钟。可空缺省仅为既有直构
    // 测试的源兼容保留——null 时回退墙钟。
    private val syncClock: SyncClock? = null,
) {
    private val logger = LoggerFactory.getLogger(GalleryService::class.java)
    private val client get() = sessionManager.okHttpClient

    /** v4 打戳助手：有 SyncClock 走服务器单调时钟，直构测试场景回落墙钟。 */
    private fun stamp(): Long = syncClock?.next() ?: System.currentTimeMillis()

    private companion object {
        /** Failure cause carried by blocked list/detail responses. */
        const val EH_UNAVAILABLE_CAUSE = "EH_UNAVAILABLE"

        /** P2: toplist 缓存固定 key（该端点无参数，单槽即可）。 */
        const val TOPLIST_CACHE_KEY = "toplist"
    }

    /**
     * P2: toplist 结果缓存（5min）。查询在 availability.isBlocked() 之前，
     * DOWN 期间命中缓存照常返回陈旧内容；只缓存 success=true 且非空的结果。
     */
    private val topListCache: Cache<String, TopListResponse> = Caffeine.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build()

    /**
     * P2: 站点搜索结果缓存（2min），key = buildSearchUrl 产物。查询在
     * availability.isBlocked() 之前（DOWN 命中缓存照常返回陈旧内容）；
     * 空关键词（首页）与关键词搜索共用站点请求路径，本地历史回退不经此缓存。
     */
    private val searchCache: Cache<String, GalleryListResponse> = Caffeine.newBuilder()
        .expireAfterWrite(2, TimeUnit.MINUTES)
        .maximumSize(128)
        .build()

    /**
     * P1: 站点 feed 结果缓存（2min），key = "$mode:$page:$pageSize"。查询在
     * availability.isBlocked() 之前（DOWN 命中缓存照常返回陈旧内容，同
     * topListCache/searchCache 先例）；仅 success=true 且非空的结果落缓存。
     * 已知取舍：缓存的是富化后的 GalleryListResponse（含 favoriteName/
     * readProgress），2 分钟内收藏/进度角标变化不 reflected——与 searchCache
     * 的既有取舍一致，不引入新的不一致类别。
     */
    private val feedCache: Cache<String, GalleryListResponse> = Caffeine.newBuilder()
        .expireAfterWrite(2, TimeUnit.MINUTES)
        .maximumSize(64)
        .build()

    /**
     * Real Gallery Site search via the core list parser (SiteEngine.getGalleryList).
     * A1 home-page flip: a blank keyword IS the home page — the site latest
     * list (host root path, no login needed) is served first, exactly like the
     * Android home page. [searchLocalHistory] is kept only as the blank-keyword
     * fallback for when the site is DOWN (blocked short-circuit or upstream
     * failure), so offline local content stays usable.
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
     *  - W3 R4-10 higher AdvanceSearchTable bits (backendized so the WebUI's
     *    single filter surface can express the full Android option set):
     *    [searchTorrentsOnly] -> `f_sto=on`, [searchLowPowerTags] -> `f_sdt1=on`,
     *    [searchDownvotedTags] -> `f_sdt2=on`, [searchExpunged] -> `f_sh=on`,
     *    [disableLanguageFilter] -> `f_sfl=on`, [disableUploaderFilter] -> `f_sfu=on`,
     *    [disableTagFilter] -> `f_sft=on`
     *
     * E2E-6 failure semantics preserved for keyword searches: an unreachable
     * site surfaces `success=false` with empty data — never fabricated
     * fallback results. The blank-keyword home path is the deliberate
     * exception: DOWN there degrades to [searchLocalHistory] instead.
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
        searchTorrents: Boolean = false,
        searchTorrentsOnly: Boolean = false,
        searchLowPowerTags: Boolean = false,
        searchDownvotedTags: Boolean = false,
        searchExpunged: Boolean = false,
        disableLanguageFilter: Boolean = false,
        disableUploaderFilter: Boolean = false,
        disableTagFilter: Boolean = false
    ): GalleryListResponse {
        if (keyword.isNullOrBlank()) {
            // A1 首页语义翻转：空 keyword = 首页 = 站点最新列表优先（Android 首页 =
            // 站点根路径最新画廊，无需登录），高级筛选参数原样进入站点 URL。
            // EH DOWN（isBlocked 短路或上游异常）回退本地历史（E2E-6「只读本地
            // 内容仍可用」），首页本地链路不受影响。
            return try {
                val url = buildSearchUrl(
                    "", category, page, sort, pageMin, pageMax, minRating,
                    searchName, searchTags, searchDesc, searchTorrents,
                    searchTorrentsOnly, searchLowPowerTags, searchDownvotedTags,
                    searchExpunged, disableLanguageFilter, disableUploaderFilter,
                    disableTagFilter
                )
                // P2: 缓存查询在 isBlocked() 之前——DOWN 期间命中缓存照常返回陈旧内容。
                searchCache.getIfPresent(url)?.let { return it }
                if (availability.isBlocked()) {
                    // EH DOWN：跳过站点请求（秒回，不触网），回退本地历史浏览。
                    logger.debug("Gallery latest list skipped: EH unavailable (empty keyword)")
                    return searchLocalHistory(keyword, category, page, pageSize)
                }
                val result = SiteEngine.getGalleryList(null, client, url, ListUrlBuilder.MODE_NORMAL)
                val items = result.galleryInfoList.map { it.toDto() }
                val total = if (result.pages > 0) result.pages * 25 else items.size
                val response = GalleryListResponse(success = true, data = items, total = total)
                // P2: 只缓存 success=true 且非空的结果。
                if (items.isNotEmpty()) searchCache.put(url, response)
                response
            } catch (e: Exception) {
                logger.warn("Gallery Site latest list failed (empty keyword)", e)
                // 上游异常同样回退本地历史：首页永不硬失败（E2E-6 本地可用语义）。
                searchLocalHistory(keyword, category, page, pageSize)
            }
        }

        return try {
            val url = buildSearchUrl(
                keyword, category, page, sort, pageMin, pageMax, minRating,
                searchName, searchTags, searchDesc, searchTorrents,
                searchTorrentsOnly, searchLowPowerTags, searchDownvotedTags,
                searchExpunged, disableLanguageFilter, disableUploaderFilter,
                disableTagFilter
            )
            // P2: 缓存查询在 isBlocked() 之前——DOWN 期间命中缓存照常返回陈旧内容。
            searchCache.getIfPresent(url)?.let { return it }
            if (availability.isBlocked()) {
                // EH DOWN：所有自动搜索直接短路（毫秒级），不发起上游请求。
                logger.debug("Gallery search skipped for keyword={}: EH unavailable", keyword)
                return ehBlockedListResponse()
            }
            val result = SiteEngine.getGalleryList(null, client, url, ListUrlBuilder.MODE_NORMAL)
            val items = result.galleryInfoList.map { it.toDto() }
            // EH lists 25 results per page; `result.pages` is the number of
            // result pages when the pager was parseable.
            val total = if (result.pages > 0) result.pages * 25 else items.size
            val response = GalleryListResponse(
                success = true,
                data = items,
                total = total
            )
            // P2: 只缓存 success=true 且非空的结果；失败/空结果照旧直返，不缓存。
            if (items.isNotEmpty()) searchCache.put(url, response)
            response
        } catch (e: Exception) {
            logger.warn("Gallery Site search failed for keyword={}", keyword, e)
            GalleryListResponse(success = false, data = emptyList(), total = 0)
        }
    }

    /**
     * Gallery Site feed (contracts/openapi.yaml GET /api/v1/gallery/feed):
     * `subscription` = watched galleries, `popular` = what's hot. Both reuse
     * the shared core [ListUrlBuilder] and the same total semantics as
     * [searchGallery] (result pages × 25, EH lists 25 per page).
     *
     * E2E-6 failure semantics preserved: an unreachable site (or a watched
     * redirect to the login page when unauthenticated) surfaces
     * `success=false` with empty data.
     */
    fun feedGallery(mode: String, page: Int, pageSize: Int): GalleryListResponse {
        // P1: 缓存查询在 isBlocked() 之前——DOWN 期间命中缓存照常返回陈旧内容。
        val cacheKey = "$mode:$page:$pageSize"
        feedCache.getIfPresent(cacheKey)?.let { return it }
        if (availability.isBlocked()) {
            logger.debug("Gallery feed skipped for mode={}: EH unavailable", mode)
            return ehBlockedListResponse()
        }
        return try {
            val siteMode = feedMode(mode)
            val url = buildFeedUrl(mode, page)
            val result = SiteEngine.getGalleryList(null, client, url, siteMode)
            val items = result.galleryInfoList.map { it.toDto() }
            val total = if (result.pages > 0) result.pages * 25 else items.size
            val response = GalleryListResponse(success = true, data = items, total = total)
            // P1: 只缓存 success=true 且非空的结果；失败/空结果照旧直返，不缓存。
            if (items.isNotEmpty()) feedCache.put(cacheKey, response)
            response
        } catch (e: Exception) {
            logger.warn("Gallery Site feed failed for mode={}: {}", mode, e.message)
            GalleryListResponse(success = false, data = emptyList(), total = 0)
        }
    }

    /**
     * Top-list feed (contracts/openapi.yaml GET /api/v1/gallery/feed mode
     * `toplist`): fetches the gallery top list and flattens the first
     * non-empty time slot — yesterday first, then past month / past year /
     * all time (the core parser allocates 10 slots, trailing ones stay null).
     */
    fun topListFeed(): TopListResponse {
        // P2: 缓存查询在 isBlocked() 之前——DOWN 期间命中缓存照常返回陈旧内容。
        // 缓存存原始解析结果；打码脱敏在出口（redactTopList）做，开关即时生效。
        topListCache.getIfPresent(TOPLIST_CACHE_KEY)?.let { return redactTopList(it) }
        if (availability.isBlocked()) {
            logger.debug("Gallery top list skipped: EH unavailable")
            return TopListResponse(success = false, data = emptyList(), total = 0, cause = EH_UNAVAILABLE_CAUSE)
        }
        return try {
            val detail = SiteEngine.getTopList(null, client, SiteUrl.getTopListUrl())
            val info = detail.galleryTopListInfo
            val selected = (0 until (info?.size() ?: 0)).firstNotNullOfOrNull { i ->
                info.get(i)?.itemArray?.takeIf { it.isNotEmpty() }
            }
            val items = selected?.filterNotNull()?.map { it.toDto() } ?: emptyList()
            val response = TopListResponse(success = true, data = items, total = items.size)
            // P2: 只缓存 success=true 且非空的结果。
            if (items.isNotEmpty()) topListCache.put(TOPLIST_CACHE_KEY, response)
            redactTopList(response)
        } catch (e: Exception) {
            logger.warn("Gallery Site top list failed: {}", e.message)
            TopListResponse(success = false, data = emptyList(), total = 0)
        }
    }

    /**
     * 排行榜打码（2026-09-04 用户裁决）：开码时不输出解析出的实际标题——
     * value → `#gid`（gid 取自 href 的 /g/<gid>/ 段），href（含 token 的
     * 站点地址）清空。关码原样返回。缓存放原始数据，脱敏在出口做，切换
     * 开关即时生效（最多一次请求的延迟）。
     */
    private fun redactTopList(response: TopListResponse): TopListResponse {
        if (!serverConfig.getBoolean(ServerConfigService.KEY_PRIVACY_MASK)) return response
        val gidRegex = Regex("/g/(\\d+)")
        return response.copy(
            data = response.data.map { item ->
                val gid = item.gid
                    ?: item.href?.let { href -> gidRegex.find(href)?.groupValues?.get(1) }
                item.copy(gid = gid, href = "", value = gid?.let { "#$it" } ?: "")
            }
        )
    }

    /** Feed-mode → core ListUrlBuilder mode mapping (only valid modes reach here). */
    private fun feedMode(mode: String): Int = when (mode) {
        "subscription" -> ListUrlBuilder.MODE_SUBSCRIPTION
        else -> ListUrlBuilder.MODE_WHATS_HOT
    }

    /** Builds the upstream feed URL through the shared core [ListUrlBuilder]. */
    internal fun buildFeedUrl(mode: String, page: Int): String {
        val builder = ListUrlBuilder()
        builder.mode = feedMode(mode)
        builder.pageIndex = page
        return builder.build()
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
        searchTorrents: Boolean,
        searchTorrentsOnly: Boolean = false,
        searchLowPowerTags: Boolean = false,
        searchDownvotedTags: Boolean = false,
        searchExpunged: Boolean = false,
        disableLanguageFilter: Boolean = false,
        disableUploaderFilter: Boolean = false,
        disableTagFilter: Boolean = false
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
        // W3 R4-10: higher AdvanceSearchTable bits (backendized).
        if (searchTorrentsOnly) advance = advance or AdvanceSearchTable.STO
        if (searchLowPowerTags) advance = advance or AdvanceSearchTable.SDT1
        if (searchDownvotedTags) advance = advance or AdvanceSearchTable.SDT2
        if (searchExpunged) advance = advance or AdvanceSearchTable.SH
        if (disableLanguageFilter) advance = advance or AdvanceSearchTable.SFL
        if (disableUploaderFilter) advance = advance or AdvanceSearchTable.SFU
        if (disableTagFilter) advance = advance or AdvanceSearchTable.SFT
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

    /** Local history fallback (blank keyword, EH DOWN / upstream failure): DB-paginated, newest first. */
    private fun searchLocalHistory(keyword: String?, category: Int?, page: Int, pageSize: Int): GalleryListResponse {
        val pageable = PageRequest.of(page.coerceAtLeast(0), pageSize.coerceAtLeast(1))
        val result = when {
            // Defensive: a non-blank keyword routed to the fallback is matched
            // against local history (title/titleJpn LIKE) instead of a full scan.
            // A7-2（H2）：改用仅存活行的 LIKE 查询（与 REST 历史检索同一条 JPQL，
            // deleted=false 内建），墓碑不进本地回退列表。
            !keyword.isNullOrBlank() ->
                historyRepository.findLiveByTitleOrTitleJpnContainingPaged(keyword.trim(), pageable)
            category == null || category == 0 ->
                historyRepository.findHistoryPaged(pageable)
            else ->
                // A7-2（H2）：分类路径同样仅存活行。
                historyRepository.findByCategoryAndDeletedFalseOrderByTimeDesc(category, pageable)
        }
        return GalleryListResponse(
            success = true,
            data = result.content.map { it.toDto() },
            total = result.totalElements.toInt()
        )
    }

    /**
     * Gallery detail, resolved in order:
     *  1. local download row (complete local source incl. pages — zero upstream),
     *  2. local history row (local DTO built immediately; site-detail enrichment
     *     is attempted only while EH is reachable, otherwise skipped entirely),
     *  3. on-site fetch when the gid is not local and a [token] is supplied
     *     (feed / search entries carry it) — but never while EH is DOWN
     *     (P1: resolved via [GalleryLookupService.getDetailCached], sharing the
     *     10min per-gid detail cache with the history enrichment path),
     *  4. local favorite row (local DTO, no upstream — EH-down safe),
     *  5. null.
     */
    fun getGalleryDetail(gid: Long, token: String? = null): GalleryDetailDto? {
        // 1. 本地推送下载行直接作为 detail 来源——pages 取行内 total
        //    （<=0 时数落盘文件），零 EH 依赖，阅读器必须能开。
        //    A7-2（D10）：墓碑下载行跳过（落到下一分支，不得以删除记录开详情）。
        //    A7-3（P1-1）：查找 List 化；读路径不按属主过滤（本地可见性语义保持，
        //    firstOrNull 与原 findByGid 等价）——下同，history/favorite 分支。
        val download = downloadRepository.findAllByGid(gid).firstOrNull()?.takeUnless { it.deleted }
        if (download != null) {
            return downloadDetailDto(download)
        }

        // 2. 历史行：本地 dto 立即构造；仅站点可达时尝试上游补强（评论等真实字段）。
        //    A7-2（H3）：墓碑历史行跳过（清空历史/设备删除后详情不再以墓碑为源）。
        val history = historyRepository.findAllByGid(gid).firstOrNull()?.takeUnless { it.deleted }
        if (history != null) {
            return enrichHistoryDetail(gid, history)
        }

        // 3. token 非空且站点可达 → 上游直取 + 落历史。DOWNLOAD 时跳过，绝不等待网络。
        //    P1: 改走 getDetailCached（gid 键 detailCache 10min 复用）——二次打开
        //    零上游请求，且与 enrichHistoryDetail 共享同一缓存条目。上游失败吞异常
        //    返回 null（失败原因不再细分记日志，与既有 enrichment 路径一致）。
        if (!token.isNullOrBlank() && !availability.isBlocked()) {
            val detail = galleryLookupService.getDetailCached(gid, token) ?: run {
                logger.warn("Gallery Site detail fetch failed for gid={}", gid)
                return null
            }
            addToHistory(gid, token, detail.title, 0)
            return detail.toDetailDto()
        }

        // 4. 收藏行：无历史/下载的收藏条目在 EH DOWN 时仍可打开详情（本地 token/标题/缩略图）。
        //    A7-2（F2）：墓碑收藏行跳过（applyFavoriteFields 会把墓碑字段清空，渲染垃圾详情）。
        val favorite = localFavoriteInfoRepository.findAllByGid(gid).firstOrNull()?.takeUnless { it.deleted }
        if (favorite != null) {
            return favoriteDetailDto(favorite)
        }

        return null
    }

    /** 本地收藏行 → detail DTO（EH 断网收藏详情可开；pages 尝试索引/上游解析，失败回落收藏行值）。 */
    private fun favoriteDetailDto(favorite: com.hippo.anotherviewer.web.entity.LocalFavoriteInfoEntity): GalleryDetailDto {
        val pages = try {
            galleryLookupService.resolvePageCount(favorite.gid) ?: favorite.pages
        } catch (e: Exception) {
            logger.warn("Failed to resolve page count for favorite gid={}: {}", favorite.gid, e.message)
            favorite.pages
        }
        return GalleryDetailDto(
            gid = favorite.gid,
            token = favorite.token,
            galleryUrl = SiteUrl.getGalleryDetailUrl(favorite.gid, favorite.token),
            title = favorite.title,
            titleJpn = favorite.titleJpn,
            thumb = favorite.thumb,
            category = favorite.category,
            posted = favorite.posted,
            uploader = favorite.uploader,
            rating = favorite.rating,
            rated = favorite.rated,
            simpleLanguage = favorite.simpleLanguage,
            simpleTags = favorite.simpleTags?.split(",")?.map { it.trim() } ?: emptyList(),
            thumbWidth = favorite.thumbWidth,
            thumbHeight = favorite.thumbHeight,
            pages = pages,
            favoriteSlot = favorite.favoriteSlot,
            favoriteName = favorite.favoriteName,
            tags = emptyList(),
            imageUrl = favorite.thumb,
            readProgress = readProgressOf(favorite.gid)
        )
    }

    /**
     * 本地下载行 → detail DTO（统一阅读器回退）。EH 不可达或从未同步元数据时
     * 阅读器仍可打开：pages 优先级 = 行内 total > 落盘推送文件数 > 上游
     * resolvePageCount（带 detailCache，失败静默）> 0（仍返回 DTO，阅读器
     * 按未知页数门限）。
     */
    private fun downloadDetailDto(download: com.hippo.anotherviewer.web.entity.DownloadInfoEntity): GalleryDetailDto {
        val pages = resolveDownloadPages(download)
        return GalleryDetailDto(
            gid = download.gid,
            token = download.token,
            galleryUrl = SiteUrl.getGalleryDetailUrl(download.gid, download.token),
            title = download.title,
            titleJpn = download.titleJpn,
            thumb = download.thumb,
            category = download.category,
            posted = download.posted,
            uploader = download.uploader,
            rating = download.rating,
            rated = download.rated,
            simpleLanguage = download.simpleLanguage,
            simpleTags = download.simpleTags?.split(",")?.map { it.trim() } ?: emptyList(),
            thumbWidth = download.thumbWidth,
            thumbHeight = download.thumbHeight,
            pages = pages,
            favoriteSlot = download.favoriteSlot,
            favoriteName = download.favoriteName,
            tags = emptyList(),
            imageUrl = download.thumb,
            readProgress = readProgressOf(download.gid)
        )
    }

    private fun countPushedPages(gid: Long): Int = downloadDirIndex.pageCount(gid)

    /**
     * 下载行页数三级 fallback（2026-08-30 联调：导入 .db 的 8797/8799 行
     * total=0 且 downloads/ 目录为空 → 阅读器 0 页降级「只读第一页」）：
     * ① 行内 total（App 推送/正常同步通常有值）→ ② 落盘推送文件
     * （downloads/<gid>/%04d.*，read 已下载内容）→ ③ 上游 resolvePageCount
     * （detailCache 10min，可达时救回历史/可用 token）→ ④ 0（DTO 仍返回，
     * 阅读器按「未知页数」处理而非卡 1 页）。
     */
    private fun resolveDownloadPages(download: com.hippo.anotherviewer.web.entity.DownloadInfoEntity): Int {
        if (download.total > 0) return download.total
        val pushed = countPushedPages(download.gid)
        if (pushed > 0) return pushed
        return try {
            galleryLookupService.resolvePageCount(download.gid) ?: 0
        } catch (e: Exception) {
            logger.warn("Failed to resolve page count for download gid={}: {}", download.gid, e.message)
            0
        }
    }

    /**
     * Local history → detail DTO. The site detail is attempted only while EH is
     * reachable so the response carries the real comments (and fills missing
     * fields); an unreachable site keeps the history-based dto (E2E-6
     * semantics) — while DOWN the enrichment is skipped entirely (no upstream
     * wait; P-C fix).
     *
     * P1: 上游补强改走 [GalleryLookupService.getDetailCached]（gid 键 detailCache
     * 10min 复用），二次点击同一画廊零上游请求。readProgress 直接取行内 page
     * （行已在手，勿重复查询），enrichment 的 copy() 原样保留。
     */
    private fun enrichHistoryDetail(gid: Long, history: HistoryInfoEntity): GalleryDetailDto {
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
            imageUrl = history.thumb,
            readProgress = history.page
        )
        if (availability.isBlocked()) {
            logger.debug("Site detail enrichment skipped for gid={}: EH unavailable", gid)
            return dto
        }
        return try {
            val detail = galleryLookupService.getDetailCached(history.gid, history.token)
                ?: return dto
            dto.copy(
                title = dto.title ?: detail.title,
                titleJpn = dto.titleJpn ?: detail.titleJpn,
                thumb = dto.thumb ?: detail.thumb,
                category = if (dto.category != 0) dto.category else detail.category,
                posted = dto.posted ?: detail.posted,
                uploader = dto.uploader ?: detail.uploader,
                rating = if (dto.rating != 0f) dto.rating else detail.rating,
                simpleTags = dto.simpleTags.ifEmpty { detail.simpleTags?.toList() ?: emptyList() },
                pages = if (dto.pages > 0) dto.pages else detail.pages,
                imageUrl = dto.imageUrl ?: detail.thumb,
                comments = detail.comments?.comments?.toList().orEmpty().map { it.toCommentItem() }
            )
        } catch (e: Exception) {
            logger.warn("Site detail fallback failed for gid={}: {}", gid, e.message)
            dto
        }
    }

    /** S5: 已存阅读进度（0 起页索引）；无历史行视为 0（未读）。A7-2（H3）：墓碑行不计进度。
        A7-3（P1-1）：查找 List 化；读路径不按属主过滤（本地可见性语义保持）。 */
    private fun readProgressOf(gid: Long): Int =
        historyRepository.findAllByGid(gid).firstOrNull()?.takeUnless { it.deleted }?.page ?: 0

    fun addToHistory(gid: Long, token: String, title: String?, mode: Int, page: Int? = null) {
        // A7-1：委托 HistoryService.addHistory——stamping（username/lastModified）、
        // 打码标题防污染与 page 语义（null 保持已存进度）单一实现点，与 REST 历史写
        // 路径完全一致；本方法曾是 second 落点导致新行 username=null/lastModified=0。
        historyService.addHistory(gid, token, title, null, null, 0, 0f, mode, page)
    }

    /** A7-2（Q1）：快搜墓碑行不列进 REST 列表（设备端删除经同步落墓碑）。 */
    fun getQuickSearches(): QuickSearchListResponse {
        val all = quickSearchRepository.findAllByDeletedFalseOrderById()
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
                    pageTo = it.pageTo,
                    sort = it.sort
                )
            }
        )
    }

    fun createQuickSearch(dto: QuickSearchDto): QuickSearchDto {
        // time 是业务时间字段，保留墙钟；lastModified 是同步水位（v4 服务器打戳）。
        val now = System.currentTimeMillis()
        // A7-2：同名墓碑行走复活（快搜按 name 联合合并，findByName 是单实体查询——
        // 墓碑旁再插一行同名活记录会让 mergeQuickSearch 命中两行直接抛
        // IncorrectResultSizeDataAccessException，毒化整条同步通道）；同名活行为现状
        // 语义（不查重，直接插入），本卡不改变。
        quickSearchRepository.findByName(dto.name)?.takeIf { it.deleted }?.let { tombstone ->
            tombstone.apply {
                name = dto.name
                mode = dto.mode
                category = dto.category
                keyword = dto.keyword
                advanceSearch = dto.advanceSearch
                minRating = dto.minRating
                pageFrom = dto.pageFrom
                pageTo = dto.pageTo
                sort = dto.sort
                time = now
                deleted = false
                if (username == null) username = usernameProvider.currentUsername()
                lastModified = stamp()
            }
            val revived = quickSearchRepository.save(tombstone)
            return QuickSearchDto(
                id = revived.id,
                name = revived.name,
                mode = revived.mode,
                category = revived.category,
                keyword = revived.keyword,
                advanceSearch = revived.advanceSearch,
                minRating = revived.minRating,
                pageFrom = revived.pageFrom,
                pageTo = revived.pageTo,
                sort = revived.sort
            )
        }
        val entity = QuickSearchEntity().apply {
            name = dto.name
            mode = dto.mode
            category = dto.category
            keyword = dto.keyword
            advanceSearch = dto.advanceSearch
            minRating = dto.minRating
            pageFrom = dto.pageFrom
            pageTo = dto.pageTo
            sort = dto.sort
            // A7-1 stamping：新行当场落属主与同步水位（deleted=false 为列默认值）。
            time = now
            username = usernameProvider.currentUsername()
            lastModified = stamp()
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
            pageTo = saved.pageTo,
            sort = saved.sort
        )
    }

    /**
     * A7-2：删除快搜 = 墓碑化（deleted=true + lastModified bump），行保留——
     * quickSearch 是同步软删实体，物理删会让删除永不传播、且 App 下次 push 同名
     * 预设时 union-merge 直接重建。不存在的 id 静默返回（对齐 deleteById 的旧行为）。
     */
    fun deleteQuickSearch(id: Long) {
        val row = quickSearchRepository.findById(id).orElse(null) ?: return
        if (row.deleted) return
        row.deleted = true
        row.lastModified = stamp()
        if (row.username == null) row.username = usernameProvider.currentUsername()
        quickSearchRepository.save(row)
    }

    /** Blocked list response: success=false, cause=EH_UNAVAILABLE, no upstream hit. */
    private fun ehBlockedListResponse() = GalleryListResponse(
        success = false,
        data = emptyList(),
        total = 0,
        cause = EH_UNAVAILABLE_CAUSE
    )

    private fun TopListItem.toDto() = TopListFeedItemDto(
        gid = gid,
        token = token,
        tag = tag,
        value = value,
        href = href
    )

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

    /** core GalleryDetail（站点直取）→ 详情 DTO。tags 按 GalleryTagGroup 展平；评论映射站点真实数据。 */
    private fun GalleryDetail.toDetailDto() = GalleryDetailDto(
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
        favoriteName = favoriteName,
        tags = tags.orEmpty().flatMap { group ->
            (0 until group.size()).map { i -> TagDto(group.groupName, group.getTagAt(i)) }
        },
        imageUrl = thumb,
        comments = comments?.comments?.toList().orEmpty().map { it.toCommentItem() }
    )

    /** 站点 GalleryComment → wire CommentItem（uploader=user，time 转可读字符串）。 */
    private fun GalleryComment.toCommentItem() = CommentItem(
        id = id,
        uploader = user,
        comment = comment,
        time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(time)),
        score = score,
    )
}
