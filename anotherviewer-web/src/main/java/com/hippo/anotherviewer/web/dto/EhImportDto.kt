package com.hippo.anotherviewer.web.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 单条 cookie 记录：来自 okhttp3-cookie.db 的 `OK_HTTP_3_COOKIE` 表
 * （_id, NAME, VALUE, EXPIRES_AT, DOMAIN, PATH, SECURE, HTTP_ONLY,
 * PERSISTENT, HOST_ONLY），或等价的 JSON 数组元素。字段名与 JSON 契约一致。
 */
data class EhImportCookieDto(
    val name: String,
    val value: String,
    @JsonProperty("expiresAt")
    val expiresAt: Long = 0,
    val domain: String,
    val path: String = "/",
    val secure: Boolean = false,
    @JsonProperty("httpOnly")
    val httpOnly: Boolean = false,
    val persistent: Boolean = false,
    @JsonProperty("hostOnly")
    val hostOnly: Boolean = false,
)

/** 各表导入成功行数（B3 契约：`imported` 对象键）。 */
data class EhImportedCounts(
    val downloads: Int = 0,
    val history: Int = 0,
    val filters: Int = 0,
    val quickSearches: Int = 0,
    val labels: Int = 0,
    val bookmarks: Int = 0,
    val favorites: Int = 0,
    val dirnames: Int = 0,
    @JsonProperty("blackList")
    val blackList: Int = 0,
    @JsonProperty("galleryTags")
    val galleryTags: Int = 0,
)

/** cookies 段结果：siteDomain=命中站点域的 cookie 数，imported=实际写入 cookieStore 数。 */
data class EhCookieImportResult(
    val imported: Int = 0,
    @JsonProperty("siteDomain")
    val siteDomain: Int = 0,
)

/** POST /api/v1/backup/import-ehviewer 响应（B3 契约）。 */
data class EhImportResponse(
    val success: Boolean,
    val imported: EhImportedCounts,
    val cookies: EhCookieImportResult,
    val skipped: Int,
)
