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
        val config = CorsConfiguration().apply {
            allowedOrigins = listOf(frontendUrl)
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 3600L
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}
