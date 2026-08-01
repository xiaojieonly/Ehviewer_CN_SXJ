package com.hippo.ehviewer.web.dto

data class DownloadListResponse(
    val downloads: List<DownloadItem>,
    val labels: List<DownloadLabel>
)

data class DownloadItem(
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
    // Always null in API responses — the server's absolute download path is
    // never exposed to clients. Retained only for JSON shape stability.
    val downloadDir: String? = null,
    // Exposes DownloadInfoEntity.error (human-readable failure/cancel reason).
    // Defaults to null so serialization is unchanged until DownloadService
    // maps the column into the DTO.
    val error: String? = null
)

data class DownloadLabel(
    val id: Long,
    val label: String,
    val time: Long
)

data class DownloadAddRequest(
    val gid: Long,
    val token: String,
    val title: String?,
    val thumb: String?,
    val label: Int = 0
)

data class DownloadLabelRequest(
    val label: String
)

data class DownloadProgress(
    val gid: Long,
    val state: Int,
    val downloaded: Int,
    val total: Int,
    val speed: Long,
    val label: Int
)
