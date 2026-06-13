package com.octopusllm.auth

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

data class RegisterRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank @field:Size(min = 8) val password: String,
)

data class VerifyEmailRequest(@field:NotBlank val token: String)

data class LoginRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val password: String,
)

data class LoginResponse(val token: String, val expiresAt: Instant)

data class PasswordResetConfirmRequest(
    @field:NotBlank val token: String,
    @field:NotBlank @field:Size(min = 8) val password: String,
)

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val jwtTokenService: JwtTokenService,
) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterRequest): Mono<Map<String, String>> =
        authService.register(request.email, request.password)
            .thenReturn(mapOf("message" to "Registration successful. You may now sign in."))

    @PostMapping("/verify-email")
    fun verifyEmail(@Valid @RequestBody request: VerifyEmailRequest): Mono<Map<String, String>> =
        authService.verifyEmail(request.token)
            .thenReturn(mapOf("message" to "Email verified. You may now log in."))

    @PostMapping("/password-reset/confirm")
    fun confirmPasswordReset(@Valid @RequestBody request: PasswordResetConfirmRequest): Mono<Map<String, String>> =
        authService.confirmPasswordReset(request.token, request.password)
            .thenReturn(mapOf("status" to "password_updated"))

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): Mono<LoginResponse> =
        authService.login(request.email, request.password).map { token ->
            val claims = jwtTokenService.validate(token).block()!!
            LoginResponse(token = token, expiresAt = claims.exp)
        }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@AuthenticationPrincipal principal: String?, request: org.springframework.web.server.ServerWebExchange): Mono<Void> {
        val authHeader = request.request.headers.getFirst("Authorization") ?: return Mono.empty()
        if (!authHeader.startsWith("Bearer ")) return Mono.empty()
        val token = authHeader.removePrefix("Bearer ")
        return jwtTokenService.validate(token).flatMap { claims ->
            authService.logout(claims.jti, claims.userId, claims.exp)
        }.then()
    }
}
