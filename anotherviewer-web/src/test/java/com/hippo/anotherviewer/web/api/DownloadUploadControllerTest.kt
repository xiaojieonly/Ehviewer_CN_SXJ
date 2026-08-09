package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.argThatK
import com.hippo.anotherviewer.web.eq
import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.dto.UploadInitResponse
import com.hippo.anotherviewer.web.service.DownloadUploadService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * 契约测试 for [DownloadUploadController]（App 推送下载三端点）。
 * standalone MockMvc + mock [DownloadUploadService]（服务端行为细节见
 * DownloadUploadServiceTest）。
 */
class DownloadUploadControllerTest {

    private lateinit var uploadService: DownloadUploadService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        uploadService = mock(DownloadUploadService::class.java)
        mockMvc = MockMvcBuilders.standaloneSetup(DownloadUploadController(uploadService))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    private fun principal(name: String): RequestPostProcessor = RequestPostProcessor { request ->
        request.userPrincipal = UsernamePasswordAuthenticationToken(
            name, null, listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
        request
    }

    private val pageBytes: ByteArray get() = ByteArray(16) { 1 }

    private fun pageFile(ext: String = "jpg"): MockMultipartFile =
        MockMultipartFile("file", "page.$ext", "image/jpeg", pageBytes)

    // ── PUT /{gid} 元数据 ────────────────────────────────────────

    @Test
    fun `initUpload accepts metadata and returns existing pages`() {
        `when`(uploadService.initUpload(eq(123L), any(), eq("alice")))
            .thenReturn(UploadInitResponse(success = true, message = "ok", existingPages = listOf(1, 2)))

        mockMvc.perform(
            put("/api/v1/download/upload/123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"tok123","title":"T","pages":20}""")
                .with(principal("alice"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("ok"))
            .andExpect(jsonPath("$.existingPages[0]").value(1))
            .andExpect(jsonPath("$.existingPages[1]").value(2))
    }

    @Test
    fun `initUpload conflict returns 400 with the InitResponse body`() {
        // 契约：非 force 且 gid 已存在 → 400 + InitResponse(success=false)，供 App 跳过该本。
        `when`(uploadService.initUpload(eq(123L), any(), eq("alice")))
            .thenReturn(UploadInitResponse(
                success = false,
                message = "gid=123 已存在下载行；如需覆盖请用 force=true",
                existingPages = listOf(3)
            ))

        mockMvc.perform(
            put("/api/v1/download/upload/123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"tok123"}""")
                .with(principal("alice"))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("gid=123 已存在下载行；如需覆盖请用 force=true"))
            .andExpect(jsonPath("$.existingPages[0]").value(3))
    }

    @Test
    fun `initUpload rejects blank token with 400 envelope`() {
        mockMvc.perform(
            put("/api/v1/download/upload/123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":""}""")
                .with(principal("alice"))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.status").value(400))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("token is required"))
        verify(uploadService, never()).initUpload(anyLong(), any(), any())
    }

    // ── POST /{gid}/page/{page} multipart ────────────────────────

    @Test
    fun `uploadPage stores the multipart file and returns true`() {
        mockMvc.perform(
            multipart("/api/v1/download/upload/123/page/7").file(pageFile()).with(principal("alice"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").value(true))
        verify(uploadService).storePage(
            eq(123L), eq(7), eq("page.jpg"),
            argThatK<ByteArray> { it.contentEquals(pageBytes) }
        )
    }

    @Test
    fun `uploadPage rejects an unsupported extension with 400 envelope`() {
        doThrow(IllegalArgumentException("unsupported image extension: page.txt"))
            .`when`(uploadService).storePage(eq(123L), eq(7), eq("page.txt"), any())

        mockMvc.perform(
            multipart("/api/v1/download/upload/123/page/7").file(pageFile("txt")).with(principal("alice"))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.status").value(400))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("unsupported image extension: page.txt"))
    }

    // ── POST /{gid}/complete ─────────────────────────────────────

    @Test
    fun `complete finalizes the download`() {
        `when`(uploadService.completeUpload(eq(123L), any())).thenReturn(true)

        mockMvc.perform(
            post("/api/v1/download/upload/123/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"total":20,"done":20}""")
                .with(principal("alice"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.error.code").value("OK"))
            .andExpect(jsonPath("$.error.status").value(200))
    }

    @Test
    fun `complete returns 404 envelope when no upload row exists`() {
        `when`(uploadService.completeUpload(eq(123L), any())).thenReturn(false)

        mockMvc.perform(
            post("/api/v1/download/upload/123/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"total":20}""")
                .with(principal("alice"))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.error.message").value("No upload row for gid=123"))
    }
}
