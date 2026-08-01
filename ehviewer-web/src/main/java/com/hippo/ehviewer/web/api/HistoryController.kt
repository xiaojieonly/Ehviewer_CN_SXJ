package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.HistoryListResponse
import com.hippo.ehviewer.web.service.HistoryService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/history")
class HistoryController(private val historyService: HistoryService) {

    @GetMapping("/list")
    fun listHistory(
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) pageSize: Int?
    ): ResponseEntity<HistoryListResponse> {
        return ResponseEntity.ok(historyService.listHistory(page, pageSize))
    }

    @DeleteMapping("/clear")
    fun clearHistory(): ResponseEntity<Map<String, Boolean>> {
        historyService.clearHistory()
        return ResponseEntity.ok(mapOf("success" to true))
    }
}
