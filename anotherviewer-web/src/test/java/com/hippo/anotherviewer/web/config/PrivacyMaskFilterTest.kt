package com.hippo.anotherviewer.web.config

import com.hippo.anotherviewer.web.dto.MaintenanceFileIssue
import com.hippo.anotherviewer.web.dto.MaintenancePreviewResponse
import com.hippo.anotherviewer.web.api.MaintenanceController
import com.hippo.anotherviewer.web.service.DownloadMaintenanceService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder

class PrivacyMaskFilterTest {

    private val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()

    // ── redact（JSON 树递归脱敏） ────────────────────────────────

    @Test
    fun `gallery content objects are scrubbed via gid anchor`() {
        val json = """
            {"success":true,"data":[
              {"gid":42,"token":"tok","title":"Real Title","titleJpn":"Jpn",
               "uploader":"anon","simpleTags":["a","b"],"tags":[],
               "galleryUrl":"https://e-hentai.org/g/42/tok/","pages":10}
            ]}
        """.trimIndent()
        val root = objectMapper.readTree(json)

        assertTrue(PrivacyMaskFilter.redact(root))

        val item = root["data"][0]
        assertEquals("#42", item["title"].asText())
        assertEquals("", item["titleJpn"].asText())
        assertEquals("", item["uploader"].asText())
        assertTrue(item["simpleTags"].isEmpty)
        assertTrue(item["tags"].isEmpty)
        assertEquals("", item["galleryUrl"].asText())
        // 无关字段保留
        assertEquals("tok", item["token"].asText())
        assertEquals(10, item["pages"].asInt())
    }

    @Test
    fun `comment objects are blanked without gid anchor`() {
        val json = """
            {"comments":[
              {"id":1,"uploader":"user1","comment":"sensitive body","time":"2026-08-01","score":0}
            ]}
        """.trimIndent()
        val root = objectMapper.readTree(json)

        assertTrue(PrivacyMaskFilter.redact(root))

        val c = root["comments"][0]
        assertEquals("", c["comment"].asText())
        assertEquals("", c["uploader"].asText())
        assertEquals("2026-08-01", c["time"].asText())
    }

    @Test
    fun `maintenance path is truncated to 10 chars with sizeBytes sibling`() {
        val json = """
            {"redundantFiles":[
              {"path":"1382450-[まだとんだし","sizeBytes":1024}
            ]}
        """.trimIndent()
        val root = objectMapper.readTree(json)

        assertTrue(PrivacyMaskFilter.redact(root))

        assertEquals("1382450-[ま", root["redundantFiles"][0]["path"].asText())
    }

    @Test
    fun `config-like path without sizeBytes sibling is untouched`() {
        val json = """{"download":{"path":"/data/downloads"},"cache":{"path":"/cache"}}"""
        val root = objectMapper.readTree(json)

        // 无 gid / 无 sizeBytes 锚点 → 不脱敏（配置往返安全）
        assertFalse(PrivacyMaskFilter.redact(root))
        assertEquals("/data/downloads", root["download"]["path"].asText())
    }

    // ── 过滤器接线（MockMvc 集成） ──────────────────────────────

    @Test
    fun `preview response is redacted only with the mask header`() {
        val service: DownloadMaintenanceService = mock(DownloadMaintenanceService::class.java)
        `when`(service.preview()).thenReturn(
            MaintenancePreviewResponse(
                redundantFiles = listOf(MaintenanceFileIssue("1382450-[まだとんだし", 1024)),
                invalidDownloads = listOf(
                    com.hippo.anotherviewer.web.dto.MaintenanceDownloadIssue(1, 600, "Real Title", "content_dir_missing")
                )
            )
        )
        val mockMvc: MockMvc = MockMvcBuilders
            .standaloneSetup(MaintenanceController(service))
            // addFilters 是 <T extends B> 泛型方法，Kotlin 链上推不出 T，需显式
            .addFilters<StandaloneMockMvcBuilder>(PrivacyMaskFilter(objectMapper))
            .build()

        // 无头（App / 关码）：全量
        mockMvc.perform(get("/api/v1/download/maintenance/preview"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.redundantFiles[0].path").value("1382450-[まだとんだし"))
            .andExpect(jsonPath("$.invalidDownloads[0].title").value("Real Title"))

        // 带头（WebUI 开码）：路径前 10 字符、标题 → #gid
        mockMvc.perform(get("/api/v1/download/maintenance/preview").header("X-Privacy-Mask", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.redundantFiles[0].path").value("1382450-[ま"))
            .andExpect(jsonPath("$.invalidDownloads[0].title").value("#600"))
    }
}
