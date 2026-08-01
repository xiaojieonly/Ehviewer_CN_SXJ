package com.hippo.ehviewer.web.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * SPA fallback: every non-API, non-static browser path — including deep links
 * of arbitrary depth such as `/gallery/123/page/4` — is forwarded to the SPA
 * `index.html`. Controller mappings always take precedence over these view
 * controllers, so the `/api` and `/ws` prefixes keep working.
 *
 * The `assets`, `icons` and `ws` prefixes are excluded from the fallback so
 * static resources and the WebSocket endpoint are never swallowed.
 */
@Configuration
class SpaWebConfig : WebMvcConfigurer {
    override fun addViewControllers(registry: ViewControllerRegistry) {
        registry.addViewController("/").setViewName("forward:/index.html")
        registry.addViewController("/{path:[^\\.]*}").setViewName("forward:/index.html")
        registry.addViewController("/{path:[^\\.]*}/{path2:[^\\.]*}").setViewName("forward:/index.html")
        registry.addViewController("/{path:(?!api|ws|assets|icons|actuator|error)[^\\.]*}/**")
            .setViewName("forward:/index.html")
    }
}
