package com.octopusllm.anonymous

import com.octopusllm.auth.User
import com.octopusllm.chat.ChatSession
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "anonymous_conversation_imports")
class AnonymousConversationImport(
    @Id
    @Column(columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "source_conversation_id", nullable = false)
    val sourceConversationId: UUID,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id")
    val session: ChatSession? = null,

    @Column(name = "source_digest", nullable = false, length = 64)
    val sourceDigest: String,

    @Column(nullable = false, length = 20)
    var status: String,

    @Column(name = "last_error", length = 1000)
    var lastError: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "synced_at")
    var syncedAt: Instant? = null,
)
