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
    /** 阅读进度（0 起页索引）；0 表示未读或无进度，卡片角标据此隐藏。 */
    val page: Int = 0,
    val time: Long
)

data class AddHistoryRequest(
    @field:NotBlank
    @field:Size(max = 64)
    val token: String,
    @field:Size(max = 256)
    val title: String? = null,
    /** 阅读模式, 前端可选; R4-4 起经 GalleryService.addToHistory 透传写入 history 行 mode 列。 */
    val mode: Int = 0,
    /**
     * 阅读进度（0 起页索引），前端可选。可空是语义必需：缺省（JSON 不带该字段，
     * Jackson 落 null）= 不改写已存进度；显式传 0 = 重读写 0。两条路径靠判空
     * 区分（非判 0），故此字段不能写成非空 `Int = 0`。
     */
    val page: Int? = null
)
