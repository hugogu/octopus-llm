package com.octopusllm.auth

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface AuthActionThrottleRepository : JpaRepository<AuthActionThrottle, AuthActionThrottleId> {
    @Transactional
    @Modifying
    @Query(
        value = """
            INSERT INTO auth_action_throttles(
                action, key_hash, window_started_at, request_count, expires_at
            ) VALUES (
                :action, :keyHash, :windowStartedAt, 1, :expiresAt
            )
            ON CONFLICT (action, key_hash, window_started_at)
            DO UPDATE SET request_count = auth_action_throttles.request_count + 1,
                          expires_at = GREATEST(auth_action_throttles.expires_at, EXCLUDED.expires_at)
        """,
        nativeQuery = true,
    )
    fun increment(
        @Param("action") action: String,
        @Param("keyHash") keyHash: String,
        @Param("windowStartedAt") windowStartedAt: Instant,
        @Param("expiresAt") expiresAt: Instant,
    ): Int

    @Query(
        value = """
            SELECT request_count FROM auth_action_throttles
            WHERE action = :action
              AND key_hash = :keyHash
              AND window_started_at = :windowStartedAt
        """,
        nativeQuery = true,
    )
    fun requestCount(
        @Param("action") action: String,
        @Param("keyHash") keyHash: String,
        @Param("windowStartedAt") windowStartedAt: Instant,
    ): Int?

    @Transactional
    @Modifying
    @Query("DELETE FROM AuthActionThrottle throttle WHERE throttle.expiresAt <= :now")
    fun deleteExpired(@Param("now") now: Instant): Int
}
