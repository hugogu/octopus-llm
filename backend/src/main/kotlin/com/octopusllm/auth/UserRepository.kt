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
     * Non-admin accounts on RFC-reserved example/test email domains — "garbage" accounts left by
     * tests or local experiments. JPQL (not native) so property-based pagination sort applies.
     * Kept in sync with [com.octopusllm.admin.TestAccountHeuristic].
     */
    @Query(SUSPECTED_TEST_WHERE)
    fun findSuspectedTestAccounts(pageable: Pageable): Page<User>

    @Query(SUSPECTED_TEST_WHERE)
    fun findAllSuspectedTestAccounts(): List<User>

    companion object {
        const val SUSPECTED_TEST_WHERE = """
            SELECT u FROM User u WHERE u.isAdmin = false AND (
              lower(u.email) LIKE '%@example.com'
              OR lower(u.email) LIKE '%@example.org'
              OR lower(u.email) LIKE '%@example.net'
              OR lower(u.email) LIKE '%@localhost'
              OR lower(u.email) LIKE '%.test'
              OR lower(u.email) LIKE '%.example'
              OR lower(u.email) LIKE '%.invalid'
              OR lower(u.email) LIKE '%.localhost'
            )
        """
    }

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
