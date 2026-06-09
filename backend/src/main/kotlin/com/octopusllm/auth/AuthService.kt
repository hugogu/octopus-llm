package com.octopusllm.auth

import org.springframework.http.HttpStatus
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.security.SecureRandom
import java.time.Instant
import java.util.HexFormat
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val emailVerificationRepository: EmailVerificationRepository,
    private val revokedTokenRepository: RevokedTokenRepository,
    private val emailService: EmailService,
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
        )
        userRepository.save(user)

        val token = generateToken()
        val verification = EmailVerification(
            user = user,
            token = token,
            expiresAt = Instant.now().plusSeconds(86400),
        )
        emailVerificationRepository.save(verification)
        emailService.sendVerificationEmail(user.email, token)
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

        if (!user.emailVerified) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Email not verified")
        }
        if (!bcrypt.matches(password, user.passwordHash)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
        }
        user.id
    }.subscribeOn(Schedulers.boundedElastic()).map { userId ->
        jwtTokenService.issue(userId)
    }

    fun logout(jti: String, userId: UUID, exp: Instant): Mono<Unit> = Mono.fromCallable {
        val revoked = RevokedToken(jti = jti, userId = userId, expiresAt = exp)
        revokedTokenRepository.save(revoked)
    }.subscribeOn(Schedulers.boundedElastic()).thenReturn(Unit)

    private fun generateToken(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return HexFormat.of().formatHex(bytes)
    }
}
