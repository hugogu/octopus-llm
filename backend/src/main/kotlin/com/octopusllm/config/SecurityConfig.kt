package com.octopusllm.config

import com.octopusllm.auth.JwtTokenService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.context.ServerSecurityContextRepository
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.net.URI

@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val jwtTokenService: JwtTokenService,
    @Value("\${app.frontend.url}") private val frontendUrl: String,
) {

    @Bean
    fun securityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .securityContextRepository(jwtSecurityContextRepository())
            .authorizeExchange { exchanges ->
                exchanges
                    .pathMatchers("/api/v1/auth/**").permitAll()
                    .pathMatchers("/api/v1/models/**").permitAll()
                    .pathMatchers("/api/v1/health").permitAll()
                    .anyExchange().authenticated()
            }
            .exceptionHandling {
                it.authenticationEntryPoint { exchange, _ ->
                    Mono.fromRunnable {
                        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
                    }
                }
            }
            .build()

    @Bean
    fun jwtSecurityContextRepository(): ServerSecurityContextRepository =
        object : ServerSecurityContextRepository {
            override fun save(exchange: ServerWebExchange, context: SecurityContext): Mono<Void> = Mono.empty()

            override fun load(exchange: ServerWebExchange): Mono<SecurityContext> {
                val header = exchange.request.headers.getFirst("Authorization")
                    ?: return Mono.empty()
                if (!header.startsWith("Bearer ")) return Mono.empty()
                val token = header.removePrefix("Bearer ")
                return jwtTokenService.validate(token).map { claims ->
                    val auth = UsernamePasswordAuthenticationToken(
                        claims.userId.toString(),
                        null,
                        listOf(SimpleGrantedAuthority("ROLE_USER")),
                    )
                    SecurityContextImpl(auth) as SecurityContext
                }.onErrorResume { Mono.empty() }
            }
        }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val allowedOriginsList = buildAllowedOrigins(frontendUrl)
        val config = CorsConfiguration().apply {
            allowedOrigins = allowedOriginsList
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 3600L
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }

    private fun buildAllowedOrigins(primaryOrigin: String): List<String> {
        val origins = linkedSetOf(primaryOrigin)
        runCatching { URI(primaryOrigin) }.getOrNull()?.let { uri ->
            val scheme = uri.scheme ?: return@let
            val host = uri.host ?: return@let
            val port = uri.port
            val alternateHost = when (host) {
                "localhost" -> "127.0.0.1"
                "127.0.0.1" -> "localhost"
                else -> null
            }
            if (alternateHost != null) {
                val normalizedPort = if (port >= 0) ":$port" else ""
                origins += "$scheme://$alternateHost$normalizedPort"
            }
        }
        return origins.toList()
    }
}
