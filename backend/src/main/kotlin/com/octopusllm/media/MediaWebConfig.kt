package com.octopusllm.media

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
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
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val absolute = Path.of(localDir).toAbsolutePath().normalize().toUri().toString()
        val location = if (absolute.endsWith("/")) absolute else "$absolute/"
        registry.addResourceHandler("/media/**").addResourceLocations(location)
    }
}
