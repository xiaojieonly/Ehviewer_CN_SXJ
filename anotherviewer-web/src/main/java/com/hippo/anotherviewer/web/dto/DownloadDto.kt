package com.hippo.anotherviewer.web.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class DownloadListResponse(
    val downloads: List<DownloadItem>,
    val labels: List<DownloadLabel>,
    /** 当前 label 条件下分页前的总条数。 */
    val total: Int
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

/**
 * 批量操作请求体（Android 多选模式 Start/Stop/Delete 的 WebUI 对等物）。
 * `all=true` 时忽略 ids，按 (label, q) 过滤条件在服务端解析全集（跨页全选）；
 * label 空/0 → 全部标签，q 空 → 全部条目。
 */
data class DownloadRangeRequest(
    @field:Size(max = 500, message = "at most 500 ids per batch")
    val ids: List<Long>? = null,
    val all: Boolean = false,
    val label: Int? = null,
    @field:Size(max = 256, message = "q must be at most 256 characters")
    val q: String? = null,
)

/** 批量移动标签请求体（Android Move；labelId=0 表示移回默认标签）。 */
data class DownloadMoveRequest(
    @field:Size(max = 500, message = "at most 500 ids per batch")
    val ids: List<Long>? = null,
    val all: Boolean = false,
    val label: Int? = null,
    @field:Size(max = 256, message = "q must be at most 256 characters")
    val q: String? = null,
    @field:Min(0, message = "labelId must be non-negative")
    val labelId: Int = 0
)

data class DownloadProgress(
    val gid: Long,
    val state: Int,
    val downloaded: Int,
    val total: Int,
    val speed: Long,
    val label: Int
)
