package com.octopusllm.auth

import org.springframework.http.HttpStatus
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val emailVerificationRepository: EmailVerificationRepository,
    private val revokedTokenRepository: RevokedTokenRepository,
    private val passwordResetRepository: PasswordResetRepository,
    private val jwtTokenService: JwtTokenService,
) {
    private val bcrypt = BCryptPasswordEncoder(12)

    fun register(email: String, password: String): Mono<Unit> = Mono.fromCallable {
        val normalizedEmail = email.lowercase()
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Email already registered")
        }
        val user = User(
            email = normalizedEmail,
            passwordHash = bcrypt.encode(password),
            emailVerified = true,
        )
        userRepository.save(user)
    }.subscribeOn(Schedulers.boundedElastic()).thenReturn(Unit)

    @Transactional
    fun verifyEmail(token: String): Mono<Unit> = Mono.fromCallable {
        val verification = emailVerificationRepository.findByToken(token)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification token")

        if (verification.usedAt != null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Token already used")
        }
        if (verification.expiresAt.isBefore(Instant.now())) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Token expired")
        }

        verification.usedAt = Instant.now()
        emailVerificationRepository.save(verification)

        val user = verification.user
        user.emailVerified = true
        user.updatedAt = Instant.now()
        userRepository.save(user)
    }.subscribeOn(Schedulers.boundedElastic()).thenReturn(Unit)

    fun login(email: String, password: String): Mono<String> = Mono.fromCallable {
        val user = userRepository.findByEmail(email.lowercase())
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")

        if (!bcrypt.matches(password, user.passwordHash)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
        }
        // A disabled account must not be issued a token even with correct credentials.
        if (user.isDisabled) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
        }
        jwtTokenService.issue(user.id, user.sessionEpoch)
    }.subscribeOn(Schedulers.boundedElastic())

    /**
     * Completes a password reset. Single-use is enforced atomically by [PasswordResetRepository.consume];
     * only the caller that flips `used_at` proceeds to set the new password.
     */
    fun confirmPasswordReset(token: String, newPassword: String): Mono<Unit> = Mono.fromCallable {
        val now = Instant.now()
        val won = passwordResetRepository.consume(token, now) == 1
        if (!won) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid, expired, or already-used reset token")
        }
        val reset = passwordResetRepository.findByToken(token)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reset token")
        val user = userRepository.findById(reset.user.id)
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reset token") }
        user.passwordHash = bcrypt.encode(newPassword)
        user.updatedAt = now
        userRepository.save(user)
    }.subscribeOn(Schedulers.boundedElastic()).thenReturn(Unit)

    fun logout(jti: String, userId: UUID, exp: Instant): Mono<Unit> = Mono.fromCallable {
        val revoked = RevokedToken(jti = jti, userId = userId, expiresAt = exp)
        revokedTokenRepository.save(revoked)
    }.subscribeOn(Schedulers.boundedElastic()).thenReturn(Unit)
}
