package com.octopusllm.migration

import com.octopusllm.auth.UserRepository
import com.octopusllm.chat.ChatSession
import com.octopusllm.chat.ChatSessionRepository
import com.octopusllm.chat.ChatTurn
import com.octopusllm.chat.ChatTurnRepository
import com.octopusllm.chat.ProviderResponse
import com.octopusllm.chat.ProviderResponseRepository
import com.octopusllm.connection.ConfiguredModel
import com.octopusllm.connection.ConfiguredModelRepository
import com.octopusllm.connection.Connection
import com.octopusllm.connection.ConnectionRepository
import com.octopusllm.userconfig.ApiKeyEncryptionService
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * Single-transaction commit of a decrypted migration bundle under the importing admin (feature 008,
 * T026). A separate bean so `@Transactional` applies through the Spring proxy: any failure rolls the
 * whole artifact back to zero business rows (FR-006). Provider keys are re-encrypted with the target
 * master key here; artifact-local ids are remapped to fresh ids consistently across turns/responses.
 */
@Service
class MigrationImportTxOps(
    private val userRepository: UserRepository,
    private val connectionRepository: ConnectionRepository,
    private val configuredModelRepository: ConfiguredModelRepository,
    private val sessionRepository: ChatSessionRepository,
    private val turnRepository: ChatTurnRepository,
    private val responseRepository: ProviderResponseRepository,
    private val encryptionService: ApiKeyEncryptionService,
) {
    @Transactional
    fun commit(bundle: MigrationBundle, adminUserId: UUID): MigrationImportResult {
        val admin = userRepository.findById(adminUserId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Importing admin not found")
        }
        val existingLabels = connectionRepository.findByUserId(adminUserId, Pageable.unpaged())
            .content.mapNotNull { it.label }.toMutableSet()

        val connectionIdMap = HashMap<UUID, UUID>()
        val modelIdMap = HashMap<UUID, UUID>()
        var connectionsRenamed = 0

        bundle.connections.forEach { ce ->
            val label = uniqueLabel(ce.label, existingLabels)
            if (label != ce.label) connectionsRenamed++
            val encrypted = encryptionService.encrypt(ce.apiKey)
            val connection = connectionRepository.save(
                Connection(
                    user = admin,
                    protocol = ce.protocol,
                    label = label,
                    baseUrl = ce.baseUrl,
                    encryptedKey = encrypted.ciphertext,
                    keyIv = encrypted.iv,
                    isBuiltin = false,
                ),
            )
            connectionIdMap[ce.artifactConnectionId] = connection.id
            ce.configuredModels.forEach { me ->
                val model = configuredModelRepository.save(
                    ConfiguredModel(
                        user = admin,
                        connection = connection,
                        modelId = me.modelId,
                        displayName = me.displayName,
                        capabilityOverrides = me.capabilityOverrides ?: emptyMap(),
                        customParams = me.customParams ?: emptyMap(),
                        inputPricePerMtok = me.inputPricePerMtok,
                        outputPricePerMtok = me.outputPricePerMtok,
                        priceCurrency = me.priceCurrency,
                    ),
                )
                modelIdMap[me.artifactConfiguredModelId] = model.id
            }
        }

        var questsImported = 0
        bundle.quests.forEach { qe ->
            val session = sessionRepository.save(
                ChatSession(
                    user = admin,
                    title = qe.title,
                    createdAt = qe.createdAt,
                    importedFromLabel = "Imported from ${qe.originalAuthorLabel ?: "another deployment"}",
                    importedAt = Instant.now(),
                ),
            )
            questsImported++
            qe.turns.forEach { te ->
                // Resolve configured-model references; unknown historical refs get a non-selectable
                // snapshot id so the turn/response stays internally consistent.
                val selectedConfigured = te.selectedArtifactConfiguredModelIds
                    .map { modelIdMap[it] ?: UUID.randomUUID() }
                val turn = turnRepository.save(
                    ChatTurn(
                        session = session,
                        sequenceNum = te.sequenceNum,
                        promptText = te.promptText,
                        attachments = te.attachments.ifEmpty { null },
                        selectedModelIds = te.selectedModelIds.toTypedArray(),
                        selectedConfiguredModelIds = selectedConfigured.toTypedArray(),
                        createdAt = te.createdAt,
                    ),
                )
                te.responses.forEach { re ->
                    responseRepository.save(
                        ProviderResponse(
                            turn = turn,
                            modelId = re.modelId,
                            configuredModelId = modelIdMap[re.artifactConfiguredModelId] ?: UUID.randomUUID(),
                            attemptNumber = re.attemptNumber,
                            modelDisplayName = re.modelDisplayName,
                            protocol = re.protocol,
                            connectionLabel = re.connectionLabel,
                            connectionId = re.artifactConnectionId?.let { connectionIdMap[it] },
                            status = re.status,
                            responseText = re.responseText,
                            reasoningText = re.reasoningText,
                            errorMessage = re.errorMessage,
                            inputTokens = re.inputTokens,
                            outputTokens = re.outputTokens,
                            cacheReadTokens = re.cacheReadTokens,
                            cacheWriteTokens = re.cacheWriteTokens,
                            latencyMs = re.latencyMs,
                            inputPricePerMtok = re.inputPricePerMtok,
                            outputPricePerMtok = re.outputPricePerMtok,
                            priceCurrency = re.priceCurrency,
                            createdAt = re.createdAt,
                        ),
                    )
                }
            }
        }

        return MigrationImportResult(
            questsImported = questsImported,
            connectionsImported = bundle.connections.size,
            connectionsRenamed = connectionsRenamed,
            mediaImported = 0,
            formatVersion = bundle.formatVersion,
        )
    }

    private fun uniqueLabel(label: String?, taken: MutableSet<String>): String? {
        if (label == null) return null
        if (taken.add(label)) return label
        var candidate = "$label (imported)"
        var n = 2
        while (!taken.add(candidate)) {
            candidate = "$label (imported $n)"
            n++
        }
        return candidate
    }
}
