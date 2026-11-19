package com.hippo.ehviewer.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class DownloadTorrentFileNameTest {

    @Test
    fun torrentList1TitleDropsColonAndKeepsTorrentSuffix() {
        assertTrue(TORRENT_LIST_1_TITLE.contains(":"))

        val fileName = DownloadTorrentManager.createTorrentFileName(TORRENT_LIST_1_TITLE, FALLBACK_URL)

        assertTrue(fileName.endsWith(".zip.torrent"))
        assertTrue(fileName.contains("番外小樱"))
        assertTrue(fileName.contains("Serpent Sakura"))
        assertFalse(fileName.contains(":"))
        assertNoForbiddenChars(fileName)
        assertTrue(fileName.toByteArray(StandardCharsets.UTF_8).size <= 255)
    }

    @Test
    fun longTitleStaysWithinUtf8Limit() {
        val title = "画".repeat(200) + ":*?\"<>|"
        val fileName = DownloadTorrentManager.createTorrentFileName(title, FALLBACK_URL)

        assertTrue(fileName.endsWith(".torrent"))
        assertNoForbiddenChars(fileName)
        val bytes = fileName.toByteArray(StandardCharsets.UTF_8)
        assertTrue(bytes.size <= 255)
        assertEquals(82 * 3 + ".torrent".length, bytes.size)
    }

    @Test
    fun blankTitleFallsBackToUrlFileName() {
        val expected = "bb3837ff9d57f15caa3cb35a226ac2c8dca2b01a.torrent"
        assertEquals(expected, DownloadTorrentManager.createTorrentFileName(":::", FALLBACK_URL))
        assertEquals(expected, DownloadTorrentManager.createTorrentFileName(" \\/:*?\"<>| ", FALLBACK_URL))
        assertEquals(expected, DownloadTorrentManager.createTorrentFileName(null, FALLBACK_URL))
    }

    private fun assertNoForbiddenChars(fileName: String) {
        for (ch in FORBIDDEN) {
            assertFalse(fileName.contains(ch))
        }
    }

    companion object {
        private const val FALLBACK_URL =
            "https://ehtracker.org/get/4307852/olx4xjefmwq52sfd5z/bb3837ff9d57f15caa3cb35a226ac2c8dca2b01a.torrent"
        private const val TORRENT_LIST_1_TITLE =
            "[DDK00] 蛇之仆·樱 Vol 1-Vol 6合集上 （Vol 1-Vol 3 ）+番外:小樱的洗脑调教  [DDK00] Servant of the Serpent: Sakura – Vol 1–6 Collection (Part 1) (Vol 1 -Vol 3).zip"
        private val FORBIDDEN = charArrayOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
    }
}
