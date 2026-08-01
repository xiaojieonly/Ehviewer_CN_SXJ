package com.hippo.ehviewer.web.config

import com.hippo.ehviewer.web.service.EhAuthService
import com.hippo.ehviewer.web.service.ServerConfigService
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

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        authService: EhAuthService,
        serverConfigService: ServerConfigService
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(AuthTokenFilter(authService, serverConfigService), UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests {
                it.requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                    .requestMatchers("/api/v1/auth/status", "/api/v1/auth/logout").permitAll()
                    .requestMatchers("/api/v1/auth/pair/complete").permitAll()
                    .requestMatchers("/api/v1/health", "/api/v1/metrics", "/api/v1/metrics/**").permitAll()
                    // Everything else under /api requires the bearer token; the
                    // SPA shell, static/PWA assets, SPA deep links and the SockJS
                    // handshake (/ws/**) stay public — the frontend authenticates
                    // REST calls via Authorization header and the WebSocket via
                    // the STOMP CONNECT frame (see composables/useWebSocket.ts).
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll()
            }
            .cors {}
        return http.build()
    }
}
