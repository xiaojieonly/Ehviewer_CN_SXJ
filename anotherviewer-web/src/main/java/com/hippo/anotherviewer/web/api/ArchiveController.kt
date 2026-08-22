package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.dto.*
import com.hippo.anotherviewer.web.service.ArchiveService
import jakarta.validation.Valid
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Result of a successful archive download. [path] is a relative hint only
 * (`<gid>/`) — absolute server paths are never exposed to clients.
 * Failures use the uniform error envelope (M-6), not this class.
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
     * 400 uniform envelope when the archive URL host is not allowed
     * (SSRF guard, mirrors ArchiveService);
     * 502 uniform envelope when the archiver flow failed (credits
     * required, upstream error).
     */
    @PostMapping("/download")
    fun downloadArchive(@Valid @RequestBody request: ArchiveDownloadRequest): ResponseEntity<*> {
        val parsed = request.url.toHttpUrlOrNull()
        if (parsed == null || !isAllowedArchiveHost(parsed.host)) {
            return errorEnvelope(HttpStatus.BAD_REQUEST, "INVALID_ARCHIVE_URL", "Archive URL host not allowed")
        }
        val ok = try {
            archiveService.downloadArchive(request.gid, request.url)
        } catch (e: com.hippo.anotherviewer.web.service.ArchiveInProgressException) {
            // MASTER-2026-08-22 P1：单飞护栏命中 → 409（与 ProcessingController
            // 的 in-progress 409 CONFLICT 同语义）。
            return errorEnvelope(HttpStatus.CONFLICT, "CONFLICT", e.message ?: "Archive download already in progress")
        }
        return if (ok) {
            ResponseEntity.ok(
                ArchiveDownloadResponse(success = true, path = "${request.gid}/")
            )
        } else {
            errorEnvelope(
                HttpStatus.BAD_GATEWAY,
                "ARCHIVE_DOWNLOAD_FAILED",
                "Archive download failed (credits required or upstream error)"
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
