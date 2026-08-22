package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.config.ApiErrorEnvelope
import com.hippo.anotherviewer.web.dto.ArchiveDownloadRequest
import com.hippo.anotherviewer.web.service.ArchiveService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class ArchiveControllerTest {

    private lateinit var archiveService: ArchiveService
    private lateinit var controller: ArchiveController

    @BeforeEach
    fun setUp() {
        archiveService = mock(ArchiveService::class.java)
        controller = ArchiveController(archiveService)
    }

    @Test
    fun `download returns success with relative path when archive is saved`() {
        `when`(archiveService.downloadArchive(123L, "https://e-hentai.org/archiver.php?gid=123"))
            .thenReturn(true)

        val response = controller.downloadArchive(ArchiveDownloadRequest(123L, "https://e-hentai.org/archiver.php?gid=123"))

        assertEquals(200, response.statusCode.value())
        val body = response.body as ArchiveDownloadResponse
        assertTrue(body.success)
        assertNull(body.message)
        assertEquals("123/", body.path)
    }

    @Test
    fun `download returns 502 uniform envelope when the archiver flow fails`() {
        `when`(archiveService.downloadArchive(123L, "https://e-hentai.org/archiver.php?gid=123"))
            .thenReturn(false)

        val response = controller.downloadArchive(ArchiveDownloadRequest(123L, "https://e-hentai.org/archiver.php?gid=123"))

        assertEquals(502, response.statusCode.value())
        val body = response.body as ApiErrorEnvelope
        assertEquals(502, body.error.status)
        assertEquals("ARCHIVE_DOWNLOAD_FAILED", body.error.code)
        assertTrue(body.error.message.isNotEmpty())
        assertTrue(body.error.traceId.isNotBlank())
    }

    @Test
    // MASTER-2026-08-22 P1：单飞护栏命中 → 409 CONFLICT 信封。
    fun `download returns 409 uniform envelope when an archive is already in progress`() {
        `when`(archiveService.downloadArchive(123L, "https://e-hentai.org/archiver.php?gid=123"))
            .thenThrow(com.hippo.anotherviewer.web.service.ArchiveInProgressException())

        val response = controller.downloadArchive(ArchiveDownloadRequest(123L, "https://e-hentai.org/archiver.php?gid=123"))

        assertEquals(409, response.statusCode.value())
        val body = response.body as ApiErrorEnvelope
        assertEquals(409, body.error.status)
        assertEquals("CONFLICT", body.error.code)
        assertTrue(body.error.traceId.isNotBlank())
    }

    @Test
    fun `download rejects disallowed hosts with 400 uniform envelope without calling the service`() {
        val response = controller.downloadArchive(ArchiveDownloadRequest(123L, "http://evil.example/archive.zip"))

        assertEquals(400, response.statusCode.value())
        val body = response.body as ApiErrorEnvelope
        assertEquals(400, body.error.status)
        assertEquals("INVALID_ARCHIVE_URL", body.error.code)
        assertTrue(body.error.message.isNotEmpty())
        assertTrue(body.error.traceId.isNotBlank())
        verify(archiveService, never()).downloadArchive(anyLong(), anyString())
    }

    @Test
    fun `download rejects malformed urls with 400 uniform envelope`() {
        val response = controller.downloadArchive(ArchiveDownloadRequest(123L, "not a url"))

        assertEquals(400, response.statusCode.value())
        val body = response.body as ApiErrorEnvelope
        assertEquals("INVALID_ARCHIVE_URL", body.error.code)
        assertTrue(body.error.traceId.isNotBlank())
        verify(archiveService, never()).downloadArchive(anyLong(), anyString())
    }

    @Test
    fun `download rejects gallery-prefixed hosts`() {
        val response = controller.downloadArchive(ArchiveDownloadRequest(123L, "https://e-hentai.org.evil.example/archive.zip"))

        assertEquals(400, response.statusCode.value())
        val body = response.body as ApiErrorEnvelope
        assertEquals("INVALID_ARCHIVE_URL", body.error.code)
        assertTrue(body.error.traceId.isNotBlank())
        verify(archiveService, never()).downloadArchive(anyLong(), anyString())
    }
}
