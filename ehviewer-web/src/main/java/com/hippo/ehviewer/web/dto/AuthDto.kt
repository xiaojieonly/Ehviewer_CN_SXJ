package com.hippo.ehviewer.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class LoginRequest(
    @field:NotBlank
    @field:Size(max = 64)
    val username: String,
    @field:NotBlank
    @field:Size(max = 72)
    val password: String
)

data class RegisterRequest(
    @field:NotBlank
    @field:Size(max = 64)
    val username: String,
    @field:NotBlank
    @field:Size(max = 72)
    val password: String
)

data class AuthResponse(
    val success: Boolean,
    val message: String,
    val token: String? = null,
    val username: String? = null
)

data class ChangePasswordRequest(
    @field:NotBlank(message = "Old password is required")
    val oldPassword: String,
    @field:NotBlank(message = "New password is required")
    @field:Size(min = 6, message = "New password must be at least 6 characters")
    @field:Size(max = 72, message = "New password must be at most 72 characters")
    val newPassword: String,
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
