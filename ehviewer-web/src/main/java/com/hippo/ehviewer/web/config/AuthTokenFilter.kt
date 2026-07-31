package com.hippo.ehviewer.web.config

import com.hippo.ehviewer.web.service.EhAuthService
import com.hippo.ehviewer.web.service.ServerConfigService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class AuthTokenFilter(
    private val authService: EhAuthService,
    private val serverConfig: ServerConfigService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // 登录关闭时，所有请求以 anonymous 身份放行
        if (!serverConfig.getBoolean(ServerConfigService.KEY_REQUIRE_AUTH, false)) {
            val auth = UsernamePasswordAuthenticationToken("default", null, listOf(SimpleGrantedAuthority("ROLE_USER")))
            SecurityContextHolder.getContext().authentication = auth
            filterChain.doFilter(request, response)
            return
        }
        val authHeader = request.getHeader("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val token = authHeader.removePrefix("Bearer ")
            val username = authService.validateToken(token)
            if (username != null) {
                val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))
                val auth = UsernamePasswordAuthenticationToken(username, null, authorities)
                SecurityContextHolder.getContext().authentication = auth
            }
        }
        filterChain.doFilter(request, response)
    }
}
