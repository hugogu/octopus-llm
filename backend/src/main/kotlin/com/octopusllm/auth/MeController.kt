package com.octopusllm.auth

import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.UUID

data class MeResponse(
    val id: UUID,
    val email: String,
    val isAdmin: Boolean,
    val isActive: Boolean,
)

/**
 * Exposes the authenticated caller's own identity so the frontend can gate admin navigation (FR-026).
 * Never reveals other users or secret material.
 */
@RestController
@RequestMapping("/api/v2/me")
class MeController(
    private val userRepository: UserRepository,
) {
    @GetMapping
    fun me(@AuthenticationPrincipal principal: String): Mono<MeResponse> =
        Mono.fromCallable {
            val user = userRepository.findById(UUID.fromString(principal))
                .orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user") }
            MeResponse(
                id = user.id,
                email = user.email,
                isAdmin = user.isAdmin,
                isActive = user.isActive,
            )
        }.subscribeOn(Schedulers.boundedElastic())
}
