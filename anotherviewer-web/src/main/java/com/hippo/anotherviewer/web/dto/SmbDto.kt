package com.hippo.anotherviewer.web.dto

data class SmbConfigResponse(
    val id: Long,
    val host: String,
    val port: Int,
    val share: String,
    val path: String?,
    val loginMode: String,
    val username: String?,
    val enabled: Boolean
)

data class SmbConfigUpdateRequest(
    val host: String,
    val port: Int = 445,
    val share: String,
    val path: String? = null,
    val loginMode: String = "GUEST",
    val username: String? = null,
    val password: String? = null,
    val enabled: Boolean = false
)

data class SmbTestConnectionRequest(
    val host: String,
    val port: Int = 445,
    val share: String,
    val path: String? = null,
    val loginMode: String = "GUEST",
    val username: String? = null,
    val password: String? = null
)

data class SmbTestConnectionResponse(
    val success: Boolean,
    val message: String
)

data class SmbSyncProgress(
    val state: String,
    val totalFiles: Int,
    val syncedFiles: Int,
    val currentFile: String,
    val speed: Long
)

data class SmbSyncRequest(
    val aggressive: Boolean = false
)
