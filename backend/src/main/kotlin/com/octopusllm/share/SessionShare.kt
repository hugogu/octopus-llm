package com.octopusllm.share

import com.octopusllm.chat.ChatSession
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "session_shares")
class SessionShare(
    @Id
    @Column(columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    val session: ChatSession,

    @Column(nullable = false, unique = true, length = 64)
    val token: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,

    // Feature 008: audience scope. New shares default to AUTHENTICATED; existing rows backfilled to
    // PUBLIC by V032 so already-issued links keep working.
    @Convert(converter = ShareScopeConverter::class)
    @Column(nullable = false, length = 20)
    var scope: ShareScope = ShareScope.AUTHENTICATED,
)
