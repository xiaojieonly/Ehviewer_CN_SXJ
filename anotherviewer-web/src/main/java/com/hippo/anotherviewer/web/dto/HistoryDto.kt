package com.hippo.anotherviewer.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class HistoryListResponse(
    val history: List<HistoryItem>,
    val total: Int? = null
)

data class HistoryItem(
    val gid: Long,
    val token: String,
    val title: String,
    val titleJpn: String,
    val thumb: String,
    val category: Int,
    val rating: Float,
    val mode: Int,
    val time: Long
)

data class AddHistoryRequest(
    @field:NotBlank
    @field:Size(max = 64)
    val token: String,
    @field:Size(max = 256)
    val title: String? = null,
    /** 阅读模式, 前端可选; 透传接线在 GalleryService (本次 M-13 不含) */
    val mode: Int = 0
)
