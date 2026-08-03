package com.hippo.anotherviewer.web.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    // 默认只放行本机回环（任意端口）。WebUI 前端与 API 同源部署（Spring 托管
    // 静态资源，Vite dev server 也经 /api、/ws 代理到本后端），默认部署根本不
    // 触发 CORS，收紧默认值无影响。仅当把前端托管在其它 origin（如独立静态
    // 服务器、LAN 另一台机器）时，才需要 ANOTHERVIEWER_CORS_ORIGINS 显式配置
    // 实际前端 origin（逗号分隔）。显式 "*" 仍被透传（可信内网场景，
    // allowCredentials=true 下浏览器要求具体 origin 或通配符）。
    @Value("\${ANOTHERVIEWER_CORS_ORIGINS:http://localhost:*,http://127.0.0.1:*}")
    private val corsOrigins: String,
) : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        // Comma-separated origin allowlist via env var; defaults to loopback
        // only (M-3, see class doc above). An explicit "*" entry is passed
        // through as the wildcard.
        val origins = corsOrigins.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val patterns = if (origins.contains("*")) listOf("*") else origins
        registry.addMapping("/api/**")
            .allowedOriginPatterns(*patterns.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
    }
}
