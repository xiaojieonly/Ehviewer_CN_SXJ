package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.service.CommentService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class CommentControllerTest {

    private lateinit var commentService: CommentService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        commentService = mock(CommentService::class.java)
        mockMvc = MockMvcBuilders.standaloneSetup(CommentController(commentService))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    private fun principal(name: String): RequestPostProcessor = RequestPostProcessor { request ->
        request.userPrincipal = UsernamePasswordAuthenticationToken(
            name, null, listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
        request
    }

    @Test
    fun `post forwards gid comment and authenticated uploader`() {
        `when`(commentService.postComment(123L, "alice", "nice")).thenReturn(true)

        mockMvc.perform(
            post("/api/v1/comment/post")
                .with(principal("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gid":123,"comment":"nice"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
        verify(commentService).postComment(123L, "alice", "nice")
    }

    @Test
    fun `post rejects blank comment with 400 envelope`() {
        mockMvc.perform(
            post("/api/v1/comment/post")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gid":123,"comment":"  "}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.status").value(400))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("comment is required"))
            .andExpect(jsonPath("$.error.traceId").exists())
        verify(commentService, never()).postComment(anyLong(), any(), any())
    }

    @Test
    fun `post rejects overlong comment with 400 envelope`() {
        mockMvc.perform(
            post("/api/v1/comment/post")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gid":123,"comment":"${"x".repeat(1001)}"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        verify(commentService, never()).postComment(anyLong(), any(), any())
    }

    @Test
    fun `vote rejects out-of-range vote with 400 envelope`() {
        mockMvc.perform(
            post("/api/v1/comment/vote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gid":123,"commentId":5,"vote":7}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        verify(commentService, never()).voteComment(anyLong(), anyLong(), anyInt())
    }

    @Test
    fun `vote accepts a valid vote`() {
        `when`(commentService.voteComment(123L, 5L, 1)).thenReturn(true)

        mockMvc.perform(
            post("/api/v1/comment/vote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gid":123,"commentId":5,"vote":1}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
        verify(commentService).voteComment(123L, 5L, 1)
    }
}
