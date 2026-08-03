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
    val posted: String?
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
