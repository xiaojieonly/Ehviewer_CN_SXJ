package com.hippo.anotherviewer.web.config

import com.hippo.anotherviewer.web.service.ServerConfigService
import com.hippo.anotherviewer.web.service.SiteAuthService
import jakarta.servlet.DispatcherType
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.context.ApplicationContext
import org.springframework.context.support.StaticApplicationContext
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.config.annotation.ObjectPostProcessor
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.security.web.header.HeaderWriterFilter
import org.springframework.web.servlet.handler.HandlerMappingIntrospector
import java.util.function.Supplier

/**
 * M-4: the SecurityConfig chain must emit X-Frame-Options / nosniff / CSP on
 * every response, and HSTS only for secure (HTTPS) requests. Built without a
 * Spring Boot context by driving the real HeaderWriterFilter of the configured
 * chain (a bare StaticApplicationContext satisfies the bean lookups that
 * SecurityConfig's configurers perform).
 *
 * T-3: additionally drives the full chain end-to-end (filter by filter) to pin
 * down authorization semantics per dispatcher type: ASYNC dispatches (the
 * continuation of an authenticated StreamingResponseBody/DeferredResult
 * request) must pass without re-authentication, while REQUEST dispatches keep
 * the existing bearer-token rules.
 */
class SecurityConfigTest {

    private val noOpPostProcessor: ObjectPostProcessor<Any> = object : ObjectPostProcessor<Any> {
        override fun <O : Any> postProcess(obj: O): O = obj
    }

    private fun chain(
        authService: SiteAuthService = mock(SiteAuthService::class.java),
        serverConfigService: ServerConfigService = mock(ServerConfigService::class.java),
    ): SecurityFilterChain {
        val context = StaticApplicationContext().apply {
            // .cors {} falls back to an MVC handler-mapping introspector when no
            // CorsConfigurationSource bean exists; a bare one is enough here.
            registerSingleton("mvcHandlerMappingIntrospector", HandlerMappingIntrospector::class.java)
            refresh()
        }
        val sharedObjects: MutableMap<Class<*>, Any> =
            mutableMapOf(ApplicationContext::class.java to context)
        val http = HttpSecurity(
            noOpPostProcessor,
            AuthenticationManagerBuilder(noOpPostProcessor),
            sharedObjects
        )
        return SecurityConfig().securityFilterChain(
            http,
            authService,
            serverConfigService
        )
    }

    /** Auth enabled, no valid token ever accepted unless stubbed per test. */
    private fun requireAuthChain(): Pair<SiteAuthService, SecurityFilterChain> {
        val authService = mock(SiteAuthService::class.java)
        val serverConfigService = mock(ServerConfigService::class.java)
        `when`(serverConfigService.getBoolean(ServerConfigService.KEY_REQUIRE_AUTH, false)).thenReturn(true)
        return authService to chain(authService, serverConfigService)
    }

    /** Run every filter of the chain in order against [request]. */
    private fun execute(chain: SecurityFilterChain, request: MockHttpServletRequest): MockHttpServletResponse {
        val response = MockHttpServletResponse()
        // No-op terminal servlet: the chain's last filter calls chain.doFilter, and
        // MockFilterChain only offers a (Servlet, Filter...) constructor.
        val terminal = object : HttpServlet() {
            override fun service(req: ServletRequest, res: ServletResponse) = Unit
        }
        MockFilterChain(terminal, *chain.filters.toTypedArray()).doFilter(request, response)
        return response
    }

    /**
     * A request as Tomcat serves it for a Boot app mapped on `/`: servletPath
     * carries the full path (AntPathRequestMatcher without a UrlPathHelper
     * matches on servletPath+pathInfo, not requestURI).
     */
    private fun apiRequest(method: String, path: String): MockHttpServletRequest =
        MockHttpServletRequest(method, path).apply { servletPath = path }

