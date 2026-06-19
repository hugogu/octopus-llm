package com.octopusllm.migration

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Migration artifact payload DTOs (feature 008). Serialized into independently authenticated-encrypted
 * ZIP entries — never persisted as a table and never written in plaintext (Constitution VI).
 * Artifact-local ids decouple the bundle from any deployment's primary keys so import can remap
 * everything onto fresh rows. Populated/consumed by the export and import services (US1).
 */
const val MIGRATION_FORMAT_VERSION = 1

/**
 * Non-secret artifact manifest written in the clear as `envelope.json`: format/version, the KDF salt
 * (not secret), and an inventory of the independently encrypted entries with their encrypted-byte
 * checksums. Import reads this first to validate version/shape and verify each entry before decrypting.
 */
data class MigrationEnvelope(
    val formatVersion: Int = MIGRATION_FORMAT_VERSION,
    val createdAt: Instant,
    val kdf: String = "pbkdf2-aes256-gcm",
    val saltHex: String,
    val entries: List<ArtifactEntry>,
)

data class ArtifactEntry(
    val path: String, // e.g. connections/<artifact_connection_id>.enc | quests/<id>.enc | media/<id>.enc
    val kind: String, // connection | quest | media
    val encryptedSizeBytes: Long,
    val encryptedSha256: String,
)

/** Per-media metadata carried inside the (encrypted) payload so import can verify restored bytes. */
data class MediaDescriptor(
    val artifactMediaId: UUID,
    val mimeType: String,
    val plaintextSizeBytes: Long,
    val plaintextSha256: String,
)

data class MigrationBundle(
    val formatVersion: Int = MIGRATION_FORMAT_VERSION,
    val exportedAt: Instant,
    val source: MigrationSource,
    val connections: List<ConnectionExport>,
    val quests: List<QuestExport>,
    val media: List<MediaDescriptor> = emptyList(),
)

data class MigrationSource(
    val instanceId: String? = null,
    val version: String? = null,
)

data class ConnectionExport(
    val artifactConnectionId: UUID,
    val protocol: String,
    val label: String?,
    val baseUrl: String,
    val isBuiltin: Boolean,
    /** Exists only inside the authenticated-encrypted payload / process memory; never plaintext at rest. */
    val apiKey: String,
    val configuredModels: List<ConfiguredModelExport>,
)

data class ConfiguredModelExport(
    val artifactConfiguredModelId: UUID,
    val modelId: String,
    val displayName: String,
    val capabilityOverrides: Map<String, Any?>?,
    val customParams: Map<String, Any?>?,
    val inputPricePerMtok: BigDecimal?,
    val outputPricePerMtok: BigDecimal?,
    val priceCurrency: String?,
)

data class QuestExport(
    val artifactQuestId: UUID,
    val title: String?,
    val createdAt: Instant,
    val originalAuthorLabel: String?,
    val turns: List<TurnExport>,
)

data class TurnExport(
    val artifactTurnId: UUID,
    val sequenceNum: Int,
    val promptText: String,
    val attachments: List<Map<String, Any?>>,
    val selectedModelIds: List<String>,
    val selectedArtifactConfiguredModelIds: List<UUID>,
    val createdAt: Instant,
    val responses: List<ProviderResponseExport>,
)

data class ProviderResponseExport(
    val artifactResponseId: UUID,
    val artifactConfiguredModelId: UUID,
    val artifactConnectionId: UUID?,
    val attemptNumber: Int,
    val modelId: String,
    val modelDisplayName: String,
    val protocol: String,
    val connectionLabel: String?,
    val status: String,
    val responseText: String?,
    val reasoningText: String?,
    val errorMessage: String?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val cacheReadTokens: Int?,
    val cacheWriteTokens: Int?,
    val latencyMs: Int,
    val inputPricePerMtok: BigDecimal?,
    val outputPricePerMtok: BigDecimal?,
    val priceCurrency: String?,
    val createdAt: Instant,
)
