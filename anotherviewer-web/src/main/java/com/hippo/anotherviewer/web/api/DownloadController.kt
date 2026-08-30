package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.dto.*
import com.hippo.anotherviewer.web.service.DownloadService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/download")
class DownloadController(private val downloadService: DownloadService) {

    @GetMapping("/list")
    fun listDownloads(
        @RequestParam(required = false) label: Int?,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "100") limit: Int,
        @RequestParam(defaultValue = "time_desc") sort: String,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "false") regex: Boolean
    ): ResponseEntity<*> {
        return try {
            ResponseEntity.ok(downloadService.listDownloads(label, offset, limit, sort, q, regex))
        } catch (e: IllegalArgumentException) {
            errorEnvelope(HttpStatus.BAD_REQUEST, "REGEX_INVALID", e.message ?: "正则表达式无效")
        }
    }

    @GetMapping("/info/{id}")
    fun getDownloadInfo(@PathVariable id: Long): ResponseEntity<DownloadItem?> {
        return ResponseEntity.ok(downloadService.getDownloadInfo(id))
    }

    @PostMapping("/add")
    fun addDownload(@Valid @RequestBody request: DownloadAddRequest): ResponseEntity<Boolean> {
        return ResponseEntity.ok(downloadService.addDownload(request))
    }

    @PostMapping("/start/{id}")
    fun startDownload(@PathVariable id: Long): ResponseEntity<Boolean> {
        return ResponseEntity.ok(downloadService.startDownload(id))
    }

    @PostMapping("/start-all")
    fun startAllDownloads(): ResponseEntity<Boolean> {
        downloadService.startAllDownloads()
        return ResponseEntity.ok(true)
    }

    @PostMapping("/restart-all")
    fun restartAllDownloads(): ResponseEntity<Boolean> {
        downloadService.restartAllDownloads()
        return ResponseEntity.ok(true)
    }

    @PostMapping("/start-range")
    fun startRange(@Valid @RequestBody request: DownloadRangeRequest): ResponseEntity<*> {
        if (!request.all && request.ids.isNullOrEmpty()) {
            return errorEnvelope(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ids must not be empty when all=false")
        }
        return try {
            ResponseEntity.ok(downloadService.startDownloads(request.ids, request.all, request.label, request.q, request.regex))
        } catch (e: IllegalArgumentException) {
            errorEnvelope(HttpStatus.BAD_REQUEST, "REGEX_INVALID", e.message ?: "正则表达式无效")
        }
    }

    @PostMapping("/stop-range")
    fun stopRange(@Valid @RequestBody request: DownloadRangeRequest): ResponseEntity<*> {
        if (!request.all && request.ids.isNullOrEmpty()) {
            return errorEnvelope(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ids must not be empty when all=false")
        }
        return try {
            ResponseEntity.ok(downloadService.pauseDownloads(request.ids, request.all, request.label, request.q, request.regex))
        } catch (e: IllegalArgumentException) {
            errorEnvelope(HttpStatus.BAD_REQUEST, "REGEX_INVALID", e.message ?: "正则表达式无效")
        }
    }

    @PostMapping("/delete-range")
    fun deleteRange(@Valid @RequestBody request: DownloadRangeRequest): ResponseEntity<*> {
        if (!request.all && request.ids.isNullOrEmpty()) {
            return errorEnvelope(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ids must not be empty when all=false")
        }
        return try {
            ResponseEntity.ok(downloadService.deleteDownloads(request.ids, request.all, request.label, request.q, request.regex))
        } catch (e: IllegalArgumentException) {
            errorEnvelope(HttpStatus.BAD_REQUEST, "REGEX_INVALID", e.message ?: "正则表达式无效")
        }
    }

    @PostMapping("/move")
    fun move(@Valid @RequestBody request: DownloadMoveRequest): ResponseEntity<*> {
        if (!request.all && request.ids.isNullOrEmpty()) {
            return errorEnvelope(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ids must not be empty when all=false")
        }
        return try {
            ResponseEntity.ok(downloadService.moveDownloads(request.ids, request.all, request.label, request.q, request.regex, request.labelId))
        } catch (e: IllegalArgumentException) {
            errorEnvelope(HttpStatus.BAD_REQUEST, "REGEX_INVALID", e.message ?: "正则表达式无效")
        }
    }

    @PostMapping("/pause/{id}")
    fun pauseDownload(@PathVariable id: Long): ResponseEntity<Boolean> {
        return ResponseEntity.ok(downloadService.pauseDownload(id))
    }

    @PostMapping("/cancel/{id}")
    fun cancelDownload(@PathVariable id: Long): ResponseEntity<Boolean> {
        return ResponseEntity.ok(downloadService.cancelDownload(id))
    }

    @DeleteMapping("/delete/{id}")
    fun deleteDownload(@PathVariable id: Long): ResponseEntity<Boolean> {
        return ResponseEntity.ok(downloadService.deleteDownload(id))
    }

    @PostMapping("/label")
    fun createLabel(@Valid @RequestBody request: DownloadLabelRequest): ResponseEntity<Boolean> {
        return ResponseEntity.ok(downloadService.createLabel(request.label))
    }

    @DeleteMapping("/label/{id}")
    fun deleteLabel(@PathVariable id: Long): ResponseEntity<Boolean> {
        return ResponseEntity.ok(downloadService.deleteLabel(id))
    }

    @GetMapping("/slots")
    fun getFilterSlots(): ResponseEntity<FilterSlotsResponse> {
        return ResponseEntity.ok(FilterSlotsResponse(downloadService.getFilterSlots()))
    }

    /** 整体替换筛选槽位；校验失败 → 400 VALIDATION_ERROR。 */
    @PutMapping("/slots")
    fun putFilterSlots(@Valid @RequestBody request: FilterSlotsResponse): ResponseEntity<*> {
        return try {
            ResponseEntity.ok(FilterSlotsResponse(downloadService.putFilterSlots(request.slots)))
        } catch (e: IllegalArgumentException) {
            errorEnvelope(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", e.message ?: "Invalid filter slots")
        }
    }
}
