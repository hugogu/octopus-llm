package com.octopusllm.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

data class AuthActionThrottleId(
    var action: String = "",
    var keyHash: String = "",
    var windowStartedAt: Instant = Instant.EPOCH,
) : Serializable

@Entity
@IdClass(AuthActionThrottleId::class)
@Table(name = "auth_action_throttles")
class AuthActionThrottle(
    @Id
    @Column(nullable = false, length = 50)
    val action: String,

    @Id
    @Column(name = "key_hash", nullable = false, length = 64)
    val keyHash: String,

    @Id
    @Column(name = "window_started_at", nullable = false)
    val windowStartedAt: Instant,

    @Column(name = "request_count", nullable = false)
    val requestCount: Int = 1,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
)
