package com.hippo.anotherviewer.web.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    @Value("\${ANOTHERVIEWER_CORS_ORIGINS:*}")
    private val corsOrigins: String,
) : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        // Comma-separated origin allowlist via env var; defaults to "*" for
        // LAN deployments where the SPA is served from arbitrary hosts.
        // An explicit "*" entry is passed through as the wildcard.
        val origins = corsOrigins.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val patterns = if (origins.contains("*")) listOf("*") else origins
        registry.addMapping("/api/**")
            .allowedOriginPatterns(*patterns.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
    }
}
