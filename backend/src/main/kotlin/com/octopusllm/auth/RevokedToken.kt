package com.octopusllm.auth

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "revoked_tokens")
class RevokedToken(
    @Id
    @Column(length = 255)
    val jti: String,

    @Column(name = "user_id", nullable = false, columnDefinition = "UUID")
    val userId: UUID,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "revoked_at", nullable = false)
    val revokedAt: Instant = Instant.now(),
)
