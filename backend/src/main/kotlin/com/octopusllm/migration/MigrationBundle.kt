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

data class MigrationBundle(
    val formatVersion: Int = MIGRATION_FORMAT_VERSION,
    val exportedAt: Instant,
    val source: MigrationSource,
    val connections: List<ConnectionExport>,
    val quests: List<QuestExport>,
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
