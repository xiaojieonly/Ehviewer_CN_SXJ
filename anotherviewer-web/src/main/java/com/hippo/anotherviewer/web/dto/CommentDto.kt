package com.hippo.anotherviewer.web.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CommentListResponse(
    val comments: List<CommentItem>
)

data class CommentPostRequest(
    @field:Min(1, message = "gid must be a positive number")
    val gid: Long,
    @field:NotBlank(message = "comment is required")
    @field:Size(max = 1000, message = "comment must be at most 1000 characters")
    val comment: String
)

data class CommentVoteRequest(
    @field:Min(1, message = "gid must be a positive number")
    val gid: Long,
    @field:Min(1, message = "commentId must be a positive number")
    val commentId: Long,
    @field:Min(-1, message = "vote must be -1, 0 or 1")
    @field:Max(1, message = "vote must be -1, 0 or 1")
    val vote: Int
)

data class CommentItem(
    val id: Long,
    val uploader: String,
    val comment: String,
    val time: String,
    val score: Int
)
