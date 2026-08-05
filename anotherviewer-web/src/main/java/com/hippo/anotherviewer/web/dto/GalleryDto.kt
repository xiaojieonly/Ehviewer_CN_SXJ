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

data class TopListResponse(
    val success: Boolean,
    val data: List<TopListFeedItemDto>,
    val total: Int
)

/**
 * One EH top-list row (contracts/openapi.yaml TopListFeedItemDto). Fields
 * mirror the core [com.hippo.anotherviewer.client.data.topList.TopListItem];
 * the site parser only fills `value`/`href`, so gid/token/tag are typically
 * null.
 */
data class TopListFeedItemDto(
    val gid: String?,
    val token: String?,
    val tag: String?,
    val value: String?,
    val href: String?
)

/**
 * Frozen 400 body for /api/v1/gallery/feed invalid mode
 * (contracts/openapi.yaml FeedErrorResponse): `{success:false, message}`.
 */
data class FeedErrorResponse(
    val success: Boolean,
    val message: String
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
    // Full AdvanceSearchTable bitmask (SNAME..SFT = 0x1..0x400, W3 R4-10:
    // the high bits are backend-supported, so presets may carry them).
    @field:Min(0, message = "advanceSearch must be within the AdvanceSearchTable bitmask (0..2047)")
    @field:Max(2047, message = "advanceSearch must be within the AdvanceSearchTable bitmask (0..2047)")
    val advanceSearch: Int,
    @field:Min(0, message = "minRating must be between 0 and 5")
    @field:Max(5, message = "minRating must be between 0 and 5")
    val minRating: Int,
    @field:Min(0, message = "pageFrom must be non-negative")
    val pageFrom: Int,
    @field:Min(0, message = "pageTo must be non-negative")
    val pageTo: Int,
    // W3 R4-11 (contracts/openapi.yaml QuickSearchDto.sort): the sort order
    // persisted with the preset; same semantics as GET /gallery/search `sort`.
    @field:Min(0, message = "sort must be between 0 and 3")
    @field:Max(3, message = "sort must be between 0 and 3")
    val sort: Int = 0
)
