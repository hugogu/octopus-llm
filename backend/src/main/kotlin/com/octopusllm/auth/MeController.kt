package com.octopusllm.auth

import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.UUID

data class MeResponse(
    val id: UUID,
    val email: String,
    val displayName: String?,
    val emailVerified: Boolean,
    val emailVerificationStatus: String,
    val isAdmin: Boolean,
    val isActive: Boolean,
)

data class UpdateProfileRequest(val displayName: String?)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)

data class ChangePasswordResponse(
    val status: String,
    val token: String,
    val expiresAt: java.time.Instant,
)

/**
 * Exposes the authenticated caller's own identity so the frontend can gate admin navigation (FR-026).
 * Never reveals other users or secret material.
 */
@RestController
@RequestMapping("/api/v2/me")
class MeController(
    private val userRepository: UserRepository,
    private val authService: AuthService,
) {
    @GetMapping
    fun me(@AuthenticationPrincipal principal: String): Mono<MeResponse> =
        Mono.fromCallable {
            val user = userRepository.findById(UUID.fromString(principal))
                .orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user") }
            response(user)
        }.subscribeOn(Schedulers.boundedElastic())

    @PatchMapping
    fun update(
        @AuthenticationPrincipal principal: String,
        @RequestBody request: UpdateProfileRequest,
    ): Mono<MeResponse> =
        Mono.fromCallable {
            val user = userRepository.findById(UUID.fromString(principal))
                .orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user") }
            user.displayName = request.displayName?.trim()?.also {
                if (it.isEmpty() || it.length > 255) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName must be 1 to 255 characters")
                }
            }
            user.updatedAt = java.time.Instant.now()
            response(userRepository.save(user))
        }.subscribeOn(Schedulers.boundedElastic())

    @PostMapping("/password")
    fun changePassword(
        @AuthenticationPrincipal principal: String,
        @RequestBody request: ChangePasswordRequest,
    ): Mono<ChangePasswordResponse> =
        authService.changePassword(UUID.fromString(principal), request.currentPassword, request.newPassword)
            .map { ChangePasswordResponse("password_updated", it.token, it.expiresAt) }

    @PostMapping("/email-verification/resend")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.ACCEPTED)
    fun resendVerification(@AuthenticationPrincipal principal: String): Mono<Map<String, String>> =
        authService.resendVerification(UUID.fromString(principal))
            .thenReturn(mapOf("status" to "verification_sent"))

    private fun response(user: User) = MeResponse(
        id = user.id,
        email = user.email,
        displayName = user.displayName,
        emailVerified = user.emailVerified,
        emailVerificationStatus = if (user.emailVerified) "verified" else "pending",
        isAdmin = user.isAdmin,
        isActive = user.isActive,
    )
}
