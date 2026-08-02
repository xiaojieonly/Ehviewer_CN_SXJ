package com.hippo.anotherviewer.web.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Bounds tests for the in-memory comment store:
 *
 * 1. The total number of comments is capped at [CommentService.MAX_COMMENTS].
 * 2. Eviction prefers the least recently used gid and drops oldest-first.
 * 3. Voting still works after eviction logic is in place.
 */
class CommentServiceTest {

    @Test
    fun `single gid is capped at the limit and keeps the newest comments`() {
        val service = CommentService()
        repeat(CommentService.MAX_COMMENTS + 100) { i ->
            service.postComment(1L, "u$i", "c$i")
        }

        val list = service.listComments(1L)
        assertEquals(CommentService.MAX_COMMENTS, list.comments.size)
        // The 100 oldest comments were evicted, oldest-first.
        assertEquals("c100", list.comments.first().comment)
        assertEquals("c${CommentService.MAX_COMMENTS + 99}", list.comments.last().comment)
    }

    @Test
    fun `over capacity, the least recently used gid is evicted first`() {
        val service = CommentService()
        // Fill gid 1 to the cap, then post to gid 2: gid 1 is now LRU.
        repeat(CommentService.MAX_COMMENTS) { i ->
            service.postComment(1L, "u", "old-$i")
        }
        service.postComment(2L, "u", "new")

        val gid1 = service.listComments(1L)
        assertEquals(CommentService.MAX_COMMENTS - 1, gid1.comments.size)
        assertEquals("old-1", gid1.comments.first().comment)
        assertEquals(1, service.listComments(2L).comments.size)
    }

    @Test
    fun `empty gid buckets are removed when fully evicted`() {
        val service = CommentService()
        service.postComment(1L, "u", "solo")
        repeat(CommentService.MAX_COMMENTS) { i ->
            service.postComment(2L, "u", "bulk-$i")
        }
        // Posting to gid 3 evicts gid 1's single comment; the bucket goes away.
        service.postComment(3L, "u", "third")

        assertEquals(0, service.listComments(1L).comments.size)
        assertEquals(CommentService.MAX_COMMENTS - 1, service.listComments(2L).comments.size)
        assertEquals(1, service.listComments(3L).comments.size)
    }

    @Test
    fun `voting still increments the score under the cap`() {
        val service = CommentService()
        service.postComment(1L, "u", "hello")
        val commentId = service.listComments(1L).comments.single().id

        assertTrue(service.voteComment(1L, commentId, 1))
        assertEquals(1, service.listComments(1L).comments.single().score)
        assertTrue(service.voteComment(1L, commentId, 2))
        assertEquals(3, service.listComments(1L).comments.single().score)
        // Voting on a missing gid/comment is refused.
        assertFalse(service.voteComment(999L, commentId, 1))
        assertFalse(service.voteComment(1L, commentId + 1, 1))
    }
}
