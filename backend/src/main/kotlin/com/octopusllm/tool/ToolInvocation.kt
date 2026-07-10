package com.octopusllm.tool

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Type
import java.time.Instant
import java.util.UUID

/**
 * A single tool execution within a turn (feature 009). Deduplicated by the unique
 * `(quest_id, turn_id, tool_name, arguments_hash)` so identical invocations across models are stored
 * once; the join to immutable provider_responses records which responses consumed it.
 */
@Entity
@Table(name = "tool_invocations")
class ToolInvocation(
    @Id
    @Column(columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @Column(name = "quest_id", nullable = false)
    val questId: UUID,

    @Column(name = "turn_id", nullable = false)
    val turnId: UUID,

    @Column(name = "tool_name", nullable = false, length = 64)
    val toolName: String,

    @Column(name = "arguments_hash", nullable = false, length = 64)
    val argumentsHash: String,

    @Type(JsonType::class)
    @Column(nullable = false, columnDefinition = "jsonb")
    val arguments: Map<String, Any?>,

    @Type(JsonType::class)
    @Column(columnDefinition = "jsonb")
    var result: Map<String, Any?>? = null,

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,

    // Stored as the lowercase ToolInvocationStatus.value to satisfy the DB CHECK constraint.
    @Column(nullable = false, length = 16)
    var status: String = ToolInvocationStatus.PENDING.value,

    @Column(name = "started_at")
    var startedAt: Instant? = null,

    @Column(name = "completed_at")
    var completedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
