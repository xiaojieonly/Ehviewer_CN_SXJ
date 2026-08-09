package com.hippo.anotherviewer.web.dto

import jakarta.validation.constraints.NotBlank

/**
 * App 推送下载（push-of-local-downloads）三端点请求/响应体，
 * 字段对齐 contracts/openapi.yaml 的 DownloadUploadInitRequest /
 * DownloadUploadInitResponse / DownloadUploadCompleteRequest。
 */
data class UploadInitRequest(
    @field:NotBlank(message = "token is required")
    val token: String,
    val title: String? = null,
    val titleJpn: String? = null,
    val thumb: String? = null,
    val category: Int = 0,
    val uploader: String? = null,
    val rating: Float = 0f,
    val simpleTags: String? = null,
    val pages: Int = 0,
    val label: Int = 0,
    /** 覆盖同一 gid 的既有下载行；非 force 冲突 → success=false（400）。 */
    val force: Boolean = false,
)

/** PUT /api/v1/download/upload/{gid} 响应；existingPages 供 App 断点续传。 */
data class UploadInitResponse(
    val success: Boolean,
    val message: String = "",
    val existingPages: List<Int> = emptyList(),
)

/** POST /api/v1/download/upload/{gid}/complete 请求体（App 上报总页数与已完成数）。 */
data class UploadCompleteRequest(
    val total: Int,
    val done: Int = 0,
)
