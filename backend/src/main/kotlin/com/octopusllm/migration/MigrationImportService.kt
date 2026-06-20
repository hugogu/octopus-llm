package com.octopusllm.migration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.UUID

class InvalidBundleException(detail: String) :
    ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_bundle: $detail")

class InvalidArtifactCredentialsException :
    ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_artifact_credentials")

class IncompatibleVersionException(version: Int) :
    ResponseStatusException(HttpStatus.CONFLICT, "incompatible_version: $version")

/**
 * A media object that has been re-stored under a fresh opaque id, ready for the DB commit to insert a
 * `media` row and rewrite the attachment references that pointed at [artifactMediaId].
 */
data class StagedMedia(
    val artifactMediaId: UUID,
    val newId: UUID,
    val mediaType: String,
    val mimeType: String,
    val sizeBytes: Long,
    val originalFilename: String?,
    val backend: String,
    val storageKey: String,
    val publicUrl: String,
)

data class MigrationImportResult(
    val questsImported: Int,
    val connectionsImported: Int,
    val connectionsRenamed: Int,
    val mediaImported: Int,
    val formatVersion: Int,
) {
    fun toResultMap(): Map<String, Any?> = mapOf(
        "questsImported" to questsImported,
        "connectionsImported" to connectionsImported,
        "connectionsRenamed" to connectionsRenamed,
        "mediaImported" to mediaImported,
        "formatVersion" to formatVersion,
    )

    companion object {
        fun fromResultMap(map: Map<String, Any?>): MigrationImportResult {
            fun int(key: String) = (map[key] as? Number)?.toInt() ?: 0
            return MigrationImportResult(
                int("questsImported"), int("connectionsImported"),
                int("connectionsRenamed"), int("mediaImported"), int("formatVersion"),
            )
        }
    }
}

/**
 * Validates and imports a migration artifact under the calling admin (feature 008, T025/T026).
 *
 * Flow: SafeZip parse → envelope/version/checksum validation → authenticated decrypt → single-tx
 * commit ([MigrationImportTxOps]). Idempotent per (admin, key): a successful replay returns the
 * original result without re-importing. (Media bytes + endpoint-policy revalidation are layered on
 * next; this slice imports Connections/models and Quests/turns/responses.)
 */
@Service
class MigrationImportService(
    private val crypto: MigrationArtifactCrypto,
    private val objectMapper: ObjectMapper,
    private val operationService: MigrationOperationService,
    private val txOps: MigrationImportTxOps,
    private val mediaStagingService: MigrationMediaStagingService,
) {
    fun import(artifact: ByteArray, passphrase: CharSequence, adminUserId: UUID, idempotencyKey: String): MigrationImportResult {
        val sourceDigest = sha256(artifact)
        val claim = operationService.claimImport(
            adminUserId, MigrationOperation.TYPE_ADMIN_IMPORT, idempotencyKey, sourceDigest,
        )
        if (!claim.isNew && claim.operation.status == MigrationOperation.STATUS_SUCCEEDED) {
            return MigrationImportResult.fromResultMap(claim.operation.result)
        }
        val operationId = claim.operation.id
        var staged: List<StagedMedia> = emptyList()
        return try {
            val bundle = parseAndDecrypt(artifact, passphrase)
            // Stage media bytes (object writes + crash-safe ledger) BEFORE the single DB transaction so
            // a rollback never leaves Quest/turn rows pointing at missing objects (R8 atomicity).
            staged = mediaStagingService.stage(
                operationId,
                bundle.media.map {
                    MediaToStage(
                        sourceMediaId = it.artifactMediaId,
                        mediaType = it.mediaType,
                        mimeType = it.mimeType,
                        sizeBytes = it.sizeBytes,
                        originalFilename = it.originalFilename,
                        content = it.content,
                    )
                },
            )
            val result = txOps.commit(bundle, staged, adminUserId)
            mediaStagingService.complete(operationId)
            operationService.succeed(claim.operation, result.toResultMap())
            result
        } catch (e: Throwable) {
            mediaStagingService.compensate(operationId, staged)
            operationService.fail(claim.operation)
            throw e
        }
    }

    private fun parseAndDecrypt(artifact: ByteArray, passphrase: CharSequence): MigrationBundle {
        val files = SafeZip.readAll(ByteArrayInputStream(artifact))
        val envelopeBytes = files["envelope.json"] ?: throw InvalidBundleException("missing envelope.json")
        val envelope = runCatching { objectMapper.readValue<MigrationEnvelope>(envelopeBytes) }
            .getOrElse { throw InvalidBundleException("unreadable envelope") }
        if (envelope.formatVersion != MIGRATION_FORMAT_VERSION) {
            throw IncompatibleVersionException(envelope.formatVersion)
        }

        val connections = mutableListOf<ConnectionExport>()
        val quests = mutableListOf<QuestExport>()
        val media = mutableListOf<MediaExport>()
        envelope.entries.forEach { entry ->
            val raw = files[entry.path] ?: throw InvalidBundleException("missing entry ${entry.path}")
            if (sha256Hex(raw) != entry.encryptedSha256) {
                throw InvalidBundleException("checksum mismatch for ${entry.path}")
            }
            val plain = runCatching { crypto.decrypt(passphrase, envelope.saltHex, raw) }
                .getOrElse { throw InvalidArtifactCredentialsException() }
            when (entry.kind) {
                "connection" -> connections += objectMapper.readValue<ConnectionExport>(plain)
                "quest" -> quests += objectMapper.readValue<QuestExport>(plain)
                "media" -> media += objectMapper.readValue<MediaExport>(plain)
                else -> throw InvalidBundleException("unknown entry kind ${entry.kind}")
            }
        }
        return MigrationBundle(
            exportedAt = envelope.createdAt,
            source = MigrationSource(),
            connections = connections,
            quests = quests,
            media = media,
        )
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun sha256Hex(bytes: ByteArray): String = sha256(bytes).joinToString("") { "%02x".format(it) }
}
