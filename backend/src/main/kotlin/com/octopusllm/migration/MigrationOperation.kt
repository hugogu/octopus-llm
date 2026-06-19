package com.octopusllm.migration

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Type
import java.time.Instant
import java.util.UUID

/**
 * Migration operation ledger row (feature 008): idempotency claim + non-secret result audit for
 * admin export/import and shared-Quest import. Never holds a passphrase, provider key, endpoint
 * secret, or sensitive custom parameter (Constitution VI) — only counts and created ids.
 */
@Entity
@Table(name = "migration_operations")
class MigrationOperation(
    @Id
    @Column(columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @Column(name = "actor_user_id", nullable = false)
    val actorUserId: UUID,

    @Column(name = "operation_type", nullable = false, length = 32)
    val operationType: String, // admin_export | admin_import | share_import

    @Column(name = "idempotency_key_hash")
    val idempotencyKeyHash: ByteArray? = null,

    @Column(name = "source_digest")
    var sourceDigest: ByteArray? = null,

    @Column(nullable = false, length = 16)
    var status: String = STATUS_IN_PROGRESS,

    @Type(JsonType::class)
    @Column(columnDefinition = "jsonb", nullable = false)
    var result: Map<String, Any?> = emptyMap(),

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    companion object {
        const val TYPE_ADMIN_EXPORT = "admin_export"
        const val TYPE_ADMIN_IMPORT = "admin_import"
        const val TYPE_SHARE_IMPORT = "share_import"
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_SUCCEEDED = "succeeded"
        const val STATUS_FAILED = "failed"
    }
}
