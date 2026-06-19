package com.octopusllm.migration

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Tracks one external media object an import is about to write (feature 008, T014). The row is
 * committed BEFORE the filesystem/S3 write so an interrupted import leaves enough information to
 * delete the orphaned object. The successful import commit deletes these rows; a retry-safe sweep
 * cleans up rows for failed/stale operations.
 */
@Embeddable
class MigrationStagedMediaId(
    @Column(name = "operation_id")
    var operationId: UUID = ZERO,

    @Column(name = "media_id")
    var mediaId: UUID = ZERO,
) : Serializable {
    override fun equals(other: Any?): Boolean =
        other is MigrationStagedMediaId && other.operationId == operationId && other.mediaId == mediaId

    override fun hashCode(): Int = 31 * operationId.hashCode() + mediaId.hashCode()

    private companion object {
        val ZERO: UUID = UUID(0L, 0L)
    }
}

@Entity
@Table(name = "migration_staged_media")
class MigrationStagedMedia(
    @EmbeddedId
    val id: MigrationStagedMediaId,

    @Column(name = "storage_backend", nullable = false, length = 16)
    val storageBackend: String,

    @Column(name = "storage_key", nullable = false, columnDefinition = "TEXT")
    val storageKey: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
