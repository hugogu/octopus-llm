package com.octopusllm.admin

import com.octopusllm.auth.JwtTokenService
import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import com.octopusllm.testsupport.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

class AdminSecurityIntegrationTest @Autowired constructor(
    private val webTestClient: WebTestClient,
    private val userRepository: UserRepository,
    private val jwtTokenService: JwtTokenService,
) : AbstractPostgresIntegrationTest() {

    private val bcrypt = BCryptPasswordEncoder(12)

    private fun newUser(admin: Boolean = false, disabled: Boolean = false): User =
        userRepository.save(
            User(
                email = "sec-${UUID.randomUUID()}@example.com",
                passwordHash = bcrypt.encode("Password123!"),
                emailVerified = true,
                isAdmin = admin,
                isActive = true,
                isDisabled = disabled,
            ),
        )

    @Test
    fun `non-admin is forbidden from admin endpoints`() {
        val user = newUser(admin = false)
        val token = jwtTokenService.issue(user.id, user.sessionEpoch)
        webTestClient.get().uri("/api/v2/admin/users")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `disabled user is rejected on authenticated requests`() {
        val user = newUser()
        val token = jwtTokenService.issue(user.id, user.sessionEpoch)
        webTestClient.get().uri("/api/v2/me").header("Authorization", "Bearer $token")
            .exchange().expectStatus().isOk

        user.isDisabled = true
        user.sessionEpoch += 1
        userRepository.save(user)

        webTestClient.get().uri("/api/v2/me").header("Authorization", "Bearer $token")
            .exchange().expectStatus().isUnauthorized
    }

    @Test
    fun `token issued before session epoch bump is rejected and a fresh token works`() {
        val user = newUser()
        val stale = jwtTokenService.issue(user.id, user.sessionEpoch)

        user.sessionEpoch += 1
        userRepository.save(user)

        webTestClient.get().uri("/api/v2/me").header("Authorization", "Bearer $stale")
            .exchange().expectStatus().isUnauthorized

        val fresh = jwtTokenService.issue(user.id, user.sessionEpoch)
        webTestClient.get().uri("/api/v2/me").header("Authorization", "Bearer $fresh")
            .exchange().expectStatus().isOk
    }
}
