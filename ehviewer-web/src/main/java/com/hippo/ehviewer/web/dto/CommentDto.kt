package com.hippo.ehviewer.web.dto

data class CommentListResponse(
    val comments: List<CommentItem>
)

data class CommentPostRequest(
    val gid: Long,
    val comment: String
)

data class CommentVoteRequest(
    val gid: Long,
    val commentId: Long,
    val vote: Int
)

data class CommentItem(
    val id: Long,
    val uploader: String,
    val comment: String,
    val time: String,
    val score: Int
)
