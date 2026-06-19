package com.octopusllm.chat

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Append-only per-Dialog deletion marker (feature 008). A `turn` marker hides an entire turn
 * (user-prompt Dialog); a `response` marker hides a single provider response (model-answer Dialog).
 * The referenced turn/response rows are never mutated (Constitution IV). `turn_id`/`response_id` are
 * stored as plain UUIDs (not associations) because this is a lightweight read-time filter, and the
 * foreign keys + partial unique indexes are enforced in the database (see V033).
 */
@Entity
@Table(name = "dialog_redactions")
class DialogRedaction(
    @Id
    @Column(columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, length = 16)
    val scope: String, // "turn" | "response"

    @Column(name = "turn_id", nullable = false)
    val turnId: UUID,

    @Column(name = "response_id")
    val responseId: UUID? = null,

    @Column(name = "redacted_by")
    val redactedBy: UUID? = null,

    @Column(name = "redacted_at", nullable = false, updatable = false)
    val redactedAt: Instant = Instant.now(),
) {
    companion object {
        const val SCOPE_TURN = "turn"
        const val SCOPE_RESPONSE = "response"
    }
}
