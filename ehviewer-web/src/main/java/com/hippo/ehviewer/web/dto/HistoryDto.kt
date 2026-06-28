package com.hippo.ehviewer.web.dto

data class HistoryListResponse(
    val history: List<HistoryItem>
)

data class HistoryItem(
    val gid: Long,
    val token: String,
    val title: String,
    val titleJpn: String,
    val thumb: String,
    val category: String,
    val rating: Float,
    val mode: Int,
    val time: Long
)
