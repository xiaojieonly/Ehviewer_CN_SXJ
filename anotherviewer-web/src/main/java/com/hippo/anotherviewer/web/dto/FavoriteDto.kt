package com.hippo.anotherviewer.web.dto

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
    val category: String,
    val rating: Float,
    val uploader: String?,
    val posted: String?
)

data class FavoriteAddRequest(
    val gid: Long,
    val token: String,
    val category: Int
)

data class FavoriteRemoveRequest(
    val gid: Long,
    val token: String,
    val category: Int
)
