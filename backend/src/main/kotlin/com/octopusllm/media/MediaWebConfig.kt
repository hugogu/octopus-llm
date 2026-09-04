package com.octopusllm.media

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Bean
import org.springframework.web.server.WebFilter
import org.springframework.web.reactive.config.ResourceHandlerRegistry
import org.springframework.web.reactive.config.WebFluxConfigurer
import java.nio.file.Path

/**
 * Serves locally-stored media (feature 007) as static, unauthenticated public assets under the media
 * path (permitted in SecurityConfig). This is the "served directly without a per-file authenticated
 * backend request" path (FR-019); URLs are opaque (FR-022).
 */
@Configuration
class MediaWebConfig(
    @Value("\${media.local.dir:./data/media}") private val localDir: String,
) : WebFluxConfigurer {
    /**
     * Media URLs are intentionally public opaque references. Apply a defense-in-depth browser policy
     * to both newly uploaded and legacy objects so a mistakenly stored HTML/SVG file cannot execute
     * if someone navigates to its URL directly.
     */
    @Bean
    fun mediaSecurityHeaders(): WebFilter = WebFilter { exchange, chain ->
        if (exchange.request.path.value().startsWith("/media/")) {
            exchange.response.headers.set("X-Content-Type-Options", "nosniff")
            exchange.response.headers.set(
                "Content-Security-Policy",
                "default-src 'none'; base-uri 'none'; form-action 'none'; sandbox",
            )
        }
        chain.filter(exchange)
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val absolute = Path.of(localDir).toAbsolutePath().normalize().toUri().toString()
        val location = if (absolute.endsWith("/")) absolute else "$absolute/"
        registry.addResourceHandler("/media/**").addResourceLocations(location)
    }
}
