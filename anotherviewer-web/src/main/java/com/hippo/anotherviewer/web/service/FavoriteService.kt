package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.dto.FavoriteItem
import com.hippo.anotherviewer.web.dto.FavoriteListResponse
import com.hippo.anotherviewer.web.entity.LocalFavoriteInfoEntity
import com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository
import org.springframework.stereotype.Service

@Service
class FavoriteService(
    private val favoriteRepository: LocalFavoriteInfoRepository,
    private val historyRepository: com.hippo.anotherviewer.web.repository.HistoryInfoRepository,
) {

    /**
     * List favorites with 1-based pagination (contract: `page` starts at 1)
     * and folder-slot filtering aligned with the Android `FavoritesScene`
     * tabs (F-UX5):
     *
     *  - `slot == 0` → the default folder: rows with `favoriteSlot` in
     *    (-1, 0). -1 is "added with the default folder", 0 is explicit
     *    Favorites 0; the app's first tab shows both.
     *  - `slot > 0`  → exactly that custom folder (`favoriteSlot == slot`).
     *  - `slot < 0`  → all slots. The openapi contract only documents slots
     *    0-9 and only a 200 response for this endpoint, so out-of-domain
     *    negative values keep the legacy total mapping instead of gaining an
     *    undocumented 4xx; no first-party client sends them (WebUI tabs are
     *    0-9).
     */
    fun listFavorites(slot: Int, page: Int, pageSize: Int = 20, q: String? = null, regex: Boolean = false): FavoriteListResponse {
        val startPage = page.coerceAtLeast(1)
        // 墓碑行（deleted=true 的同步删除记录）不列进 REST 列表，对齐 HistoryService：
        // 增量同步需要墓碑行落库（SyncService.mergeFavorite），但 /favorite/list
        // 只呈现存活收藏；total/分页也只按存活行计（R4-17）。
        val all = favoriteRepository.findAllByOrderByTimeDesc().filter { !it.deleted }
        val slotFiltered = when {
            slot == 0 -> all.filter { it.favoriteSlot == SLOT_DEFAULT_FOLDER || it.favoriteSlot == 0 }
            slot > 0 -> all.filter { it.favoriteSlot == slot }
            else -> all
        }
        // 筛选槽位（q 筛选）：q 非空时按 title/titleJpn 匹配——regex=false 为
        // 大小写不敏感子串（contains(ignoreCase)）；regex=true 时 q 按正则解释
        // （过滤链末端追加，slot 过滤照旧先行，total/分页只按匹配后行数计）。
        // 非法正则抛 IllegalArgumentException → 控制器转 400 REGEX_INVALID。
        val qFilter = q?.takeIf { it.isNotBlank() }
        val filtered = if (qFilter != null) {
            val matcher = if (regex) {
                try {
                    Regex(qFilter)
                } catch (e: Exception) {
                    throw IllegalArgumentException("正则表达式无效: ${e.message}")
                }
            } else null
            slotFiltered.filter { entity ->
                val t = entity.title ?: ""
                val tj = entity.titleJpn ?: ""
                if (matcher != null) matcher.containsMatchIn(t) || matcher.containsMatchIn(tj)
                else t.contains(qFilter, ignoreCase = true) || tj.contains(qFilter, ignoreCase = true)
            }
        } else slotFiltered
        val total = filtered.size
        val totalPages = (total + pageSize - 1) / pageSize
        val paged = filtered.drop((startPage - 1) * pageSize).take(pageSize)
        // 阅读进度批量查（findByGidIn 防 N+1）：同 gid 历史行的 page 即当前进度。
        val progressByGid = historyRepository.findByGidIn(paged.map { it.gid })
            .associate { it.gid to it.page }
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
                posted = entity.posted,
                // F-UX5: 条目真实 slot 随行下发——收藏页的 ♥ 徽章据此渲染真值，
                // 不再退化为当前页签号。
                favoriteSlot = entity.favoriteSlot,
                readProgress = progressByGid[entity.gid]
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
