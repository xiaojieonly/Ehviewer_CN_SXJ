package com.hippo.anotherviewer.web.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.util.UUID

/**
 * Last-resort exception translation for every controller: uncaught
 * exceptions, validation failures, unreadable bodies, unknown resources and
 * unsupported methods all render as the uniform [ApiErrorEnvelope] instead
 * of Spring's ad-hoc default bodies.
 *
 * Per-controller `@ExceptionHandler`s (e.g. [AuthController]'s validation
 * handling) and existing, more specific advice (e.g. [AuthExceptionHandler])
 * keep precedence; this advice only covers what no one else handles.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(Exception::class)
    fun handleUncaught(ex: Exception): ResponseEntity<ApiErrorEnvelope> {
        val traceId = newTraceId()
        MDC.putCloseable(MDC_TRACE_KEY, traceId).use {
            log.error("Uncaught exception, traceId={}", traceId, ex)
        }
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error", traceId)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiErrorEnvelope> {
        val message = ex.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "Validation failed"
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ResponseEntity<ApiErrorEnvelope> {
        val message = ex.constraintViolations.firstOrNull()?.message ?: "Validation failed"
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(): ResponseEntity<ApiErrorEnvelope> =
        error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Malformed request body")

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ApiErrorEnvelope> =
        error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Invalid value for parameter '${ex.name}'")

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(ex: MissingServletRequestParameterException): ResponseEntity<ApiErrorEnvelope> =
        error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Missing required parameter '${ex.parameterName}'")

    @ExceptionHandler(NoResourceFoundException::class, NoHandlerFoundException::class)
    fun handleNotFound(): ResponseEntity<ApiErrorEnvelope> =
        error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found")

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotAllowed(
        ex: HttpRequestMethodNotSupportedException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorEnvelope> {
        // 405 归因（T-3）：带 URI/method/remoteAddr 记日志，便于从日志定位误调方。
        log.warn(
            "Method not allowed: {} {} from {}; supported={}",
            request.method, request.requestURI, request.remoteAddr, ex.supportedHttpMethods
        )
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.message ?: "Method not allowed")
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(
        ex: AccessDeniedException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorEnvelope> {
        // 安全过滤器链内的拒绝由 SecurityConfig 的 accessDeniedHandler 处理；走到这里的是
        // 控制器层（方法级安全等）抛出的拒绝，同样带请求上下文记日志并回 403（避免被
        // Exception::class 兜底误报 500）。AuthorizationDeniedException 是其子类，一并覆盖。
        log.warn("Access denied: {} {} from {} ({})", request.method, request.requestURI, request.remoteAddr, ex.message)
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied")
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleMediaTypeNotSupported(ex: HttpMediaTypeNotSupportedException): ResponseEntity<ApiErrorEnvelope> =
        error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", ex.message ?: "Unsupported media type")

    private fun error(
        status: HttpStatus,
        code: String,
        message: String,
        traceId: String = newTraceId(),
    ): ResponseEntity<ApiErrorEnvelope> =
        ResponseEntity.status(status).body(ApiErrorEnvelope(ApiError(code, message, traceId, status.value())))

    /** No MDC trace infrastructure exists yet; mint a short random id per error. */
    private fun newTraceId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    private companion object {
        const val MDC_TRACE_KEY = "traceId"
    }
}
