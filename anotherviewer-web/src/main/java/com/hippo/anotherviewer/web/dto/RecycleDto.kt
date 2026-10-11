package com.hippo.anotherviewer.web.dto

/**
 * v4 回收站（WP3）DTO 族：墓碑行（7 类同步实体）的列出 / 恢复 / 物理清除。
 *
 * key 的 wire 形态按实体类型（与 [com.hippo.anotherviewer.web.service.RecycleService] 的
 * 编码一致）：
 * - favorites / downloads / history / bookmarks → gid 的十进制字符串；
 * - filters → `"{type}:{text}"`；
 * - quickSearches → 预设名；downloadLabels → 标签名。
 */
data class RecycleItem(
    /** 实体类别（favorites/downloads/history/bookmarks/filters/quickSearches/downloadLabels）。 */
    val entity: String,
    /** 实体自然键（见类注释）。 */
    val key: String,
    /** 展示标题：内容实体 = title；bookmark = `{gid}-{category}`；filter = `{type}:{text}`；快搜/标签 = 名称。 */
    val title: String,
    /** 墓碑时间（= 行的 lastModified，删除发生的水位）。 */
    val deletedAt: Long,
    /** 剩余保留天数（max(0, retentionDays - 已过天数)）。 */
    val remainingDays: Long,
)

data class RecycleListResponse(
    val items: List<RecycleItem>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
)

/** 恢复/清除的单项定位（entity + key 与 [RecycleItem] 同一编码）。 */
data class RecycleRestoreItem(
    val entity: String,
    val key: String,
)

data class RecycleRestoreRequest(
    val items: List<RecycleRestoreItem> = emptyList(),
)

data class RecycleRestoreResponse(
    val restored: Int,
    val failed: Int,
)

/** items 缺省/为空 = 清除全部到期项（与定时任务同一路径）；非空 = 立即清除指定项。 */
data class RecyclePurgeRequest(
    val items: List<RecycleRestoreItem>? = null,
)

data class RecyclePurgeResponse(
    val purged: Int,
)
