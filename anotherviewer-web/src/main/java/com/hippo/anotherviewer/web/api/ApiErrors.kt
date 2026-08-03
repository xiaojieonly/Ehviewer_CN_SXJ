package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.config.ApiError
import com.hippo.anotherviewer.web.config.ApiErrorEnvelope
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.util.UUID

/**
 * Builds the same uniform error envelope produced by
 * [com.hippo.anotherviewer.web.config.GlobalExceptionHandler] (M-6):
 * controller-side business-error paths construct it directly instead of
 * inventing ad-hoc `{success:false, message}` or bare bodies, so every error
 * response shares one JSON shape with a traceId and a machine-readable code.
 */
fun errorEnvelope(status: HttpStatus, code: String, message: String): ResponseEntity<ApiErrorEnvelope> =
    ResponseEntity.status(status).body(
        ApiErrorEnvelope(ApiError(code = code, message = message, traceId = newTraceId(), status = status.value()))
    )

/** Mirrors GlobalExceptionHandler's traceId scheme (16 hex chars). */
fun newTraceId(): String = UUID.randomUUID().toString().replace("-", "").take(16)
