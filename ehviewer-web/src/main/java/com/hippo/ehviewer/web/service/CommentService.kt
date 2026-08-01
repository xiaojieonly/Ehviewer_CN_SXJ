package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.web.dto.CommentItem
import com.hippo.ehviewer.web.dto.CommentListResponse
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Service
class CommentService {

    companion object {
        // Comments are in-memory only (lost on restart by design); the cap
        // bounds the map while the server runs. When exceeded, the oldest
        // comments (by gid recency, then insertion order) are evicted.
        const val MAX_COMMENTS = 500
    }

    private val commentIdGenerator = AtomicLong(1)
    private val comments = ConcurrentHashMap<Long, MutableList<CommentItem>>()
    // gid -> monotonic recency sequence used to pick the eviction victim.
    private val gidRecency = ConcurrentHashMap<Long, Long>()
    private val recencyGenerator = AtomicLong()
    private val commentCount = AtomicLong(0)
    private val lock = Any()

    fun listComments(gid: Long): CommentListResponse {
        val commentList = comments[gid] ?: emptyList()
        return CommentListResponse(commentList)
    }

    fun postComment(gid: Long, uploader: String, comment: String): Boolean {
        val item = CommentItem(
            id = commentIdGenerator.getAndIncrement(),
            uploader = uploader,
            comment = comment,
            time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date()),
            score = 0
        )
        synchronized(lock) {
            val commentList = comments.getOrPut(gid) { mutableListOf() }
            commentList.add(item)
            commentCount.incrementAndGet()
            gidRecency[gid] = recencyGenerator.getAndIncrement()
            evictOverflow()
        }
        return true
    }

    fun voteComment(gid: Long, commentId: Long, vote: Int): Boolean {
        synchronized(lock) {
            val commentList = comments[gid] ?: return false
            val index = commentList.indexOfFirst { it.id == commentId }
            if (index < 0) return false
            val old = commentList[index]
            commentList[index] = old.copy(score = old.score + vote)
            // Keep score-only activity from making a gid look stale; it still
            // counts as recent use for eviction purposes.
            gidRecency[gid] = recencyGenerator.getAndIncrement()
        }
        return true
    }

    /**
     * Drops the oldest comment (from the least recently used gid) until the
     * store fits within [MAX_COMMENTS]. Empty gid buckets are removed so the
     * map only ever holds live data.
     */
    private fun evictOverflow() {
        while (commentCount.get() > MAX_COMMENTS) {
            val oldestGid = gidRecency.minByOrNull { it.value }?.key ?: break
            val commentList = comments[oldestGid] ?: break
            commentList.removeAt(0)
            commentCount.decrementAndGet()
            if (commentList.isEmpty()) {
                comments.remove(oldestGid)
                gidRecency.remove(oldestGid)
            }
        }
    }
}
