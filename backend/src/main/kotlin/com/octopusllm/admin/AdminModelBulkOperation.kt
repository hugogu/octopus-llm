package com.octopusllm.admin

import com.octopusllm.auth.User
import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Type
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "admin_model_bulk_operations")
class AdminModelBulkOperation(
    @Id
    @Column(columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_user_id", nullable = false)
    val adminUser: User,

    @Column(nullable = false, length = 30)
    val action: String,

    @Column(name = "selection_mode", nullable = false, length = 20)
    val selectionMode: String,

    @Type(JsonType::class)
    @Column(name = "selection_filter", columnDefinition = "jsonb", nullable = false)
    val selectionFilter: Map<String, Any?> = emptyMap(),

    @Column(nullable = false, length = 20)
    var status: String,

    @Column(name = "target_count", nullable = false)
    var targetCount: Int = 0,

    @Column(name = "processed_count", nullable = false)
    var processedCount: Int = 0,

    @Column(name = "success_count", nullable = false)
    var successCount: Int = 0,

    @Column(name = "failure_count", nullable = false)
    var failureCount: Int = 0,

    @Column(name = "changed_count", nullable = false)
    var changedCount: Int = 0,

    @Column(name = "already_satisfied_count", nullable = false)
    var alreadySatisfiedCount: Int = 0,

    @Column(name = "idempotency_key_hash", length = 64)
    var idempotencyKeyHash: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant = Instant.now().plusSeconds(15 * 60),

    @Column(name = "started_at")
    var startedAt: Instant? = null,

    @Column(name = "completed_at")
    var completedAt: Instant? = null,
)
