package com.octopusllm.media

import java.util.UUID

/** Result of persisting bytes to a storage backend. */
data class StoredMedia(
    val storageKey: String,
    val publicUrl: String,
    val backend: String,
)

/**
 * Strategy for persisting media bytes (feature 007). Implementations are Spring beans keyed by
 * [backend] ("local", "s3", …) and resolved at runtime by [MediaStorageFactory] from the admin
 * storage settings. Provider-/backend-specific logic stays entirely within an implementation.
 */
interface MediaStorage {
    /** Backend identifier matching `storage_settings.backend`. */
    val backend: String

    /** Deterministic object key used by [store], exposed so crash-cleanup ledgers can record it first. */
    fun storageKey(id: UUID, extension: String): String =
        if (extension.isBlank()) id.toString() else "$id.$extension"

    /** Persist [bytes] under an opaque key derived from [id]; returns the stored handle + public URL. */
    fun store(id: UUID, bytes: ByteArray, mimeType: String, extension: String): StoredMedia

    /** Read a stored object's bytes (e.g. to inline audio as base64 for providers that require it). */
    fun read(storageKey: String): ByteArray?

    /** Remove a previously stored object. Idempotent: absent objects are a no-op. */
    fun delete(storageKey: String)
}
