package com.hippo.anotherviewer.web.config

/**
 * Uniform error envelope returned by [GlobalExceptionHandler] for every
 * uncaught exception path. All API error responses share one outer key
 * (`error`) with a machine-readable [code], a human-readable [message],
 * a request-scoped [traceId] and the HTTP [status] echoed inside.
 */
data class ApiErrorEnvelope(
    val error: ApiError,
)

data class ApiError(
    val code: String,
    val message: String,
    val traceId: String,
    val status: Int,
)
