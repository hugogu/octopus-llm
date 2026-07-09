package com.octopusllm.auth

import org.springframework.http.HttpStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

data class PasswordUpdateResult(
    val token: String,
    val expiresAt: Instant,
)

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val emailVerificationRepository: EmailVerificationRepository,
    private val revokedTokenRepository: RevokedTokenRepository,
    private val passwordResetRepository: PasswordResetRepository,
    private val jwtTokenService: JwtTokenService,
    private val emailService: EmailService,
    private val throttleRepository: AuthActionThrottleRepository,
    @Value("\${app.jwt.secret}") private val throttleSecret: String,
) {
    private val bcrypt = BCryptPasswordEncoder(12)
    private val secureRandom = SecureRandom()

    fun register(email: String, password: String): Mono<Unit> = Mono.fromCallable {
        val normalizedEmail = email.lowercase()
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Email already registered")
        }
        // The very first registered account becomes the initial administrator and is auto-activated
        // so they can actually sign in (same shape as AdminBootstrap). Concurrent first-time
        // registrations are an acceptable race: at worst two admins instead of one.
        val isFirstUser = userRepository.count() == 0L
        val user = User(
            email = normalizedEmail,
            passwordHash = bcrypt.encode(password),
            emailVerified = false,
            isAdmin = isFirstUser,
            isActive = isFirstUser,
        )
        val saved = userRepository.save(user)
        issueVerification(saved)
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

    fun requestPasswordReset(email: String, clientIp: String?): Mono<Unit> = Mono.fromCallable {
        val normalizedEmail = email.trim().lowercase()
        val now = Instant.now()
        val emailAllowed = incrementWithinLimit(
            action = "password_reset_request",
            rawKey = "email:$normalizedEmail",
            now = now,
            limit = 5,
            windowSeconds = 900,
        )
        val networkAllowed = incrementWithinLimit(
            action = "password_reset_request",
            rawKey = "network:${clientIp ?: "unknown"}",
            now = now,
            limit = 30,
            windowSeconds = 900,
        )
        if (emailAllowed && networkAllowed) {
            userRepository.findByEmail(normalizedEmail)
                ?.takeIf { !it.isDisabled }
                ?.let { user ->
                    val reset = passwordResetRepository.save(
                        PasswordReset(
                            user = user,
                            token = randomToken(),
                            expiresAt = now.plus(1, ChronoUnit.HOURS),
                        ),
                    )
                    emailService.sendPasswordResetEmail(user.email, reset.token)
                }
        }
    }.subscribeOn(Schedulers.boundedElastic()).thenReturn(Unit)

    fun resendVerification(userId: UUID): Mono<Unit> = Mono.fromCallable {
        val user = userRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user") }
        if (user.emailVerified) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Email is already verified")
        }
        val now = Instant.now()
        val latest = emailVerificationRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
        if (latest != null && latest.createdAt.plusSeconds(60).isAfter(now)) {
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Verification resend cooldown has not elapsed")
        }
        if (!incrementWithinLimit("verification_resend", "user:$userId", now, 5, 3600)) {
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Verification resend limit exceeded")
        }
        issueVerification(user)
    }.subscribeOn(Schedulers.boundedElastic()).thenReturn(Unit)

    fun changePassword(userId: UUID, currentPassword: String, newPassword: String): Mono<PasswordUpdateResult> =
        Mono.fromCallable {
            val user = userRepository.findById(userId)
                .orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user") }
            if (!bcrypt.matches(currentPassword, user.passwordHash)) {
                throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
            }
            validateNewPassword(currentPassword, newPassword)
            user.passwordHash = bcrypt.encode(newPassword)
            user.sessionEpoch += 1
            user.updatedAt = Instant.now()
            userRepository.save(user)
            replacementToken(user)
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
        validateNewPassword(null, newPassword)
        user.passwordHash = bcrypt.encode(newPassword)
        user.sessionEpoch += 1
        user.updatedAt = now
        userRepository.save(user)
    }.subscribeOn(Schedulers.boundedElastic()).thenReturn(Unit)

    fun logout(jti: String, userId: UUID, exp: Instant): Mono<Unit> = Mono.fromCallable {
        val revoked = RevokedToken(jti = jti, userId = userId, expiresAt = exp)
        revokedTokenRepository.save(revoked)
    }.subscribeOn(Schedulers.boundedElastic()).thenReturn(Unit)

    private fun issueVerification(user: User) {
        emailVerificationRepository.deleteByUserIdAndUsedAtIsNull(user.id)
        val verification = emailVerificationRepository.save(
            EmailVerification(
                user = user,
                token = randomToken(),
                expiresAt = Instant.now().plus(24, ChronoUnit.HOURS),
            ),
        )
        emailService.sendVerificationEmail(user.email, verification.token)
    }

    private fun replacementToken(user: User): PasswordUpdateResult {
        val token = jwtTokenService.issue(user.id, user.sessionEpoch)
        val claims = jwtTokenService.validate(token).block()
            ?: throw IllegalStateException("Issued token could not be validated")
        return PasswordUpdateResult(token, claims.exp)
    }

    private fun validateNewPassword(currentPassword: String?, newPassword: String) {
        if (newPassword.length < 8) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be at least 8 characters")
        }
        if (currentPassword != null && currentPassword == newPassword) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must differ from current password")
        }
    }

    private fun incrementWithinLimit(
        action: String,
        rawKey: String,
        now: Instant,
        limit: Int,
        windowSeconds: Long,
    ): Boolean {
        val windowStart = Instant.ofEpochSecond((now.epochSecond / windowSeconds) * windowSeconds)
        val keyHash = hmac(rawKey)
        throttleRepository.increment(action, keyHash, windowStart, windowStart.plusSeconds(windowSeconds * 2))
        return (throttleRepository.requestCount(action, keyHash, windowStart) ?: 0) <= limit
    }

    private fun hmac(value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(throttleSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
