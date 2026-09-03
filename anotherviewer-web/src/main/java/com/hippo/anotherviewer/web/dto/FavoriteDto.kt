package com.hippo.anotherviewer.web.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

data class FavoriteListResponse(
    val favorites: List<FavoriteItem>,
    val totalPages: Int,
    val currentPage: Int
)

data class FavoriteItem(
    val gid: Long,
    val token: String,
    val title: String,
    val titleJpn: String,
    val thumb: String,
    val category: Int,
    val rating: Float,
    val uploader: String?,
    val posted: String?,
    // F-UX5: 条目真实收藏槽位（Android 契约：-2 未收藏 / -1 默认夹 / 0-9 自定义夹）。
    // 列表响应附加字段（openapi FavoriteItem 未限制 additionalProperties），
    // 供收藏页 ♥ 徽章渲染真值；旧客户端忽略该字段不受影响。
    val favoriteSlot: Int = -2,
    // 阅读进度（0 起页索引，来自同 gid 历史行；无历史行为 null），
    // 收藏页卡片角标用；缺省/0 时前端不显示角标。
    val readProgress: Int? = null
)

data class FavoriteAddRequest(
    @field:Min(1, message = "gid must be a positive number")
    val gid: Long,
    // token may legitimately be empty for locally-created favorites, so it is
    // only length-bounded, never required.
    @field:Size(max = 64, message = "token must be at most 64 characters")
    val token: String,
    // category is a site bitmask (up to 512), intentionally unconstrained.
    val category: Int,
    // Android favoriteSlot semantics: -1 = default folder, 0-9 = custom slots.
    // Optional: clients that don't target a folder omit it and get the default.
    val slot: Int = -1
)

data class FavoriteRemoveRequest(
    @field:Min(1, message = "gid must be a positive number")
    val gid: Long,
    val token: String,
    val category: Int
)