    private fun headerResponse(secure: Boolean): MockHttpServletResponse {
        val headerWriterFilter = chain().filters.filterIsInstance<HeaderWriterFilter>().single()
        val request = MockHttpServletRequest("GET", "/api/v1/health")
        request.isSecure = secure
        val response = MockHttpServletResponse()
        headerWriterFilter.doFilter(request, response, MockFilterChain())
        return response
    }

    @Test
    fun `frame options deny and nosniff are always present`() {
        val response = headerResponse(secure = false)
        assertEquals("DENY", response.getHeader("X-Frame-Options"))
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"))
    }

    @Test
    fun `CSP is a permissive-but-non-empty SPA policy`() {
        val response = headerResponse(secure = false)
        val csp = response.getHeader("Content-Security-Policy")
        assertTrue(csp!!.contains("default-src 'self'"))
        assertTrue(csp.contains("style-src 'self' 'unsafe-inline'"))
        assertTrue(csp.contains("img-src 'self' data: blob:"))
        assertTrue(csp.contains("connect-src 'self' ws: wss:"))
        assertTrue(csp.contains("object-src 'none'"))
    }

    @Test
    fun `HSTS is only emitted for secure requests`() {
        assertNull(headerResponse(secure = false).getHeader("Strict-Transport-Security"))
        val hsts = headerResponse(secure = true).getHeader("Strict-Transport-Security")
        assertTrue(hsts!!.contains("max-age=31536000"))
        assertTrue(hsts.contains("includeSubDomains"))
    }

    @Test
    fun `async dispatch of an api route passes without re-authentication`() {
        // T-3: an ASYNC dispatch (StreamingResponseBody et al.) is the continuation of an
        // already-authorized REQUEST dispatch. The bearer filter skips async dispatches and
        // the SecurityContext does not propagate, so re-authorizing would deny a legitimately
        // in-flight response. dispatcherTypeMatchers(ASYNC).permitAll() must let it through
        // even with no token present.
        val (_, chain) = requireAuthChain()
        val request = apiRequest("GET", "/api/v1/backup/export")
        request.dispatcherType = DispatcherType.ASYNC

        assertEquals(200, execute(chain, request).status)
    }

    @Test
    fun `the authorization rules grant async dispatches but still deny anonymous request dispatches`() {
        // Pin the dispatcherTypeMatchers(ASYNC).permitAll() rule itself. The
        // AuthorizationFilter may skip async dispatches (filterAsyncDispatch
        // defaults to false), so the rule is only observable by asking the
        // configured AuthorizationManager directly.
        val (_, chain) = requireAuthChain()
        val filter = chain.filters.filterIsInstance<AuthorizationFilter>().single()
        val managerField = AuthorizationFilter::class.java.getDeclaredField("authorizationManager")
        managerField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val manager = managerField.get(filter) as AuthorizationManager<HttpServletRequest>
        val noAuth = Supplier<Authentication?> { null }

        val asyncRequest = apiRequest("GET", "/api/v1/backup/export").apply {
            dispatcherType = DispatcherType.ASYNC
        }
        assertTrue(manager.check(noAuth, asyncRequest).isGranted, "ASYNC dispatch must be permitted")

        val requestDispatch = apiRequest("GET", "/api/v1/backup/export")
        assertTrue(!manager.check(noAuth, requestDispatch).isGranted, "anonymous REQUEST dispatch must be denied")
    }
    @Test
    fun `request dispatch of an api route still requires the bearer token`() {
        val (authService, chain) = requireAuthChain()

        val withoutToken = apiRequest("GET", "/api/v1/backup/export")
        assertEquals(401, execute(chain, withoutToken).status)

        `when`(authService.validateToken("valid-token")).thenReturn("alice")
        val withToken = apiRequest("GET", "/api/v1/backup/export")
        withToken.addHeader("Authorization", "Bearer valid-token")
        assertEquals(200, execute(chain, withToken).status)
    }

    @Test
    fun `request dispatch of public api routes passes without token`() {
        val (_, chain) = requireAuthChain()
        assertEquals(200, execute(chain, apiRequest("GET", "/api/v1/health")).status)
    }
}
