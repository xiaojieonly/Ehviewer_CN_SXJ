package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.service.TorrentService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/torrent")
class TorrentController(private val torrentService: TorrentService) {

    @GetMapping("/list/{gid}")
    fun listTorrents(@PathVariable gid: Long): ResponseEntity<TorrentListResponse> {
        val torrents = torrentService.listTorrents(gid)
        return ResponseEntity.ok(TorrentListResponse(torrents))
    }

    @GetMapping("/download")
    fun downloadTorrent(@RequestParam token: String): ResponseEntity<Boolean> {
        return ResponseEntity.ok(torrentService.downloadTorrent(token))
    }
}
