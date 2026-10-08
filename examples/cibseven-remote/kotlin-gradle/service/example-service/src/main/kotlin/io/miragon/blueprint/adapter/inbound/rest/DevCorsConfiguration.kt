package io.miragon.blueprint.adapter.inbound.rest

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Documented escape hatch you should NOT need on the production path — this blueprint is headless, so
 * nothing is served cross-origin by default. It exists for local development: if you point a separate
 * API client (a UI prototype, an API explorer) at this worker from another origin, activate the `dev`
 * profile to allow it. See CONTRIBUTING.md.
 */
@Configuration
@Profile("dev")
class DevCorsConfiguration : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry
            .addMapping("/api/**")
            .allowedOrigins("http://localhost:5173")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH")
    }
}
