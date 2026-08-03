package com.hippo.anotherviewer.web.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

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
    @field:Min(1, message = "gid must be a positive number")
    val gid: Long,
    @field:NotBlank(message = "token is required")
    @field:Size(max = 64, message = "token must be at most 64 characters")
    val token: String,
    @field:Size(max = 256, message = "title must be at most 256 characters")
    val title: String?,
    @field:Size(max = 512, message = "thumb must be at most 512 characters")
    val thumb: String?,
    @field:Min(0, message = "label must be non-negative")
    val label: Int = 0
)

data class DownloadLabelRequest(
    @field:NotBlank(message = "label is required")
    @field:Size(max = 64, message = "label must be at most 64 characters")
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
