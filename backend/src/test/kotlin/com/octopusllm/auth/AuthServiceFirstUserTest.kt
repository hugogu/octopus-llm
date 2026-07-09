package com.octopusllm.auth

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier

/**
 * Verifies that the very first registration promotes the new account to administrator + active so
 * a freshly-deployed system can be used immediately (FE-friendly default). Subsequent registrations
 * keep the existing non-admin / inactive defaults.
 */
class AuthServiceFirstUserTest {

    private val userRepository = mockk<UserRepository>()
    private val emailVerificationRepository = mockk<EmailVerificationRepository>(relaxed = true)
    private val revokedTokenRepository = mockk<RevokedTokenRepository>(relaxed = true)
    private val passwordResetRepository = mockk<PasswordResetRepository>(relaxed = true)
    private val jwtTokenService = mockk<JwtTokenService>(relaxed = true)
    private val emailService = mockk<EmailService>(relaxed = true)
    private val throttleRepository = mockk<AuthActionThrottleRepository>(relaxed = true)

    init {
        // JpaRepository.save() default returns Object/null under a relaxed mock — AuthService reads
        // verification.token after save, so we must return the argument.
        every { emailVerificationRepository.save(any()) } answers { firstArg() }
    }

    private val authService = AuthService(
        userRepository = userRepository,
        emailVerificationRepository = emailVerificationRepository,
        revokedTokenRepository = revokedTokenRepository,
        passwordResetRepository = passwordResetRepository,
        jwtTokenService = jwtTokenService,
        emailService = emailService,
        throttleRepository = throttleRepository,
        throttleSecret = "0123456789012345678901234567890123456789012345678901234567890123",
    )

    @Test
    fun `first registered user is promoted to admin and active`() {
        every { userRepository.existsByEmail("founder@example.com") } returns false
        every { userRepository.count() } returns 0L
        every { userRepository.save(any()) } answers { firstArg() }

        StepVerifier.create(authService.register("founder@example.com", "Test1234!"))
            .assertNext { /* Unit */ }
            .verifyComplete()

        verify { userRepository.save(match { it.isAdmin && it.isActive }) }
        verify(exactly = 0) { userRepository.save(match { !it.isAdmin || !it.isActive }) }
    }

    @Test
    fun `subsequent registrations stay non-admin and inactive`() {
        every { userRepository.existsByEmail("second@example.com") } returns false
        every { userRepository.count() } returns 5L
        every { userRepository.save(any()) } answers { firstArg() }

        StepVerifier.create(authService.register("second@example.com", "Test1234!"))
            .assertNext { /* Unit */ }
            .verifyComplete()

        verify { userRepository.save(match { !it.isAdmin && !it.isActive }) }
        verify(exactly = 0) { userRepository.save(match { it.isAdmin || it.isActive }) }
    }

    @Test
    fun `email conflict is rejected before the first-user check`() {
        every { userRepository.existsByEmail("taken@example.com") } returns true

        StepVerifier.create(authService.register("taken@example.com", "Test1234!"))
            .expectErrorMatches { it is org.springframework.web.server.ResponseStatusException && it.statusCode.value() == 409 }
            .verify()

        verify(exactly = 0) { userRepository.count() }
        verify(exactly = 0) { userRepository.save(any()) }
    }
}
