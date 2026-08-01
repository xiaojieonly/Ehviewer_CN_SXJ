package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.service.ArchiveService
import okhttp3.HttpUrl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Result of an archive download attempt. [path] is a relative hint only
 * (`<gid>/`) — absolute server paths are never exposed to clients.
 */
data class ArchiveDownloadResponse(
    val success: Boolean,
    val message: String? = null,
    val path: String? = null
)

@RestController
@RequestMapping("/api/v1/archive")
class ArchiveController(private val archiveService: ArchiveService) {

    @GetMapping("/list/{gid}")
    fun listArchives(@PathVariable gid: Long): ResponseEntity<ArchiveListResponse> {
        val archives = archiveService.listArchives(gid)
        return ResponseEntity.ok(ArchiveListResponse(archives))
    }

    /**
     * 200 `{success:true, path:"<gid>/"}` on success;
     * 400 `{success:false, message}` when the archive URL host is not allowed
     * (SSRF guard, mirrors ArchiveService);
     * 502 `{success:false, message}` when the archiver flow failed (credits
     * required, upstream error).
     */
    @PostMapping("/download")
    fun downloadArchive(@RequestBody request: ArchiveDownloadRequest): ResponseEntity<ArchiveDownloadResponse> {
        val parsed = HttpUrl.parse(request.url)
        if (parsed == null || !isAllowedArchiveHost(parsed.host())) {
            return ResponseEntity.badRequest().body(
                ArchiveDownloadResponse(success = false, message = "Archive URL host not allowed")
            )
        }
        val ok = archiveService.downloadArchive(request.gid, request.url)
        return if (ok) {
            ResponseEntity.ok(
                ArchiveDownloadResponse(success = true, path = "${request.gid}/")
            )
        } else {
            ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                ArchiveDownloadResponse(success = false, message = "Archive download failed (credits required or upstream error)")
            )
        }
    }

    /**
     * Mirrors ArchiveService.isAllowedArchiveHost so disallowed hosts get a
     * distinct 400 response. ArchiveService remains the authoritative guard.
     */
    private fun isAllowedArchiveHost(host: String): Boolean {
        val normalized = host.lowercase().removePrefix("www.")
        return normalized == "e-hentai.org" || normalized == "exhentai.org"
    }
}
