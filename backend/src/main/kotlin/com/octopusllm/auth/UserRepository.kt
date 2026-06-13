package com.octopusllm.auth

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean

    fun findByEmailContainingIgnoreCase(email: String, pageable: Pageable): Page<User>

    fun countByIsAdminTrueAndIsDisabledFalse(): Long

    /**
     * Count of administrators who can actually administer right now: enabled, not disabled, and with
     * no active (unused, unexpired) password reset outstanding. Used by the last-usable-admin guard
     * for both disable and password-reset, evaluated inside a SERIALIZABLE transaction so concurrent
     * mutations conflict instead of both reducing the count to zero.
     */
    @Query(
        value = """
            SELECT count(*) FROM users u
            WHERE u.is_admin AND NOT u.is_disabled
              AND NOT EXISTS (
                SELECT 1 FROM password_resets pr
                WHERE pr.user_id = u.id AND pr.used_at IS NULL AND pr.expires_at > NOW()
              )
        """,
        nativeQuery = true,
    )
    fun countUsableAdmins(): Long
}
