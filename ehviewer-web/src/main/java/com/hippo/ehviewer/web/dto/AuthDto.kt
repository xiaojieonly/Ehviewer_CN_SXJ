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
    val username: String? = null
)
