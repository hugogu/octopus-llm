package com.octopusllm.migration

import com.fasterxml.jackson.databind.ObjectMapper
import com.octopusllm.chat.ChatSessionRepository
import com.octopusllm.chat.ChatTurnRepository
import com.octopusllm.chat.DialogRedactionService
import com.octopusllm.chat.ProviderResponseRepository
import com.octopusllm.chat.latestProviderResponses
import com.octopusllm.connection.ConfiguredModelRepository
import com.octopusllm.connection.ConnectionRepository
import com.octopusllm.media.MediaRepository
import com.octopusllm.media.MediaStorageFactory
import com.octopusllm.userconfig.ApiKeyEncryptionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds the passphrase-encrypted migration artifact (feature 008, T024).
 *
 * Exports Connections (+ configured models), Quests (turns + non-redacted responses), and the media
 * objects those non-redacted turns reference into one passphrase-encrypted ZIP. Artifact-local ids
 * reuse the source UUIDs — import always allocates fresh ids and remaps every reference. Media bytes
 * are read one object at a time and never appear in plaintext at rest. (True HTTP streaming without
 * buffering the whole artifact is a follow-up; the controller streams the buffered artifact out.)
 */
@Service
class MigrationExportService(
    private val connectionRepository: ConnectionRepository,
    private val configuredModelRepository: ConfiguredModelRepository,
    private val sessionRepository: ChatSessionRepository,
    private val turnRepository: ChatTurnRepository,
    private val responseRepository: ProviderResponseRepository,
    private val dialogRedactionService: DialogRedactionService,
    private val encryptionService: ApiKeyEncryptionService,
    private val mediaRepository: MediaRepository,
    private val mediaStorageFactory: MediaStorageFactory,
    private val crypto: MigrationArtifactCrypto,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Produces the full encrypted artifact as bytes. Decrypted keys live only in local variables. */
    fun export(passphrase: CharSequence): ByteArray {
        val salt = crypto.newSaltHex()
        val modelsByConnection = configuredModelRepository.findAll().groupBy { it.connection.id }
        val referencedMediaIds = LinkedHashSet<UUID>()

        val connections = connectionRepository.findAll().map { connection ->
            ConnectionExport(
                artifactConnectionId = connection.id,
                protocol = connection.protocol,
                label = connection.label,
                baseUrl = connection.baseUrl,
                isBuiltin = connection.isBuiltin,
                apiKey = encryptionService.decrypt(connection.encryptedKey, connection.keyIv),
                configuredModels = modelsByConnection[connection.id].orEmpty().map { model ->
                    ConfiguredModelExport(
                        artifactConfiguredModelId = model.id,
                        modelId = model.modelId,
                        displayName = model.displayName,
                        capabilityOverrides = model.capabilityOverrides,
                        customParams = model.customParams,
                        inputPricePerMtok = model.inputPricePerMtok,
                        outputPricePerMtok = model.outputPricePerMtok,
                        priceCurrency = model.priceCurrency,
                    )
                },
            )
        }

        val quests = sessionRepository.findAll().map { session ->
            val turns = turnRepository.findBySessionIdOrderBySequenceNum(session.id)
            val redactions = dialogRedactionService.forTurns(turns.map { it.id })
            QuestExport(
                artifactQuestId = session.id,
                title = session.title,
                createdAt = session.createdAt,
                originalAuthorLabel = session.user.email,
                turns = turns.filterNot { redactions.isTurnRedacted(it.id) }.map { turn ->
                    val responses = latestProviderResponses(turn, responseRepository.findByTurnId(turn.id))
                        .filterNot { redactions.isResponseRedacted(it.id) }
                    turn.attachments.orEmpty().forEach { ref ->
                        (ref["media_id"] as? String)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                            ?.let { referencedMediaIds += it }
                    }
                    TurnExport(
                        artifactTurnId = turn.id,
                        sequenceNum = turn.sequenceNum,
                        promptText = turn.promptText,
                        attachments = turn.attachments.orEmpty(),
                        selectedModelIds = turn.selectedModelIds.toList(),
                        selectedArtifactConfiguredModelIds = turn.selectedConfiguredModelIds.toList(),
                        createdAt = turn.createdAt,
                        responses = responses.map { it.toExport() },
                    )
                },
            )
        }

        // Read referenced media one object at a time; skip rows whose bytes are no longer present.
        val media = referencedMediaIds.mapNotNull { mediaId ->
            val row = mediaRepository.findById(mediaId).orElse(null) ?: return@mapNotNull null
            val bytes = runCatching {
                mediaStorageFactory.resolveByBackend(row.storageBackend)?.read(row.storageKey)
            }.getOrNull()
            if (bytes == null) {
                log.warn("migration_export_media_missing id={} backend={}", mediaId.toString().take(8), row.storageBackend)
                return@mapNotNull null
            }
            MediaExport(
                artifactMediaId = row.id,
                mediaType = row.mediaType,
                mimeType = row.mimeType,
                sizeBytes = row.sizeBytes,
                originalFilename = row.originalFilename,
                content = bytes,
            )
        }

        return writeArtifact(passphrase, salt, connections, quests, media)
    }

    private fun writeArtifact(
        passphrase: CharSequence,
        salt: String,
        connections: List<ConnectionExport>,
        quests: List<QuestExport>,
        media: List<MediaExport>,
    ): ByteArray {
        val entries = mutableListOf<ArtifactEntry>()
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            connections.forEach { connection ->
                val path = "connections/${connection.artifactConnectionId}.enc"
                entries += encryptEntry(zip, path, "connection", passphrase, salt, connection)
            }
            quests.forEach { quest ->
                val path = "quests/${quest.artifactQuestId}.enc"
                entries += encryptEntry(zip, path, "quest", passphrase, salt, quest)
            }
            media.forEach { item ->
                val path = "media/${item.artifactMediaId}.enc"
                entries += encryptEntry(zip, path, "media", passphrase, salt, item)
            }
            val envelope = MigrationEnvelope(createdAt = Instant.now(), saltHex = salt, entries = entries)
            writeRaw(zip, "envelope.json", objectMapper.writeValueAsBytes(envelope))
        }
        return buffer.toByteArray()
    }

    private fun encryptEntry(
        zip: ZipOutputStream,
        path: String,
        kind: String,
        passphrase: CharSequence,
        salt: String,
        payload: Any,
    ): ArtifactEntry {
        val cipher = crypto.encrypt(passphrase, salt, objectMapper.writeValueAsBytes(payload))
        writeRaw(zip, path, cipher)
        return ArtifactEntry(path, kind, cipher.size.toLong(), sha256Hex(cipher))
    }

    private fun writeRaw(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

private fun com.octopusllm.chat.ProviderResponse.toExport() = ProviderResponseExport(
    artifactResponseId = id,
    artifactConfiguredModelId = configuredModelId,
    artifactConnectionId = connectionId,
    attemptNumber = attemptNumber,
    modelId = modelId,
    modelDisplayName = modelDisplayName,
    protocol = protocol,
    connectionLabel = connectionLabel,
    status = status,
    responseText = responseText,
    reasoningText = reasoningText,
    errorMessage = errorMessage,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    cacheReadTokens = cacheReadTokens,
    cacheWriteTokens = cacheWriteTokens,
    latencyMs = latencyMs,
    inputPricePerMtok = inputPricePerMtok,
    outputPricePerMtok = outputPricePerMtok,
    priceCurrency = priceCurrency,
    createdAt = createdAt,
)
