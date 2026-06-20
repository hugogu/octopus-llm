package com.octopusllm.chat

import com.octopusllm.auth.UserRepository
import com.octopusllm.media.Media
import com.octopusllm.media.MediaRepository
import com.octopusllm.migration.StagedMedia
import com.octopusllm.share.SharedQuestImportSource
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

data class SharedQuestImportResult(
    val sessionId: UUID,
    val title: String?,
    val importedFromLabel: String = "Imported from a shared Quest",
) {
    fun toResultMap(): Map<String, Any?> = mapOf(
        "sessionId" to sessionId.toString(),
        "title" to title,
        "importedFromLabel" to importedFromLabel,
    )

    companion object {
        fun fromResultMap(result: Map<String, Any?>) = SharedQuestImportResult(
            sessionId = UUID.fromString(result["sessionId"] as String),
            title = result["title"] as? String,
            importedFromLabel = result["importedFromLabel"] as? String ?: "Imported from a shared Quest",
        )
    }
}

@Service
class SharedQuestImportTxOps(
    private val userRepository: UserRepository,
    private val sessionRepository: ChatSessionRepository,
    private val turnRepository: ChatTurnRepository,
    private val responseRepository: ProviderResponseRepository,
    private val mediaRepository: MediaRepository,
) {
    @Transactional
    fun commit(source: SharedQuestImportSource, stagedMedia: List<StagedMedia>, importerId: UUID): SharedQuestImportResult {
        val importer = userRepository.findById(importerId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        val session = sessionRepository.save(
            ChatSession(
                user = importer,
                title = source.title,
                importedFromLabel = "Imported from a shared Quest",
                importedAt = Instant.now(),
            ),
        )
        val stagedBySourceId = stagedMedia.associateBy(StagedMedia::artifactMediaId)
        val modelIdMap = mutableMapOf<UUID, UUID>()

        source.turns.forEach { sourceTurn ->
            val attachments = sourceTurn.attachments.map { attachment ->
                val sourceMediaId = (attachment["media_id"] as? String)
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val staged = sourceMediaId?.let(stagedBySourceId::get)
                if (staged == null) attachment else attachment + mapOf(
                    "media_id" to staged.newId.toString(),
                    "url" to staged.publicUrl,
                )
            }
            val turn = turnRepository.save(
                ChatTurn(
                    session = session,
                    sequenceNum = sourceTurn.sequenceNum,
                    promptText = sourceTurn.promptText,
                    attachments = attachments.ifEmpty { null },
                    selectedModelIds = sourceTurn.selectedModelIds.toTypedArray(),
                    selectedConfiguredModelIds = sourceTurn.selectedConfiguredModelIds
                        .map { modelIdMap.getOrPut(it) { UUID.randomUUID() } }
                        .toTypedArray(),
                    createdAt = sourceTurn.createdAt,
                ),
            )

            sourceTurn.attachments.forEach { attachment ->
                val sourceMediaId = (attachment["media_id"] as? String)
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val staged = sourceMediaId?.let(stagedBySourceId::get) ?: return@forEach
                mediaRepository.save(
                    Media(
                        id = staged.newId,
                        ownerUserId = importerId,
                        turnId = turn.id,
                        mediaType = staged.mediaType,
                        mimeType = staged.mimeType,
                        sizeBytes = staged.sizeBytes,
                        storageBackend = staged.backend,
                        storageKey = staged.storageKey,
                        publicUrl = staged.publicUrl,
                        originalFilename = staged.originalFilename,
                    ),
                )
            }
            sourceTurn.responses.forEach { sourceResponse ->
                responseRepository.save(
                    ProviderResponse(
                        turn = turn,
                        modelId = sourceResponse.modelId,
                        configuredModelId = modelIdMap.getOrPut(sourceResponse.configuredModelId) { UUID.randomUUID() },
                        attemptNumber = sourceResponse.attemptNumber,
                        modelDisplayName = sourceResponse.modelDisplayName,
                        protocol = sourceResponse.protocol,
                        connectionLabel = sourceResponse.connectionLabel,
                        connectionId = null,
                        status = sourceResponse.status,
                        responseText = sourceResponse.responseText,
                        reasoningText = sourceResponse.reasoningText,
                        errorMessage = sourceResponse.errorMessage,
                        inputTokens = sourceResponse.inputTokens,
                        outputTokens = sourceResponse.outputTokens,
                        cacheReadTokens = sourceResponse.cacheReadTokens,
                        cacheWriteTokens = sourceResponse.cacheWriteTokens,
                        latencyMs = sourceResponse.latencyMs,
                        inputPricePerMtok = sourceResponse.inputPricePerMtok,
                        outputPricePerMtok = sourceResponse.outputPricePerMtok,
                        priceCurrency = sourceResponse.priceCurrency,
                        createdAt = sourceResponse.createdAt,
                    ),
                )
            }
        }
        return SharedQuestImportResult(session.id, session.title)
    }
}
