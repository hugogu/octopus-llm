package com.octopusllm.media

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * An uploaded media object (feature 007). Bytes live in the configured storage backend; this row is
 * the opaque, durable handle. [id] doubles as the public URL path segment, so it MUST stay
 * non-enumerable (UUIDv4). [turnId] is null while the upload is orphaned and set when the owning chat
 * turn is saved, after which the media is immutable.
 */
@Entity
@Table(name = "media")
class Media(
    @Id
    @Column(columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @Column(name = "owner_user_id", nullable = false, columnDefinition = "UUID")
    val ownerUserId: UUID,

    @Column(name = "media_type", nullable = false, length = 16)
    val mediaType: String,

    @Column(name = "mime_type", nullable = false, length = 255)
    val mimeType: String,

    @Column(name = "size_bytes", nullable = false)
    val sizeBytes: Long,

    @Column(name = "storage_backend", nullable = false, length = 16)
    val storageBackend: String,

    @Column(name = "storage_key", nullable = false, columnDefinition = "TEXT")
    val storageKey: String,

    @Column(name = "public_url", nullable = false, columnDefinition = "TEXT")
    val publicUrl: String,

    @Column(name = "original_filename", columnDefinition = "TEXT")
    val originalFilename: String? = null,

    @Column(name = "turn_id", columnDefinition = "UUID")
    var turnId: UUID? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
