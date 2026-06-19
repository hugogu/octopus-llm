package com.octopusllm.chat

import com.octopusllm.auth.User
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "chat_sessions")
class ChatSession(
    @Id
    @Column(columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(length = 500)
    var title: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "selected_model_id", length = 255)
    var selectedModelId: String? = null,

    // Feature 008: display-only provenance for Quests created via import. Ownership stays `user`.
    @Column(name = "imported_from_label", length = 255)
    var importedFromLabel: String? = null,

    @Column(name = "imported_at")
    var importedAt: Instant? = null,
)
