package com.octopusllm.migration

import com.fasterxml.jackson.databind.ObjectMapper
import com.octopusllm.chat.ChatSessionRepository
import com.octopusllm.chat.ChatTurnRepository
import com.octopusllm.chat.DialogRedactionService
import com.octopusllm.chat.ProviderResponseRepository
import com.octopusllm.chat.latestProviderResponses
import com.octopusllm.connection.ConfiguredModelRepository
import com.octopusllm.connection.ConnectionRepository
import com.octopusllm.userconfig.ApiKeyEncryptionService
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds the passphrase-encrypted migration artifact (feature 008, T024).
 *
 * NOTE (incremental): this slice exports Connections (+ configured models) and Quests (turns +
 * non-redacted responses) into the encrypted ZIP. Media object bytes (T026 staging path) and
 * controller streaming (T027) are layered on next; attachment references are carried through as-is.
 * Artifact-local ids reuse the source UUIDs — import always allocates fresh ids and remaps.
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
    private val crypto: MigrationArtifactCrypto,
    private val objectMapper: ObjectMapper,
) {
    /** Produces the full encrypted artifact as bytes. Decrypted keys live only in local variables. */
    fun export(passphrase: CharSequence): ByteArray {
        val salt = crypto.newSaltHex()
        val modelsByConnection = configuredModelRepository.findAll().groupBy { it.connection.id }

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

        return writeArtifact(passphrase, salt, connections, quests)
    }

    private fun writeArtifact(
        passphrase: CharSequence,
        salt: String,
        connections: List<ConnectionExport>,
        quests: List<QuestExport>,
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
