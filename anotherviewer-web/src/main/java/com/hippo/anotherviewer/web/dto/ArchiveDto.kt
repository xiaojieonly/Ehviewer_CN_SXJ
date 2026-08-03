package com.hippo.anotherviewer.web.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

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
    @field:Min(1, message = "gid must be a positive number")
    val gid: Long,
    @field:NotBlank(message = "url is required")
    @field:Size(max = 2048, message = "url must be at most 2048 characters")
    val url: String
)
