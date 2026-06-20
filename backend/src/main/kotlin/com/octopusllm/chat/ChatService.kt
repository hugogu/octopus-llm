package com.octopusllm.chat

import com.octopusllm.admin.StorageSettingsService
import com.octopusllm.auth.UserRepository
import com.octopusllm.config.DuplicateRequestException
import com.octopusllm.connection.ConfiguredModel
import com.octopusllm.connection.ConfiguredModelService
import com.octopusllm.connection.ConnectionService
import com.octopusllm.llm.Attachment
import com.octopusllm.llm.ConcurrentLlmOrchestrator
import com.octopusllm.llm.HistoryTurn
import com.octopusllm.llm.LlmRequest
import com.octopusllm.llm.LlmStreamEvent
import com.octopusllm.llm.ModelDispatchTarget
import com.octopusllm.media.MediaRepository
import com.octopusllm.media.MediaStorageFactory
import com.octopusllm.migration.MediaToStage
import com.octopusllm.migration.MigrationMediaStagingService
import com.octopusllm.migration.MigrationOperation
import com.octopusllm.migration.MigrationOperationService
import com.octopusllm.model.ProtocolDefinitions
import com.octopusllm.share.ShareService
import java.util.Base64
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class ChatService(
    private val sessionRepository: ChatSessionRepository,
    private val turnRepository: ChatTurnRepository,
    private val responseRepository: ProviderResponseRepository,
    private val userRepository: UserRepository,
    private val configuredModelService: ConfiguredModelService,
    private val connectionService: ConnectionService,
    private val orchestrator: ConcurrentLlmOrchestrator,
    private val mediaRepository: MediaRepository,
    private val storageSettingsService: StorageSettingsService,
    private val mediaStorageFactory: MediaStorageFactory,
    private val dialogRedactionService: DialogRedactionService,
    private val shareService: ShareService,
    private val migrationOperationService: MigrationOperationService,
    private val migrationMediaStagingService: MigrationMediaStagingService,
    private val sharedQuestImportTxOps: SharedQuestImportTxOps,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun createSession(userId: UUID, title: String?): Mono<ChatSession> =
        blocking {
            val user = userRepository.findById(userId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
            }
            sessionRepository.save(ChatSession(user = user, title = title))
        }

    data class SharedImportOutcome(
        val operationId: UUID,
        val status: String,
        val result: SharedQuestImportResult? = null,
        val replay: Boolean = false,
    )

    fun importFromShare(token: String, callerId: UUID, idempotencyKey: String): Mono<SharedImportOutcome> =
        blocking {
            val sourceDigest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
            val claim = migrationOperationService.claimImport(
                callerId,
                MigrationOperation.TYPE_SHARE_IMPORT,
                idempotencyKey,
                sourceDigest,
            )
            if (!claim.isNew && claim.operation.status == MigrationOperation.STATUS_SUCCEEDED) {
                return@blocking SharedImportOutcome(
                    operationId = claim.operation.id,
                    status = MigrationOperation.STATUS_SUCCEEDED,
                    result = SharedQuestImportResult.fromResultMap(claim.operation.result),
                    replay = true,
                )
            }
            if (!claim.isNew && claim.operation.status == MigrationOperation.STATUS_IN_PROGRESS) {
                return@blocking SharedImportOutcome(
                    operationId = claim.operation.id,
                    status = MigrationOperation.STATUS_IN_PROGRESS,
                )
            }

            val operationId = claim.operation.id
            var staged = emptyList<com.octopusllm.migration.StagedMedia>()
            try {
                val source = shareService.importSource(token, callerId)
                staged = migrationMediaStagingService.stage(
                    operationId,
                    source.media.map {
                        MediaToStage(
                            sourceMediaId = it.sourceMediaId,
                            mediaType = it.mediaType,
                            mimeType = it.mimeType,
                            sizeBytes = it.sizeBytes,
                            originalFilename = it.originalFilename,
                            content = it.content,
                        )
                    },
                )
                val result = sharedQuestImportTxOps.commit(source, staged, callerId)
                migrationMediaStagingService.complete(operationId)
                migrationOperationService.succeed(claim.operation, result.toResultMap())
                SharedImportOutcome(
                    operationId = operationId,
                    status = MigrationOperation.STATUS_SUCCEEDED,
                    result = result,
                )
            } catch (error: Throwable) {
                migrationMediaStagingService.compensate(operationId, staged)
                migrationOperationService.fail(claim.operation)
                throw error
            }
        }

    fun listSessions(userId: UUID, limit: Int, offset: Int): Mono<Pair<List<ChatSession>, Long>> =
        blocking {
            if (limit !in 1..100 || offset < 0) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination")
            }
            val page = sessionRepository.findByUserIdOrderByCreatedAtDesc(
                userId,
                PageRequest.of(offset / limit, limit),
            )
            page.content to page.totalElements
        }

    fun getSession(
        sessionId: UUID,
        userId: UUID,
    ): Mono<Pair<ChatSession, List<Pair<ChatTurn, List<ProviderResponse>>>>> =
        blocking {
            val session = requireSession(sessionId, userId)
            val turns = turnRepository.findBySessionIdOrderBySequenceNum(sessionId)
            // Feature 008: hide redacted Dialogs (whole turns and individual responses) from the
            // owner's view, without touching the immutable rows.
            val redactions = dialogRedactionService.forTurns(turns.map { it.id })
            val visible = turns
                .filterNot { redactions.isTurnRedacted(it.id) }
                .map { turn -> turn to latestResponses(turn).filterNot { redactions.isResponseRedacted(it.id) } }
            session to visible
        }

    fun deleteSession(sessionId: UUID, userId: UUID): Mono<Unit> =
        blocking {
            val session = requireSession(sessionId, userId)
            // Delete stored media objects for this session's turns (rows cascade-delete; FR-024).
            turnRepository.findBySessionIdOrderBySequenceNum(sessionId).forEach { turn ->
                mediaRepository.findByTurnId(turn.id).forEach { media ->
                    runCatching { mediaStorageFactory.resolveByBackend(media.storageBackend)?.delete(media.storageKey) }
                }
            }
            sessionRepository.delete(session)
            Unit
        }

    fun redactTurn(sessionId: UUID, turnId: UUID, callerId: UUID): Mono<Unit> =
        blocking {
            requireSessionForDialogMutation(sessionId, callerId)
            val turn = turnRepository.findById(turnId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Turn not found")
            }
            if (turn.session.id != sessionId) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "Turn not found")
            }
            dialogRedactionService.redactTurn(turnId, callerId)
            Unit
        }

    fun redactResponse(sessionId: UUID, turnId: UUID, responseId: UUID, callerId: UUID): Mono<Unit> =
        blocking {
            requireSessionForDialogMutation(sessionId, callerId)
            val turn = turnRepository.findById(turnId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Turn not found")
            }
            if (turn.session.id != sessionId) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "Turn not found")
            }
            val response = responseRepository.findById(responseId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Response not found")
            }
            if (response.turn.id != turnId) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "Response not found")
            }
            dialogRedactionService.redactResponse(turnId, responseId, callerId)
            Unit
        }

    fun submitTurn(
        sessionId: UUID,
        userId: UUID,
        promptText: String,
        selectedConfiguredModelIds: List<UUID>,
        attachments: List<Map<String, String>>,
        clientRequestId: String?,
        clientIp: String?,
    ): Flux<LlmStreamEvent> {
        val setup: Mono<TurnSetup> =
            blocking {
                if (clientRequestId != null) {
                    turnRepository.findByClientRequestId(clientRequestId)?.let {
                        throw DuplicateRequestException(it.id.toString())
                    }
                }
                val session = requireSession(sessionId, userId)
                val models = configuredModelService.requireSelectable(userId, selectedConfiguredModelIds)
                if (models.any { !it.isEnabled }) {
                    throw ResponseStatusException(HttpStatus.FORBIDDEN, "One or more configured models are disabled")
                }

                // Resolve and validate media references (feature 007). The client sends opaque
                // media ids (+ order); type/mime/size/url come authoritatively from the media rows.
                val orderedRefs = attachments.mapNotNull { ref ->
                    ref["media_id"]?.let { id -> runCatching { UUID.fromString(id) }.getOrNull()?.to(ref["order"]?.toIntOrNull() ?: 0) }
                }
                val mediaById = resolveMedia(orderedRefs.map { it.first }, userId)
                val attachedTypes = mediaById.values.map { it.mediaType }.toSet()

                // Capability gating: a model is capable only if it accepts every attached media type.
                val capable = models.filter { model -> attachedTypes.all { it in modalitiesOf(model) } }
                val excluded = models.filterNot { it in capable }
                if (attachedTypes.isNotEmpty() && capable.isEmpty()) {
                    throw ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "No selected model can accept the attached media (${attachedTypes.joinToString(", ")})",
                    )
                }

                val turnRefs = orderedRefs.mapNotNull { (id, order) ->
                    mediaById[id]?.let { m ->
                        mapOf<String, Any?>(
                            "media_id" to m.id.toString(),
                            "media_type" to m.mediaType,
                            "mime_type" to m.mimeType,
                            "size_bytes" to m.sizeBytes,
                            "url" to m.publicUrl,
                            "order" to order,
                        )
                    }
                }.sortedBy { it["order"] as Int }

                val sequence = turnRepository.countBySessionId(sessionId).toInt() + 1
                val turn = turnRepository.save(
                    ChatTurn(
                        session = session,
                        sequenceNum = sequence,
                        promptText = promptText,
                        attachments = turnRefs.ifEmpty { null },
                        // Record only the models actually dispatched; excluded models surface as notices.
                        selectedModelIds = capable.map { it.modelId }.toTypedArray(),
                        selectedConfiguredModelIds = capable.map { it.id }.toTypedArray(),
                        clientRequestId = clientRequestId,
                        clientIp = clientIp,
                    ),
                )

                // Bind the media to this turn (immutable from here; orphan sweep no longer touches it).
                if (mediaById.isNotEmpty()) {
                    mediaById.values.forEach { it.turnId = turn.id }
                    mediaRepository.saveAll(mediaById.values)
                }

                if (session.title == null) session.title = promptText.trim().take(60)
                session.updatedAt = Instant.now()
                sessionRepository.save(session)

                val targets = capable.map(::dispatchTarget)
                val request = requestForTurn(sessionId, turn)
                val excludedNotices = excluded.map { model ->
                    LlmStreamEvent.CapabilityNotice(
                        modelId = model.modelId,
                        notice = "${model.displayName} does not support ${attachedTypes.joinToString(", ")} input",
                        configuredModelId = model.id,
                    )
                }
                if (excluded.isNotEmpty()) {
                    log.info(
                        "media_capability_excluded user={} types={} excludedModels={}",
                        userId.toString().take(8), attachedTypes, excluded.map { it.modelId },
                    )
                }
                TurnSetup(turn, targets, request, excludedNotices)
            }

        return setup.flatMapMany { setupResult ->
            val attempts = setupResult.targets.associate { it.configuredModelId to ResponseAttempt(1, null) }
            streamResponses(
                setupResult.turn,
                userId,
                setupResult.targets,
                setupResult.request,
                attempts,
                includeTurnCreated = true,
                leadingNotices = setupResult.excludedNotices,
            )
        }
    }

    private data class TurnSetup(
        val turn: ChatTurn,
        val targets: List<ModelDispatchTarget>,
        val request: LlmRequest,
        val excludedNotices: List<LlmStreamEvent.CapabilityNotice>,
    )

    /** Loads owned, orphaned media by id; enforces per-prompt ceiling and that none is already bound. */
    private fun resolveMedia(ids: List<UUID>, userId: UUID): Map<UUID, com.octopusllm.media.Media> {
        if (ids.isEmpty()) return emptyMap()
        val distinct = ids.toSet()
        val found = mediaRepository.findAllByIdInAndOwnerUserId(distinct, userId)
        if (found.size != distinct.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown or unauthorized media reference")
        }
        found.firstOrNull { it.turnId != null }?.let {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Media is already attached to a turn")
        }
        val settings = storageSettingsService.get()
        if (found.size > settings.maxFilesPerPrompt) {
            throw ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "At most ${settings.maxFilesPerPrompt} attachments per message",
            )
        }
        val total = found.sumOf { it.sizeBytes }
        if (total > settings.maxTotalBytesPerPrompt) {
            throw ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Attachments exceed the ${settings.maxTotalBytesPerPrompt}-byte per-message limit",
            )
        }
        return found.associateBy { it.id }
    }

    private fun modalitiesOf(model: ConfiguredModel): Set<String> {
        val protocol = ProtocolDefinitions.require(model.connection.protocol)
        return ProtocolDefinitions.mergeCapabilities(protocol.baseline, model.capabilityOverrides)
            .inputModalities.toSet()
    }

    fun retryModel(
        sessionId: UUID,
        turnId: UUID,
        configuredModelId: UUID,
        userId: UUID,
        clientRequestId: String,
    ): Flux<LlmStreamEvent> {
        data class RetrySetup(
            val turn: ChatTurn,
            val target: ModelDispatchTarget?,
            val request: LlmRequest?,
            val attempt: ResponseAttempt,
        )

        val setup = blocking {
            requireSession(sessionId, userId)
            val turn = turnRepository.findById(turnId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Turn not found")
            }
            if (turn.session.id != sessionId || turn.session.user.id != userId) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "Turn not found")
            }

            responseRepository.findByRetryRequestId(clientRequestId)?.let { existing ->
                if (
                    existing.turn.id != turnId ||
                    existing.configuredModelId != configuredModelId ||
                    existing.turn.session.user.id != userId
                ) {
                    throw DuplicateRequestException(existing.id.toString())
                }
                return@blocking RetrySetup(
                    turn,
                    null,
                    null,
                    ResponseAttempt(existing.attemptNumber, clientRequestId, existing),
                )
            }

            val latest = responseRepository
                .findFirstByTurnIdAndConfiguredModelIdOrderByAttemptNumberDesc(turnId, configuredModelId)
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "This model has not failed")
            if (latest.status != "error") {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Only failed model responses can be retried")
            }

            val model = configuredModelService.requireSelectable(userId, listOf(configuredModelId)).single()
            if (!model.isEnabled) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "Configured model is disabled")
            }
            RetrySetup(
                turn = turn,
                target = dispatchTarget(model),
                request = requestForTurn(sessionId, turn),
                attempt = ResponseAttempt(latest.attemptNumber + 1, clientRequestId),
            )
        }

        return setup.flatMapMany { retry ->
            retry.attempt.existing?.let(::replayResponse)
                ?: streamResponses(
                    turn = retry.turn,
                    userId = userId,
                    targets = listOf(requireNotNull(retry.target)),
                    request = requireNotNull(retry.request),
                    attempts = mapOf(configuredModelId to retry.attempt),
                    includeTurnCreated = false,
                )
        }
    }

    private data class ResponseAttempt(
        val number: Int,
        val retryRequestId: String?,
        val existing: ProviderResponse? = null,
    )

    private fun streamResponses(
        turn: ChatTurn,
        userId: UUID,
        targets: List<ModelDispatchTarget>,
        request: LlmRequest,
        attempts: Map<UUID, ResponseAttempt>,
        includeTurnCreated: Boolean,
        leadingNotices: List<LlmStreamEvent> = emptyList(),
    ): Flux<LlmStreamEvent> {
        val textBuffers = ConcurrentHashMap<UUID, StringBuilder>()
        val reasoningBuffers = ConcurrentHashMap<UUID, StringBuilder>()
        val startTimes = ConcurrentHashMap<UUID, Long>()

        val targetById = targets.associateBy { it.configuredModelId }
        targets.forEach {
            textBuffers[it.configuredModelId] = StringBuilder()
            reasoningBuffers[it.configuredModelId] = StringBuilder()
            startTimes[it.configuredModelId] = System.currentTimeMillis()
        }

        val modelEvents = orchestrator.stream(targets, request)
            .flatMap<LlmStreamEvent> { event ->
                val configuredModelId = configuredModelId(event) ?: return@flatMap Mono.just(event)
                val target = targetById[configuredModelId] ?: return@flatMap Mono.just(event)
                val attempt = attempts.getValue(configuredModelId)
                when (event) {
                    is LlmStreamEvent.Token -> {
                        textBuffers[configuredModelId]?.append(event.delta)
                        Mono.just(event)
                    }
                    is LlmStreamEvent.Reasoning -> {
                        reasoningBuffers[configuredModelId]?.append(event.delta)
                        Mono.just(event)
                    }
                    is LlmStreamEvent.ModelComplete -> persistResponse(
                        ProviderResponse(
                            turn = turn,
                            configuredModelId = configuredModelId,
                            attemptNumber = attempt.number,
                            retryRequestId = attempt.retryRequestId,
                            modelId = target.modelId,
                            modelDisplayName = target.displayName,
                            protocol = target.protocol,
                            connectionLabel = target.connectionLabel,
                            connectionId = target.connectionId,
                            status = "complete",
                            responseText = textBuffers[configuredModelId]?.toString(),
                            reasoningText = reasoningBuffers[configuredModelId]?.toString()?.ifBlank { null },
                            inputTokens = event.inputTokens,
                            outputTokens = event.outputTokens,
                            cacheReadTokens = event.cacheReadTokens,
                            cacheWriteTokens = event.cacheWriteTokens,
                            latencyMs = event.latencyMs.toInt(),
                            inputPricePerMtok = target.inputPricePerMtok,
                            outputPricePerMtok = target.outputPricePerMtok,
                            priceCurrency = target.priceCurrency,
                        ),
                        userId,
                    ).map { saved -> event.copy(responseId = saved.id) }
                    is LlmStreamEvent.ModelError -> persistResponse(
                        ProviderResponse(
                            turn = turn,
                            configuredModelId = configuredModelId,
                            attemptNumber = attempt.number,
                            retryRequestId = attempt.retryRequestId,
                            modelId = target.modelId,
                            modelDisplayName = target.displayName,
                            protocol = target.protocol,
                            connectionLabel = target.connectionLabel,
                            connectionId = target.connectionId,
                            status = "error",
                            errorMessage = event.error,
                            latencyMs = (
                                System.currentTimeMillis() -
                                    (startTimes[configuredModelId] ?: System.currentTimeMillis())
                                ).toInt(),
                            inputPricePerMtok = target.inputPricePerMtok,
                            outputPricePerMtok = target.outputPricePerMtok,
                            priceCurrency = target.priceCurrency,
                        ),
                        userId,
                    ).map { saved -> event.copy(responseId = saved.id) }
                    is LlmStreamEvent.CapabilityNotice -> Mono.just(event)
                }
            }

        if (!includeTurnCreated) return modelEvents
        val turnCreated = LlmStreamEvent.CapabilityNotice(
            modelId = "__system__",
            notice = """{"event":"turn_created","turnId":"${turn.id}","sequenceNum":${turn.sequenceNum}}""",
        )
        return Flux.concat(Flux.just(turnCreated), Flux.fromIterable(leadingNotices), modelEvents)
    }

    private fun replayResponse(response: ProviderResponse): Flux<LlmStreamEvent> {
        val events = mutableListOf<LlmStreamEvent>()
        response.reasoningText?.takeIf { it.isNotEmpty() }?.let {
            events.add(LlmStreamEvent.Reasoning(response.modelId, it, response.configuredModelId))
        }
        response.responseText?.takeIf { it.isNotEmpty() }?.let {
            events.add(LlmStreamEvent.Token(response.modelId, it, response.configuredModelId))
        }
        events.add(
            if (response.status == "complete") {
                LlmStreamEvent.ModelComplete(
                    modelId = response.modelId,
                    inputTokens = response.inputTokens,
                    outputTokens = response.outputTokens,
                    latencyMs = response.latencyMs.toLong(),
                    cacheReadTokens = response.cacheReadTokens,
                    cacheWriteTokens = response.cacheWriteTokens,
                    configuredModelId = response.configuredModelId,
                    responseId = response.id,
                )
            } else {
                LlmStreamEvent.ModelError(
                    modelId = response.modelId,
                    error = response.errorMessage ?: "Unknown error",
                    configuredModelId = response.configuredModelId,
                    responseId = response.id,
                )
            },
        )
        return Flux.fromIterable(events)
    }

    private fun dispatchTarget(model: com.octopusllm.connection.ConfiguredModel): ModelDispatchTarget {
        val connection = model.connection
        val protocol = ProtocolDefinitions.require(connection.protocol)
        return ModelDispatchTarget(
            configuredModelId = model.id,
            modelId = model.modelId,
            protocol = connection.protocol,
            decryptedApiKey = connectionService.decryptAndValidate(connection),
            capabilityMatrix = ProtocolDefinitions.mergeCapabilities(
                protocol.baseline,
                model.capabilityOverrides,
            ),
            customParams = model.customParams,
            baseUrl = connection.baseUrl,
            displayName = model.displayName,
            connectionLabel = connection.label,
            connectionId = connection.id,
            inputPricePerMtok = model.inputPricePerMtok,
            outputPricePerMtok = model.outputPricePerMtok,
            priceCurrency = model.priceCurrency,
        )
    }

    private fun requestForTurn(sessionId: UUID, turn: ChatTurn): LlmRequest {
        val priorTurns = turnRepository.findBySessionIdOrderBySequenceNum(sessionId)
            .filter { it.sequenceNum < turn.sequenceNum }
        val history = priorTurns.flatMap { prior ->
            listOf(HistoryTurn(role = "user", text = prior.promptText)) +
                latestResponses(prior)
                    .filter { it.status == "complete" && it.responseText != null }
                    .map { HistoryTurn(role = "assistant", text = requireNotNull(it.responseText)) }
        }
        return LlmRequest(
            prompt = turn.promptText,
            history = history,
            // Supports both the feature-007 media-reference shape (media_type/mime_type/url) and the
            // legacy inline-base64 shape (type/data/mimeType). Media is only on the current turn.
            attachments = turn.attachments.orEmpty().mapNotNull { ref ->
                val type = (ref["media_type"] ?: ref["type"]) as? String ?: return@mapNotNull null
                Attachment(
                    type = type,
                    // Audio is inlined as base64 (providers' input_audio needs bytes, not a URL);
                    // image/video are referenced by their public URL.
                    data = if (type == "audio") inlineBase64(ref) else (ref["data"] as? String).orEmpty(),
                    mimeType = (ref["mime_type"] ?: ref["mimeType"]) as? String ?: "",
                    url = ref["url"] as? String,
                )
            },
        )
    }

    private fun inlineBase64(ref: Map<String, Any?>): String {
        val id = (ref["media_id"] as? String)?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return ""
        val media = mediaRepository.findById(id).orElse(null) ?: return ""
        val bytes = mediaStorageFactory.resolveByBackend(media.storageBackend)?.read(media.storageKey) ?: return ""
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun latestResponses(turn: ChatTurn): List<ProviderResponse> {
        return latestProviderResponses(turn, responseRepository.findByTurnId(turn.id))
    }

    private fun persistResponse(response: ProviderResponse, userId: UUID): Mono<ProviderResponse> =
        Mono.fromCallable { responseRepository.save(response) }
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess {
                log.info(
                    "llm_call user={} protocol={} configuredModelId={} modelId={} latencyMs={} inputTokens={} outputTokens={} status={}",
                    userId.toString().take(8),
                    response.protocol,
                    response.configuredModelId,
                    response.modelId,
                    response.latencyMs,
                    response.inputTokens,
                    response.outputTokens,
                    response.status,
                )
            }

    private fun configuredModelId(event: LlmStreamEvent): UUID? = when (event) {
        is LlmStreamEvent.Token -> event.configuredModelId
        is LlmStreamEvent.Reasoning -> event.configuredModelId
        is LlmStreamEvent.ModelComplete -> event.configuredModelId
        is LlmStreamEvent.ModelError -> event.configuredModelId
        is LlmStreamEvent.CapabilityNotice -> event.configuredModelId
    }

    private fun requireSession(sessionId: UUID, userId: UUID): ChatSession {
        val session = sessionRepository.findById(sessionId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found")
        }
        if (session.user.id != userId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found")
        }
        return session
    }

    private fun requireSessionForDialogMutation(sessionId: UUID, callerId: UUID): ChatSession {
        val session = sessionRepository.findById(sessionId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found")
        }
        if (session.user.id == callerId) return session
        val caller = userRepository.findById(callerId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found")
        }
        if (!caller.isAdmin) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Dialog deletion is owner or admin only")
        }
        return session
    }

    private fun <T> blocking(block: () -> T): Mono<T> =
        Mono.fromCallable(block).subscribeOn(Schedulers.boundedElastic())
}
