package com.hippo.anotherviewer.web.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

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
    @field:Size(max = 255, message = "host must be at most 255 characters")
    val host: String,
    @field:Min(1, message = "port must be between 1 and 65535")
    @field:Max(65535, message = "port must be between 1 and 65535")
    val port: Int = 445,
    @field:Size(max = 255, message = "share must be at most 255 characters")
    val share: String,
    @field:Size(max = 1024, message = "path must be at most 1024 characters")
    val path: String? = null,
    @field:Size(max = 16, message = "loginMode must be at most 16 characters")
    val loginMode: String = "GUEST",
    @field:Size(max = 255, message = "username must be at most 255 characters")
    val username: String? = null,
    @field:Size(max = 255, message = "password must be at most 255 characters")
    val password: String? = null,
    val enabled: Boolean = false
)

data class SmbTestConnectionRequest(
    @field:Size(max = 255, message = "host must be at most 255 characters")
    val host: String,
    @field:Min(1, message = "port must be between 1 and 65535")
    @field:Max(65535, message = "port must be between 1 and 65535")
    val port: Int = 445,
    @field:Size(max = 255, message = "share must be at most 255 characters")
    val share: String,
    @field:Size(max = 1024, message = "path must be at most 1024 characters")
    val path: String? = null,
    @field:Size(max = 16, message = "loginMode must be at most 16 characters")
    val loginMode: String = "GUEST",
    @field:Size(max = 255, message = "username must be at most 255 characters")
    val username: String? = null,
    @field:Size(max = 255, message = "password must be at most 255 characters")
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
