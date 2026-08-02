package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.dto.*
import com.hippo.anotherviewer.web.service.SiteAuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.core.Authentication
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: SiteAuthService,
) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        if (!authService.isRegistrationAllowed()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(AuthResponse(false, "Registration is disabled on this server"))
        }
        val response = authService.register(request)
        return if (response.success) ResponseEntity.ok(response) else ResponseEntity.badRequest().body(response)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        val response = authService.login(request)
        return if (response.success) ResponseEntity.ok(response) else ResponseEntity.badRequest().body(response)
    }

    @PostMapping("/change-password")
    fun changePassword(
        authentication: Authentication,
        @Valid @RequestBody request: ChangePasswordRequest,
    ): ResponseEntity<AuthResponse> {
        val response = authService.changePassword(authentication.name, request)
        return if (response.success) ResponseEntity.ok(response) else ResponseEntity.badRequest().body(response)
    }

    /** Bean-validation failures must keep the `{success, message}` contract shape. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<AuthResponse> {
        val message = ex.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "Invalid request"
        return ResponseEntity.badRequest().body(AuthResponse(false, message))
    }

    /** Missing/undeserializable fields (e.g. omitted request keys) keep the contract shape too. */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(): ResponseEntity<AuthResponse> =
        ResponseEntity.badRequest().body(AuthResponse(false, "Old password and new password are required"))

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

    @PostMapping("/pair")
    fun generatePairCode(authentication: Authentication): ResponseEntity<PairCodeResponse> {
        return ResponseEntity.ok(authService.generatePairCode(authentication.name))
    }

    /** Alias for POST /pair (openapi client compatibility). */
    @PostMapping("/pair/start")
    fun generatePairCodeStart(authentication: Authentication): ResponseEntity<PairCodeResponse> =
        generatePairCode(authentication)

    @PostMapping("/pair/complete")
    fun completePairing(@Valid @RequestBody request: PairCompleteRequest): ResponseEntity<PairCompleteResponse> {
        val response = authService.completePairing(request)
        return if (response.success) ResponseEntity.ok(response) else ResponseEntity.badRequest().body(response)
    }
}
