package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.service.EhAuthService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: EhAuthService) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        val response = authService.register(request)
        return if (response.success) ResponseEntity.ok(response) else ResponseEntity.badRequest().body(response)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        val response = authService.login(request)
        return if (response.success) ResponseEntity.ok(response) else ResponseEntity.badRequest().body(response)
    }

    @GetMapping("/status")
    fun status(@RequestHeader("Authorization", required = false) authHeader: String?): ResponseEntity<AuthStatusResponse> {
        val token = authHeader?.removePrefix("Bearer ")
        return ResponseEntity.ok(authService.getStatus(token))
    }

    @PostMapping("/logout")
    fun logout(@RequestHeader("Authorization", required = false) authHeader: String?): ResponseEntity<AuthResponse> {
        val token = authHeader?.removePrefix("Bearer ")
        authService.logout(token)
        return ResponseEntity.ok(AuthResponse(true, "Logged out"))
    }
}
