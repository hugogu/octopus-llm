package com.octopusllm.auth

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface PasswordResetRepository : JpaRepository<PasswordReset, UUID> {
    fun findByToken(token: String): PasswordReset?

    @Query(
        value = """
            SELECT count(*) > 0 FROM password_resets
            WHERE user_id = :userId AND used_at IS NULL AND expires_at > :now
        """,
        nativeQuery = true,
    )
    fun hasActiveReset(@Param("userId") userId: UUID, @Param("now") now: Instant): Boolean

    /**
     * Atomic single-use consume: marks the token used only if it is currently unused and unexpired.
     * Returns 1 when this caller won the race, 0 otherwise (unknown, expired, or already used).
     */
    @Transactional
    @Modifying
    @Query(
        value = """
            UPDATE password_resets SET used_at = :now
            WHERE token = :token AND used_at IS NULL AND expires_at > :now
        """,
        nativeQuery = true,
    )
    fun consume(@Param("token") token: String, @Param("now") now: Instant): Int
}
