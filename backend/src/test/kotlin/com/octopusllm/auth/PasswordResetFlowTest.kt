package com.octopusllm.auth

import com.ninjasquad.springmockk.MockkBean
import com.octopusllm.testsupport.AbstractPostgresIntegrationTest
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.jupiter.api.Assertions.assertEquals

class PasswordResetFlowTest @Autowired constructor(
    private val webTestClient: WebTestClient,
    private val userRepository: UserRepository,
    private val passwordResetRepository: PasswordResetRepository,
    private val jwtTokenService: JwtTokenService,
) : AbstractPostgresIntegrationTest() {

    @MockkBean(relaxed = true)
    private lateinit var emailService: EmailService

    private val bcrypt = BCryptPasswordEncoder(12)

    private fun newUser(admin: Boolean = false, disabled: Boolean = false, password: String = "Password123!"): User =
        userRepository.save(
            User(
                email = "pr-${UUID.randomUUID()}@example.com",
                passwordHash = bcrypt.encode(password),
                emailVerified = true,
                isAdmin = admin,
                isActive = true,
                isDisabled = disabled,
            ),
        )

    @Test
    fun `admin reset invalidates old password and confirm sets a new one`() {
        every { emailService.sendPasswordResetEmail(any(), any()) } returns Unit
        val admin = newUser(admin = true)
        // second admin so resetting the target (also non-admin) is never the last-admin case
        val target = newUser(password = "OldPass123!")

        webTestClient.post().uri("/api/v2/admin/users/${target.id}/reset-password")
            .header("Authorization", "Bearer ${jwtTokenService.issue(admin.id, admin.sessionEpoch)}")
            .exchange().expectStatus().isAccepted

        // old password no longer works
        webTestClient.post().uri("/api/v1/auth/login")
            .bodyValue(mapOf("email" to target.email, "password" to "OldPass123!"))
            .exchange().expectStatus().isUnauthorized

        val token = passwordResetRepository.findAll().first { it.user.id == target.id }.token

        webTestClient.post().uri("/api/v1/auth/password-reset/confirm")
            .bodyValue(mapOf("token" to token, "password" to "BrandNew123!"))
            .exchange().expectStatus().isOk

        webTestClient.post().uri("/api/v1/auth/login")
            .bodyValue(mapOf("email" to target.email, "password" to "BrandNew123!"))
            .exchange().expectStatus().isOk

        // token cannot be reused
        webTestClient.post().uri("/api/v1/auth/password-reset/confirm")
            .bodyValue(mapOf("token" to token, "password" to "Another123!"))
            .exchange().expectStatus().isBadRequest
    }

    @Test
    fun `disabled account cannot log in even with correct credentials`() {
        val user = newUser(disabled = true, password = "Correct123!")
        webTestClient.post().uri("/api/v1/auth/login")
            .bodyValue(mapOf("email" to user.email, "password" to "Correct123!"))
            .exchange().expectStatus().isUnauthorized
    }

    @Test
    fun `concurrent confirmations of the same token resolve to exactly one winner`() {
        val user = newUser()
        val token = UUID.randomUUID().toString()
        passwordResetRepository.save(
            PasswordReset(
                user = user,
                token = token,
                expiresAt = Instant.now().plus(1, ChronoUnit.HOURS),
            ),
        )

        val pool = Executors.newFixedThreadPool(8)
        val now = Instant.now()
        val tasks = (1..8).map { Callable { passwordResetRepository.consume(token, now) } }
        val winners = pool.invokeAll(tasks).sumOf { it.get() }
        pool.shutdown()

        assertEquals(1, winners, "exactly one concurrent consume must win")
    }
}
