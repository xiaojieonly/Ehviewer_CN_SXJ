package com.hippo.anotherviewer.web.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class GalleryListResponse(
    val success: Boolean,
    val data: List<GalleryItemDto>,
    val total: Int
)

data class GalleryItemDto(
    val gid: Long,
    val token: String,
    val galleryUrl: String,
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
    val galleryUrl: String,
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

data class QuickSearchListResponse(
    val success: Boolean,
    val data: List<QuickSearchDto>
)

data class QuickSearchDto(
    val id: Long,
    @field:NotBlank(message = "name is required")
    @field:Size(max = 64, message = "name must be at most 64 characters")
    val name: String,
    val mode: Int,
    val category: Int,
    @field:Size(max = 512, message = "keyword must be at most 512 characters")
    val keyword: String?,
    @field:Min(0, message = "advanceSearch must be 0 or 1")
    @field:Max(1, message = "advanceSearch must be 0 or 1")
    val advanceSearch: Int,
    @field:Min(0, message = "minRating must be between 0 and 5")
    @field:Max(5, message = "minRating must be between 0 and 5")
    val minRating: Int,
    @field:Min(0, message = "pageFrom must be non-negative")
    val pageFrom: Int,
    @field:Min(0, message = "pageTo must be non-negative")
    val pageTo: Int
)
