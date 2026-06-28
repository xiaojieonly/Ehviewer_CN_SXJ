package com.hippo.ehviewer.web.dto

data class ArchiveListResponse(
    val archives: List<ArchiveItem>
)

data class ArchiveItem(
    val gid: Long,
    val url: String,
    val name: String,
    val size: String,
    val price: String,
    val credit: String
)

data class ArchiveDownloadRequest(
    val gid: Long,
    val url: String
)
