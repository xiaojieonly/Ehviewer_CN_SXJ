package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.web.dto.CommentItem
import com.hippo.ehviewer.web.dto.CommentListResponse
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Service
class CommentService {

    private val commentIdGenerator = AtomicLong(1)
    private val comments = ConcurrentHashMap<Long, MutableList<CommentItem>>()

    fun listComments(gid: Long): CommentListResponse {
        val commentList = comments[gid] ?: emptyList()
        return CommentListResponse(commentList)
    }

    fun postComment(gid: Long, uploader: String, comment: String): Boolean {
        val commentList = comments.getOrPut(gid) { mutableListOf() }
        val item = CommentItem(
            id = commentIdGenerator.getAndIncrement(),
            uploader = uploader,
            comment = comment,
            time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date()),
            score = 0
        )
        commentList.add(item)
        return true
    }

    fun voteComment(gid: Long, commentId: Long, vote: Int): Boolean {
        val commentList = comments[gid] ?: return false
        val index = commentList.indexOfFirst { it.id == commentId }
        if (index < 0) return false
        val old = commentList[index]
        commentList[index] = old.copy(score = old.score + vote)
        return true
    }
}
