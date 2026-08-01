package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.service.TorrentService
import okhttp3.HttpUrl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class TorrentDownloadErrorResponse(
    val success: Boolean,
    val message: String
)

@RestController
@RequestMapping("/api/v1/torrent")
class TorrentController(private val torrentService: TorrentService) {

    @GetMapping("/list/{gid}")
    fun listTorrents(@PathVariable gid: Long): ResponseEntity<TorrentListResponse> {
        val torrents = torrentService.listTorrents(gid)
        return ResponseEntity.ok(TorrentListResponse(torrents))
    }

    /**
     * Streams the `.torrent` file bytes for a torrent URL/token as an
     * `application/x-bittorrent` attachment (Content-Disposition
     * `filename=<gid>.torrent`). Returns 404 JSON when the file is
     * unavailable (host not allowed, fetch failed, no file).
     */
    @GetMapping("/download")
    fun downloadTorrent(@RequestParam token: String): ResponseEntity<*> {
        val bytes = torrentService.fetchTorrentFile(token)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                TorrentDownloadErrorResponse(success = false, message = "Torrent file unavailable")
            )
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/x-bittorrent"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${torrentFileName(token)}\"")
            .body(bytes)
    }

    /**
     * Derives a download filename from the torrent URL: prefers the `gid`
     * query parameter, falls back to the first all-digit path segment.
     */
    private fun torrentFileName(token: String): String {
        val url = HttpUrl.parse(token)
        val gid = url?.queryParameter("gid")?.trim()
            ?: url?.pathSegments()?.firstOrNull { it.isNotEmpty() && it.all(Char::isDigit) }
        return if (gid != null && gid.isNotEmpty()) "$gid.torrent" else "torrent.torrent"
    }
}
