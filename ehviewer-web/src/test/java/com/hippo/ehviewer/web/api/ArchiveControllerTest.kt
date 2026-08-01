package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.ArchiveDownloadRequest
import com.hippo.ehviewer.web.service.ArchiveService
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
        val body = response.body!!
        assertTrue(body.success)
        assertNull(body.message)
        assertEquals("123/", body.path)
    }

    @Test
    fun `download returns 502 json when the archiver flow fails`() {
        `when`(archiveService.downloadArchive(123L, "https://e-hentai.org/archiver.php?gid=123"))
            .thenReturn(false)

        val response = controller.downloadArchive(ArchiveDownloadRequest(123L, "https://e-hentai.org/archiver.php?gid=123"))

        assertEquals(502, response.statusCode.value())
        val body = response.body!!
        assertFalse(body.success)
        assertTrue(!body.message.isNullOrEmpty())
        assertNull(body.path)
    }

    @Test
    fun `download rejects disallowed hosts with 400 without calling the service`() {
        val response = controller.downloadArchive(ArchiveDownloadRequest(123L, "http://evil.example/archive.zip"))

        assertEquals(400, response.statusCode.value())
        val body = response.body!!
        assertFalse(body.success)
        assertTrue(!body.message.isNullOrEmpty())
        verify(archiveService, never()).downloadArchive(anyLong(), anyString())
    }

    @Test
    fun `download rejects malformed urls with 400`() {
        val response = controller.downloadArchive(ArchiveDownloadRequest(123L, "not a url"))

        assertEquals(400, response.statusCode.value())
        assertFalse(response.body!!.success)
        assertTrue(!response.body!!.message.isNullOrEmpty())
        verify(archiveService, never()).downloadArchive(anyLong(), anyString())
    }

    @Test
    fun `download rejects exhentai-prefixed hosts`() {
        val response = controller.downloadArchive(ArchiveDownloadRequest(123L, "https://e-hentai.org.evil.example/archive.zip"))

        assertEquals(400, response.statusCode.value())
        assertFalse(response.body!!.success)
        assertTrue(!response.body!!.message.isNullOrEmpty())
        verify(archiveService, never()).downloadArchive(anyLong(), anyString())
    }
}
