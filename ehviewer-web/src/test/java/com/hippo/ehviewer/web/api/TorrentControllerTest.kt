package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.service.TorrentService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class TorrentControllerTest {

    private lateinit var torrentService: TorrentService
    private lateinit var controller: TorrentController

    @BeforeEach
    fun setUp() {
        torrentService = mock(TorrentService::class.java)
        controller = TorrentController(torrentService)
    }

    @Test
    fun `download returns torrent bytes as x-bittorrent attachment`() {
        val bytes = "d8:announce6:urlae".toByteArray()
        `when`(torrentService.fetchTorrentFile("https://ehtracker.org/download/abc.torrent?gid=12345&p=1"))
            .thenReturn(bytes)

        val response = controller.downloadTorrent("https://ehtracker.org/download/abc.torrent?gid=12345&p=1")

        assertEquals(200, response.statusCode.value())
        val body = response.body as ByteArray
        assertArrayEquals(bytes, body)
        assertEquals("application/x-bittorrent", response.headers.getFirst("Content-Type"))
        val disposition = response.headers.getFirst("Content-Disposition")
        assertNotNull(disposition)
        assertTrue(disposition!!.startsWith("attachment"))
        assertTrue(disposition.contains("filename=\"12345.torrent\""))
    }

    @Test
    fun `download falls back to generic filename when gid cannot be derived`() {
        `when`(torrentService.fetchTorrentFile("https://ehtracker.org/download/torrent.torrent"))
            .thenReturn(ByteArray(0))

        val response = controller.downloadTorrent("https://ehtracker.org/download/torrent.torrent")

        assertEquals(200, response.statusCode.value())
        assertTrue(response.headers.getFirst("Content-Disposition")!!.contains("filename=\"torrent.torrent\""))
    }

    @Test
    fun `download returns 404 json when torrent file is unavailable`() {
        `when`(torrentService.fetchTorrentFile("https://ehtracker.org/download/missing.torrent?gid=1"))
            .thenReturn(null)

        val response = controller.downloadTorrent("https://ehtracker.org/download/missing.torrent?gid=1")

        assertEquals(404, response.statusCode.value())
        val body = response.body as TorrentDownloadErrorResponse
        assertFalse(body.success)
        assertTrue(body.message.isNotEmpty())
    }
}
