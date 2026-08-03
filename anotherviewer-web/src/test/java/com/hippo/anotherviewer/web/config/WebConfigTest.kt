package com.hippo.anotherviewer.web.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.servlet.config.annotation.CorsRegistry

/** Exposes the protected registry view so unit tests can inspect the mapping. */
private class ExposedCorsRegistry : CorsRegistry() {
    fun configurations(): Map<String, CorsConfiguration> = getCorsConfigurations()
}

/**
 * M-3: the CORS allowlist must default to loopback-only (never "*"), keep the
 * env override, and still pass an explicit "*" through for trusted networks.
 */
class WebConfigTest {

    private fun corsConfig(envValue: String): CorsConfiguration {
        val registry = ExposedCorsRegistry()
        WebConfig(envValue).addCorsMappings(registry)
        return registry.configurations()["/api/**"]!!
    }

    @Test
    fun `default allowlist is loopback only, not wildcard`() {
        val config = corsConfig("http://localhost:*,http://127.0.0.1:*")
        val patterns = config.allowedOriginPatterns!!
        assertFalse(patterns.contains("*"))
        assertTrue(patterns.containsAll(listOf("http://localhost:*", "http://127.0.0.1:*")))
    }

    @Test
    fun `env override replaces the default allowlist`() {
        val config = corsConfig("https://reader.example.com")
        assertEquals(listOf("https://reader.example.com"), config.allowedOriginPatterns)
    }

    @Test
    fun `explicit wildcard passthrough is preserved for trusted networks`() {
        val config = corsConfig("*")
        assertEquals(listOf("*"), config.allowedOriginPatterns)
    }

    @Test
    fun `mixed list keeps the wildcard`() {
        val config = corsConfig("https://a.example.com,*,http://localhost:*")
        assertEquals(listOf("*"), config.allowedOriginPatterns)
    }

    @Test
    fun `empty env value yields no allowed patterns`() {
        val config = corsConfig("")
        assertEquals(0, config.allowedOriginPatterns!!.size)
    }

    @Test
    fun `credentials are enabled for the api mapping`() {
        val config = corsConfig("http://localhost:*")
        assertEquals(true, config.allowCredentials)
        assertEquals(listOf("GET", "POST", "PUT", "DELETE", "OPTIONS"), config.allowedMethods)
    }
}
