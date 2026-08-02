package com.hippo.anotherviewer.web.dto

data class CacheStatsResponse(
    val diskCacheSizeBytes: Long,
    val diskCacheMaxBytes: Long,
    val memoryCacheEntries: Int,
    val memoryCacheMaxEntries: Int,
    val hitCount: Long,
    val missCount: Long,
    val hitRate: Double
)

data class SuccessResponse(
    val success: Boolean
)
