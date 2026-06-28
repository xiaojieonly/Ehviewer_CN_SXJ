package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.CommentListResponse
import com.hippo.ehviewer.web.dto.CommentPostRequest
import com.hippo.ehviewer.web.dto.CommentVoteRequest
import com.hippo.ehviewer.web.service.CommentService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/comment")
class CommentController(private val commentService: CommentService) {

    @GetMapping("/list/{gid}")
    fun listComments(@PathVariable gid: Long): ResponseEntity<CommentListResponse> {
        return ResponseEntity.ok(commentService.listComments(gid))
    }

    @PostMapping("/post")
    fun postComment(@RequestBody request: CommentPostRequest): ResponseEntity<Map<String, Boolean>> {
        val result = commentService.postComment(request.gid, "anonymous", request.comment)
        return ResponseEntity.ok(mapOf("success" to result))
    }

    @PostMapping("/vote")
    fun voteComment(@RequestBody request: CommentVoteRequest): ResponseEntity<Map<String, Boolean>> {
        val result = commentService.voteComment(request.gid, request.commentId, request.vote)
        return ResponseEntity.ok(mapOf("success" to result))
    }
}
