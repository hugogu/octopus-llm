package com.octopusllm.admin

import com.octopusllm.auth.PasswordReset
import com.octopusllm.auth.PasswordResetRepository
import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

/**
 * Synchronous, transactional account-state mutations. The last-usable-admin guard runs inside
 * SERIALIZABLE transactions (disable / reset) so concurrent requests conflict via SSI rather than
 * both reducing the usable-admin count to zero (C1 / C2). Lives in its own bean so callers reach it
 * through the Spring proxy (self-invocation would bypass @Transactional).
 */
@Service
class AdminUserTxOps(
    private val userRepository: UserRepository,
    private val passwordResetRepository: PasswordResetRepository,
) {
    private val bcrypt = BCryptPasswordEncoder(12)
    private val random = SecureRandom()
    private val resetTtl = java.time.Duration.ofHours(24)

    @Transactional
    fun activate(userId: UUID): User {
        val user = require(userId)
        user.isActive = true
        user.updatedAt = Instant.now()
        return userRepository.save(user)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun disable(userId: UUID): User {
        val user = require(userId)
        if (user.isDisabled) return user // idempotent
        if (isUsableAdmin(user) && userRepository.countUsableAdmins() <= 1) {
            throw lastAdmin("disable")
        }
        user.isDisabled = true
        user.sessionEpoch += 1
        user.updatedAt = Instant.now()
        return userRepository.save(user)
    }

    @Transactional
    fun enable(userId: UUID): User {
        val user = require(userId)
        if (!user.isDisabled) return user // idempotent
        user.isDisabled = false
        user.updatedAt = Instant.now()
        return userRepository.save(user)
    }

    /**
     * Invalidates the current password and sessions, then issues a single-use reset token.
     * Refused when the target is the last usable administrator (resetting it would lock out admin).
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun prepareReset(userId: UUID): Pair<User, PasswordReset> {
        val user = require(userId)
        if (isUsableAdmin(user) && userRepository.countUsableAdmins() <= 1) {
            throw lastAdmin("reset the password of")
        }
        user.passwordHash = bcrypt.encode(UUID.randomUUID().toString())
        user.sessionEpoch += 1
        user.updatedAt = Instant.now()
        userRepository.save(user)

        val token = PasswordReset(
            user = user,
            token = newToken(),
            expiresAt = Instant.now().plus(resetTtl).truncatedTo(ChronoUnit.MILLIS),
        )
        return user to passwordResetRepository.save(token)
    }

    /**
     * Hard-deletes a non-admin account and all of its owned data (cascades). Administrators are
     * refused (422) — demote first — which also avoids the audit/allocator restrict FKs and any
     * last-admin concern.
     */
    @Transactional
    fun delete(userId: UUID) {
        val user = require(userId)
        if (user.isAdmin) {
            throw ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Cannot delete an administrator; demote the account first",
            )
        }
        userRepository.delete(user)
    }

    /** Deletes every suspected-test (non-admin) account and returns their ids for auditing. */
    @Transactional
    fun deleteAllTestAccounts(): List<UUID> {
        val accounts = userRepository.findAllSuspectedTestAccounts()
        val ids = accounts.map { it.id }
        userRepository.deleteAll(accounts)
        return ids
    }

    private fun isUsableAdmin(user: User): Boolean =
        user.isAdmin && !user.isDisabled &&
            !passwordResetRepository.hasActiveReset(user.id, Instant.now())

    private fun require(userId: UUID): User =
        userRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }

    private fun newToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun lastAdmin(verb: String) =
        ResponseStatusException(HttpStatus.CONFLICT, "Cannot $verb the last administrator")
}
