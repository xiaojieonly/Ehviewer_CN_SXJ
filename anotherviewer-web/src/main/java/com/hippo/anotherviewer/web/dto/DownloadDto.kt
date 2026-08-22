package com.hippo.anotherviewer.web.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
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
 * `all=true` 时忽略 ids，按 (label, q[, regex]) 过滤条件在服务端解析全集
 * （跨页全选）；label 空/0 → 全部标签，q 空 → 全部条目；regex=true 时 q
 * 按正则解释（服务端内存匹配）。
 */
data class DownloadRangeRequest(
    @field:Size(max = 500, message = "at most 500 ids per batch")
    val ids: List<Long>? = null,
    val all: Boolean = false,
    val label: Int? = null,
    @field:Size(max = 256, message = "q must be at most 256 characters")
    val q: String? = null,
    val regex: Boolean = false,
)

/** 批量移动标签请求体（Android Move；labelId=0 表示移回默认标签）。 */
data class DownloadMoveRequest(
    @field:Size(max = 500, message = "at most 500 ids per batch")
    val ids: List<Long>? = null,
    val all: Boolean = false,
    val label: Int? = null,
    @field:Size(max = 256, message = "q must be at most 256 characters")
    val q: String? = null,
    val regex: Boolean = false,
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

/**
 * 筛选槽位（命名正则预设）：id 由前端生成（如 nanoid/时间戳），服务端仅透传；
 * 整体存 serverConfig KV `download.filterSlots`（JSON 数组），随备份自动导出。
 */
data class FilterSlotDto(
    val id: String,
    val name: String,
    val pattern: String,
)

/** 筛选槽位集合：GET /api/v1/download/slots 响应与 PUT 请求体共用。 */
data class FilterSlotsResponse(
    val slots: List<FilterSlotDto> = emptyList()
)

// ── 下载维护（W2-DL F2）：冗余文件 / 无效下载，dry-run 预览 + 执行两段式 ──

/** 冗余文件条目：downloads 根目录下无任何下载行引用的条目（path 相对根目录）。 */
data class MaintenanceFileIssue(
    val path: String,
    val sizeBytes: Long,
)

/** 无效下载条目：行存在但本地内容缺失/损坏（判定语义见 DownloadMaintenanceService）。 */
data class MaintenanceDownloadIssue(
    val id: Long,
    val gid: Long,
    val title: String?,
    /** 机器可读原因：content_dir_missing | no_usable_page_files。 */
    val reason: String,
)

/** GET /api/v1/download/maintenance/preview 响应：两段式的第一段（只读扫描）。 */
data class MaintenancePreviewResponse(
    val redundantFiles: List<MaintenanceFileIssue>,
    val invalidDownloads: List<MaintenanceDownloadIssue>,
)

/** 本次要清理的类别（两按钮一一对应）。 */
enum class MaintenanceKind {
    REDUNDANT_FILES,
    INVALID_DOWNLOADS
}

/** POST /api/v1/download/maintenance/clean 请求体。 */
data class MaintenanceCleanRequest(
    @field:NotNull(message = "kind must not be null")
    val kind: MaintenanceKind? = null
)

/** POST /api/v1/download/maintenance/clean 响应：实际删除结果（执行前重新扫描）。 */
data class MaintenanceCleanResponse(
    val kind: MaintenanceKind,
    /** 删除的冗余条目个数（REDUNDANT_FILES 分支计数）。 */
    val removedFiles: Int,
    /** 删除的无效下载行数（INVALID_DOWNLOADS 分支计数）。 */
    val removedDownloads: Int,
    /** 释放的磁盘字节数（删除前统计；含无效下载分支清掉的残留目录）。 */
    val freedBytes: Long
)
