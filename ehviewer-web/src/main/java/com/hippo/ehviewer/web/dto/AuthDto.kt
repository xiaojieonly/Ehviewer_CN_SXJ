package com.hippo.ehviewer.web.dto

import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank val username: String,
    @field:NotBlank val password: String
)

data class RegisterRequest(
    @field:NotBlank val username: String,
    @field:NotBlank val password: String
)

data class AuthResponse(
    val success: Boolean,
    val message: String,
    val token: String? = null,
    val username: String? = null
)

data class AuthStatusResponse(
    val authenticated: Boolean,
    val username: String? = null,
    val authRequired: Boolean = false,
    // E-Hentai session state sourced from the unified cookie store shared with ehviewer-core.
    val ehSessionValid: Boolean = false,
    val ehSessionExpired: Boolean = false
)

// --- Device pairing ---

data class PairCodeResponse(
    val code: String,
    val expiresAt: Long,
)

data class PairCompleteRequest(
    @field:NotBlank val code: String,
    @field:NotBlank val deviceId: String,
    @field:NotBlank val deviceName: String,
    val platform: String = "android",
)

data class PairCompleteResponse(
    val success: Boolean,
    val message: String = "",
    val token: String = "",
    val username: String = "",
)
