package com.hippo.ehviewer.web.dto

data class GalleryListResponse(
    val success: Boolean,
    val data: List<GalleryItemDto>,
    val total: Int
)

data class GalleryItemDto(
    val gid: Long,
    val token: String,
    val title: String?,
    val titleJpn: String?,
    val thumb: String?,
    val category: Int,
    val posted: String?,
    val uploader: String?,
    val rating: Float,
    val rated: Boolean,
    val simpleLanguage: String?,
    val simpleTags: List<String>,
    val thumbWidth: Int,
    val thumbHeight: Int,
    val pages: Int,
    val favoriteSlot: Int,
    val favoriteName: String?
)

data class GalleryDetailDto(
    val gid: Long,
    val token: String,
    val title: String?,
    val titleJpn: String?,
    val thumb: String?,
    val category: Int,
    val posted: String?,
    val uploader: String?,
    val rating: Float,
    val rated: Boolean,
    val simpleLanguage: String?,
    val simpleTags: List<String>,
    val thumbWidth: Int,
    val thumbHeight: Int,
    val pages: Int,
    val favoriteSlot: Int,
    val favoriteName: String?,
    val tags: List<TagDto>,
    val imageUrl: String?
)

data class TagDto(
    val namespace: String?,
    val tag: String
)

data class DownloadListResponse(
    val success: Boolean,
    val data: List<DownloadItemDto>
)

data class DownloadItemDto(
    val id: Long,
    val gid: Long,
    val token: String,
    val title: String?,
    val titleJpn: String?,
    val thumb: String?,
    val category: Int,
    val state: Int,
    val total: Int,
    val done: Int,
    val label: Int,
    val downloadDir: String?
)

data class QuickSearchListResponse(
    val success: Boolean,
    val data: List<QuickSearchDto>
)

data class QuickSearchDto(
    val id: Long,
    val name: String,
    val mode: Int,
    val category: Int,
    val keyword: String?,
    val advanceSearch: Int,
    val minRating: Int,
    val pageFrom: Int,
    val pageTo: Int
)
