package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.web.dto.TorrentItem
import org.springframework.stereotype.Service

@Service
class TorrentService {

    fun listTorrents(gid: Long): List<TorrentItem> {
        return emptyList()
    }

    fun downloadTorrent(token: String): Boolean {
        return false
    }
}
