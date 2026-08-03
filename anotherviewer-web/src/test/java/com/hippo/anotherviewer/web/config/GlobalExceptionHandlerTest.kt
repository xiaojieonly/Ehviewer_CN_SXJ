package com.hippo.anotherviewer.web.config

import com.jayway.jsonpath.JsonPath
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException

class ValidatedBody(
    @field:NotBlank(message = "name is required")
    val name: String,
)

/** Endpoints that trigger every failure mode [GlobalExceptionHandler] translates. */
@RestController
class FaultyController {

    @GetMapping("/boom")
    fun boom(): String = throw RuntimeException("kaboom")

    @PostMapping("/validated")
    fun validated(@Valid @RequestBody body: ValidatedBody): String = "ok"

    @PostMapping("/unreadable")
    fun unreadable(@RequestBody body: ValidatedBody): String = "ok"

    @GetMapping("/mismatch")
    fun mismatch(@RequestParam("page") page: Int): String = "ok"

    @GetMapping("/missing")
    fun missing(@RequestParam("name") name: String): String = "ok"

    @GetMapping("/no-resource")
    fun noResource(): String = throw NoResourceFoundException(HttpMethod.GET, "/api/v1/nope")

    @GetMapping("/no-handler")
    fun noHandler(): String = throw NoHandlerFoundException("GET", "/api/v1/nope", HttpHeaders())
}

/**
 * Contract tests for the uniform error envelope: every failure path renders
 * as `{ "error": { code, message, traceId, status } }` with the right HTTP
 * status and error code, per audit M-6 (advice-first wave).
 */
class GlobalExceptionHandlerTest {

    private fun mvc(): MockMvc {
        val mockMvc = MockMvcBuilders.standaloneSetup(FaultyController())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
        // Unmapped paths must surface as NoHandlerFoundException so the
        // advice's 404 translation is exercised through the real dispatch.
        mockMvc.dispatcherServlet.setThrowExceptionIfNoHandlerFound(true)
        return mockMvc
    }

    private fun perform(request: MockHttpServletRequestBuilder) = mvc().perform(request)

    @Test
    fun `uncaught exception returns 500 with the uniform envelope and a traceId`() {
        val body = perform(get("/boom"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error.status").value(500))
            .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.error.message").value("Internal server error"))
            .andExpect(jsonPath("$.error.traceId").exists())
            .andReturn().response.contentAsString

        val traceId: String = JsonPath.read(body, "$.error.traceId")
        assertTrue(traceId.isNotBlank(), "traceId must be non-empty")
    }

    @Test
    fun `bean validation failure returns 400 VALIDATION_ERROR with the first field error`() {
        perform(post("/validated")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":""}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.status").value(400))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("name is required"))
            .andExpect(jsonPath("$.error.traceId").exists())
    }

    @Test
    fun `unreadable request body returns 400 BAD_REQUEST`() {
        perform(post("/unreadable")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.status").value(400))
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.error.message").value("Malformed request body"))
            .andExpect(jsonPath("$.error.traceId").exists())
    }

    @Test
    fun `unmapped path returns 404 NOT_FOUND`() {
        perform(get("/api/v1/does-not-exist"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.status").value(404))
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.error.message").value("Resource not found"))
            .andExpect(jsonPath("$.error.traceId").exists())
    }

    @Test
    fun `NoResourceFoundException returns 404 NOT_FOUND`() {
        perform(get("/no-resource"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.error.traceId").exists())
    }

    @Test
    fun `NoHandlerFoundException returns 404 NOT_FOUND`() {
        perform(get("/no-handler"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.error.traceId").exists())
    }

    @Test
    fun `wrong http method returns 405 METHOD_NOT_ALLOWED`() {
        perform(post("/boom"))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(jsonPath("$.error.status").value(405))
            .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"))
            .andExpect(jsonPath("$.error.traceId").exists())
    }

    @Test
    fun `type mismatch on a request parameter returns 400 BAD_REQUEST`() {
        perform(get("/mismatch").param("page", "abc"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.error.traceId").exists())
    }

    @Test
    fun `missing required request parameter returns 400 BAD_REQUEST`() {
        perform(get("/missing"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.error.traceId").exists())
    }
}
