package com.hippo.anotherviewer.web.config

import com.hippo.anotherviewer.web.dto.AuthResponse
import com.hippo.anotherviewer.web.service.SiteSessionExpiredException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Translates Gallery Site session-expiration signals into HTTP 401 responses so API
 * clients can detect an expired login and prompt the user to re-authenticate.
 */
@RestControllerAdvice
class AuthExceptionHandler {

    @ExceptionHandler(SiteSessionExpiredException::class)
    fun handleSessionExpired(ex: SiteSessionExpiredException): ResponseEntity<AuthResponse> {
        val message = ex.message ?: "Gallery Site session has expired. Please log in again."
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(AuthResponse(success = false, message = message))
    }
}
