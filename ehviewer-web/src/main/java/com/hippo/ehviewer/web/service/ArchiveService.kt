package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.web.dto.ArchiveItem
import org.springframework.stereotype.Service

@Service
class ArchiveService {

    fun listArchives(gid: Long): List<ArchiveItem> {
        return emptyList()
    }

    fun downloadArchive(gid: Long, url: String): Boolean {
        return false
    }
}
