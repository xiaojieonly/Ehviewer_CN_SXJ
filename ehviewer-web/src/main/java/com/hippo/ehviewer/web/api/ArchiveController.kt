package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.service.ArchiveService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/archive")
class ArchiveController(private val archiveService: ArchiveService) {

    @GetMapping("/list/{gid}")
    fun listArchives(@PathVariable gid: Long): ResponseEntity<ArchiveListResponse> {
        val archives = archiveService.listArchives(gid)
        return ResponseEntity.ok(ArchiveListResponse(archives))
    }

    @PostMapping("/download")
    fun downloadArchive(@RequestBody request: ArchiveDownloadRequest): ResponseEntity<Boolean> {
        return ResponseEntity.ok(archiveService.downloadArchive(request.gid, request.url))
    }
}
