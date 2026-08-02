package com.hippo.anotherviewer.web.dto

data class TorrentListResponse(
    val torrents: List<TorrentItem>
)

data class TorrentItem(
    val gid: Long,
    val token: String,
    val name: String,
    val size: String,
    val addedTime: String
)
