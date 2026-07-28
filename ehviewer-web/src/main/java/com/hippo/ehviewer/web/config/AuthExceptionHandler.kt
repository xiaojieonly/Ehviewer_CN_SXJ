package com.hippo.ehviewer.web.config

import com.hippo.ehviewer.web.dto.AuthResponse
import com.hippo.ehviewer.web.service.EhSessionExpiredException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Translates E-Hentai session-expiration signals into HTTP 401 responses so API
 * clients can detect an expired login and prompt the user to re-authenticate.
 */
@RestControllerAdvice
class AuthExceptionHandler {

    @ExceptionHandler(EhSessionExpiredException::class)
    fun handleSessionExpired(ex: EhSessionExpiredException): ResponseEntity<AuthResponse> {
        val message = ex.message ?: "E-Hentai session has expired. Please log in again."
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(AuthResponse(success = false, message = message))
    }
}
