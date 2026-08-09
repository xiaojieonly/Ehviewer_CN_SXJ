package com.hippo.anotherviewer.web.config

import com.hippo.anotherviewer.web.service.SiteAuthService
import com.hippo.anotherviewer.web.service.ServerConfigService
import jakarta.servlet.DispatcherType
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig {

    private val logger = LoggerFactory.getLogger(SecurityConfig::class.java)

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        authService: SiteAuthService,
        serverConfigService: ServerConfigService
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(AuthTokenFilter(authService, serverConfigService), UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling {
                // Missing/invalid bearer token on a protected /api route must yield
                // 401 (not the default 403) so clients trigger re-login.
                it.authenticationEntryPoint { _, response, _ ->
                    response.status = HttpServletResponse.SC_UNAUTHORIZED
                    response.contentType = "application/json;charset=UTF-8"
                    response.writer.write("""{"success":false,"message":"Authentication required"}""")
                }
                // 403 归因（T-3）：拒绝时带请求上下文（method/uri/remoteAddr）记日志，
                // 取代 AccessDeniedHandlerImpl 无上下文的「Cannot send 403」日志；响应已
                // 提交（async 写入阶段）无法再改状态码，只记 ERROR 不再写响应体。
                it.accessDeniedHandler { request, response, ex ->
                    if (response.isCommitted) {
                        logger.error(
                            "Access denied after response committed: {} {} from {} ({})",
                            request.method, request.requestURI, request.remoteAddr, ex.message
                        )
                    } else {
                        logger.warn(
                            "Access denied: {} {} from {} ({})",
                            request.method, request.requestURI, request.remoteAddr, ex.message
                        )
                        response.status = HttpServletResponse.SC_FORBIDDEN
                        response.contentType = "application/json;charset=UTF-8"
                        response.writer.write("""{"success":false,"message":"Access denied"}""")
                    }
                }
            }
            .authorizeHttpRequests {
                // ASYNC dispatch（StreamingResponseBody/DeferredResult 等）是已认证 REQUEST
                // dispatch 的延续：AuthTokenFilter（OncePerRequestFilter）默认跳过 async
                // dispatch，SecurityContext 不跨 dispatch 传播（STATELESS 下 REQUEST 结束即
                // 清空），若按路径规则重复鉴权会对空上下文抛 AuthorizationDeniedException ——
                // 响应已提交时沦为 ERROR 日志噪音，未提交时表面化为 401。故对 ASYNC dispatch
                // 一律放行：只按 dispatcher 类型匹配，REQUEST dispatch 的既有规则保持不变；
                // 攻击者无法绕过 REQUEST 鉴权直接发起 ASYNC dispatch，无鉴权绕过面。
                it.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                    .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                    .requestMatchers("/api/v1/auth/eh-login").permitAll()
                    .requestMatchers("/api/v1/auth/status").permitAll()
                    .requestMatchers("/api/v1/auth/pair/complete").permitAll()
                    .requestMatchers("/api/v1/auth/register-device").permitAll()
                    .requestMatchers("/api/v1/health", "/api/v1/metrics", "/api/v1/metrics/**").permitAll()
                    // Everything else under /api requires the bearer token (logout
                    // included); the SPA shell, static/PWA assets, SPA deep links
                    // and the SockJS handshake (/ws/**) stay public — the frontend
                    // authenticates REST calls via Authorization header and the
                    // WebSocket via the STOMP CONNECT frame (useWebSocket.ts).
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll()
            }
            .headers { headers ->
                // 应用层安全响应头（M-4）。分层策略：Caddy 反代部署（deploy/Caddyfile）
                // 已设同值头（X-Content-Type-Options nosniff / X-Frame-Options DENY），
                // 双层一致不冲突；本配置保证无反代时（Docker/systemd 直接暴露）同样有头。
                headers.frameOptions { frame -> frame.deny() }
                headers.contentTypeOptions {}
                // HSTS 只在 request.isSecure()（直接 HTTPS 连接）时写入；经 Caddy 等
                // 反代终结 TLS 时 Spring 看到的是内联 HTTP，不会写入 —— 完整 HTTPS
                // 部署的 HSTS 应在反代层添加（见 README「安全响应头」）。
                headers.httpStrictTransportSecurity { hsts -> hsts.includeSubDomains(true) }
                // 宽松但非空的 CSP（适配 SPA，见 README「安全响应头」的取舍说明）：
                //  - 脚本仅同源（Vite 产物为外部文件，无内联脚本，无需 'unsafe-inline'）
                //  - style-src 'unsafe-inline'：Vue 动态 style 绑定/运行时注入样式
                //  - img-src data: blob:：占位图与 reader 的 blob 图源
                //  - connect-src ws: wss:：SockJS/STOMP 走同源 /ws；'self' 在旧浏览器
                //    不匹配 ws 方案，显式放行以兼容（同源即可用）
                //  - 未启用 'unsafe-eval'（Vite 生产构建无 eval）与 frame-ancestors
                headers.contentSecurityPolicy(
                    "default-src 'self'; " +
                        "style-src 'self' 'unsafe-inline'; " +
                        "img-src 'self' data: blob:; " +
                        "connect-src 'self' ws: wss:; " +
                        "font-src 'self' data:; " +
                        "object-src 'none'; " +
                        "base-uri 'self'; " +
                        "form-action 'self'"
                )
            }
            .cors {}
        return http.build()
    }
}
