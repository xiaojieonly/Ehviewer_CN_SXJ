package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.dto.HistoryListResponse
import com.hippo.anotherviewer.web.service.HistoryService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/history")
class HistoryController(private val historyService: HistoryService) {

    @GetMapping("/list")
    fun listHistory(
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) pageSize: Int?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "false") regex: Boolean
    ): ResponseEntity<*> {
        return try {
            ResponseEntity.ok(historyService.listHistory(page, pageSize, q, regex))
        } catch (e: IllegalArgumentException) {
            errorEnvelope(HttpStatus.BAD_REQUEST, "REGEX_INVALID", e.message ?: "正则表达式无效")
        }
    }

    @DeleteMapping("/clear")
    fun clearHistory(): ResponseEntity<Map<String, Boolean>> {
        historyService.clearHistory()
        return ResponseEntity.ok(mapOf("success" to true))
    }
}
