package com.octopusllm.chat

import com.octopusllm.auth.UserRepository
import com.octopusllm.config.DuplicateRequestException
import com.octopusllm.connection.ConfiguredModelService
import com.octopusllm.connection.ConnectionService
import com.octopusllm.llm.Attachment
import com.octopusllm.llm.ConcurrentLlmOrchestrator
import com.octopusllm.llm.HistoryTurn
import com.octopusllm.llm.LlmRequest
import com.octopusllm.llm.LlmStreamEvent
import com.octopusllm.llm.ModelDispatchTarget
import com.octopusllm.model.ProtocolDefinitions
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant
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
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun createSession(userId: UUID, title: String?): Mono<ChatSession> =
        blocking {
            val user = userRepository.findById(userId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
            }
            sessionRepository.save(ChatSession(user = user, title = title))
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
            session to turns.map { turn -> turn to responseRepository.findByTurnId(turn.id) }
        }

    fun deleteSession(sessionId: UUID, userId: UUID): Mono<Unit> =
        blocking {
            sessionRepository.delete(requireSession(sessionId, userId))
            Unit
        }

    fun submitTurn(
        sessionId: UUID,
        userId: UUID,
        promptText: String,
        selectedConfiguredModelIds: List<UUID>,
        attachments: List<Map<String, String>>,
        clientRequestId: String?,
    ): Flux<LlmStreamEvent> {
        val setup: Mono<Triple<ChatTurn, List<ModelDispatchTarget>, LlmRequest>> =
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

                val sequence = turnRepository.countBySessionId(sessionId).toInt() + 1
                val turn = turnRepository.save(
                    ChatTurn(
                        session = session,
                        sequenceNum = sequence,
                        promptText = promptText,
                        attachments = attachments.ifEmpty { null },
                        selectedModelIds = models.map { it.modelId }.toTypedArray(),
                        selectedConfiguredModelIds = models.map { it.id }.toTypedArray(),
                        clientRequestId = clientRequestId,
                    ),
                )

                if (session.title == null) session.title = promptText.trim().take(60)
                session.updatedAt = Instant.now()
                sessionRepository.save(session)

                val targets = models.map { model ->
                    val connection = model.connection
                    val protocol = ProtocolDefinitions.require(connection.protocol)
                    ModelDispatchTarget(
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
                    )
                }

                val priorTurns = turnRepository.findBySessionIdOrderBySequenceNum(sessionId).dropLast(1)
                val history = priorTurns.flatMap { prior ->
                    listOf(HistoryTurn(role = "user", text = prior.promptText)) +
                        responseRepository.findByTurnId(prior.id)
                            .filter { it.status == "complete" && it.responseText != null }
                            .map { HistoryTurn(role = "assistant", text = requireNotNull(it.responseText)) }
                }
                val request = LlmRequest(
                    prompt = promptText,
                    history = history,
                    attachments = attachments.map {
                        Attachment(
                            type = it["type"].orEmpty(),
                            data = it["data"].orEmpty(),
                            mimeType = it["mimeType"] ?: it["mime_type"].orEmpty(),
                        )
                    },
                )
                Triple(turn, targets, request)
            }

        val textBuffers = ConcurrentHashMap<UUID, StringBuilder>()
        val reasoningBuffers = ConcurrentHashMap<UUID, StringBuilder>()
        val startTimes = ConcurrentHashMap<UUID, Long>()

        return setup.flatMapMany { (turn, targets, request) ->
            val targetById = targets.associateBy { it.configuredModelId }
            targets.forEach {
                textBuffers[it.configuredModelId] = StringBuilder()
                reasoningBuffers[it.configuredModelId] = StringBuilder()
                startTimes[it.configuredModelId] = System.currentTimeMillis()
            }

            val turnCreated = LlmStreamEvent.CapabilityNotice(
                modelId = "__system__",
                notice = """{"event":"turn_created","turnId":"${turn.id}","sequenceNum":${turn.sequenceNum}}""",
            )

            val modelEvents = orchestrator.stream(targets, request)
                .doOnNext { event ->
                    val configuredModelId = configuredModelId(event) ?: return@doOnNext
                    val target = targetById[configuredModelId] ?: return@doOnNext
                    when (event) {
                        is LlmStreamEvent.Token -> textBuffers[configuredModelId]?.append(event.delta)
                        is LlmStreamEvent.Reasoning -> reasoningBuffers[configuredModelId]?.append(event.delta)
                        is LlmStreamEvent.ModelComplete -> persistResponse(
                            ProviderResponse(
                                turn = turn,
                                configuredModelId = configuredModelId,
                                modelId = target.modelId,
                                modelDisplayName = target.displayName,
                                protocol = target.protocol,
                                connectionLabel = target.connectionLabel,
                                status = "complete",
                                responseText = textBuffers[configuredModelId]?.toString(),
                                reasoningText = reasoningBuffers[configuredModelId]?.toString()?.ifBlank { null },
                                inputTokens = event.inputTokens,
                                outputTokens = event.outputTokens,
                                latencyMs = event.latencyMs.toInt(),
                            ),
                            userId,
                        )
                        is LlmStreamEvent.ModelError -> persistResponse(
                            ProviderResponse(
                                turn = turn,
                                configuredModelId = configuredModelId,
                                modelId = target.modelId,
                                modelDisplayName = target.displayName,
                                protocol = target.protocol,
                                connectionLabel = target.connectionLabel,
                                status = "error",
                                errorMessage = event.error,
                                latencyMs = (
                                    System.currentTimeMillis() -
                                        (startTimes[configuredModelId] ?: System.currentTimeMillis())
                                    ).toInt(),
                            ),
                            userId,
                        )
                        is LlmStreamEvent.CapabilityNotice -> Unit
                    }
                }

            Flux.concat(Flux.just(turnCreated), modelEvents)
        }
    }

    private fun persistResponse(response: ProviderResponse, userId: UUID) {
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
            .subscribe()
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

    private fun <T> blocking(block: () -> T): Mono<T> =
        Mono.fromCallable(block).subscribeOn(Schedulers.boundedElastic())
}
