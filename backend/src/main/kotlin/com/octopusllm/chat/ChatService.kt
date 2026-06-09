package com.octopusllm.chat

import com.octopusllm.auth.UserRepository
import com.octopusllm.config.DuplicateRequestException
import com.octopusllm.llm.*
import com.octopusllm.userconfig.ApiKeyEncryptionService
import com.octopusllm.userconfig.UserModelConfigRepository
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
    private val modelConfigRepository: UserModelConfigRepository,
    private val encryptionService: ApiKeyEncryptionService,
    private val orchestrator: ConcurrentLlmOrchestrator,
) {

    fun createSession(userId: UUID, title: String?): Mono<ChatSession> =
        Mono.fromCallable {
            val user = userRepository.findById(userId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
            }
            sessionRepository.save(ChatSession(user = user, title = title))
        }.subscribeOn(Schedulers.boundedElastic())

    fun listSessions(userId: UUID, limit: Int, offset: Int): Mono<Pair<List<ChatSession>, Long>> =
        Mono.fromCallable {
            val page = sessionRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(offset / limit, limit))
            page.content to page.totalElements
        }.subscribeOn(Schedulers.boundedElastic())

    fun getSession(sessionId: UUID, userId: UUID): Mono<Pair<ChatSession, List<Pair<ChatTurn, List<ProviderResponse>>>>> =
        Mono.fromCallable {
            val session = sessionRepository.findById(sessionId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found")
            }
            if (session.user.id != userId) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found")
            val turns = turnRepository.findBySessionIdOrderBySequenceNum(sessionId)
            val turnsWithResponses = turns.map { turn ->
                turn to responseRepository.findByTurnId(turn.id)
            }
            session to turnsWithResponses
        }.subscribeOn(Schedulers.boundedElastic())

    fun submitTurn(
        sessionId: UUID,
        userId: UUID,
        promptText: String,
        selectedModelIds: List<String>,
        attachments: List<Map<String, String>>,
        clientRequestId: String?,
    ): Flux<LlmStreamEvent> {
        // (0) Idempotency check
        val idempotencyCheck: Mono<Void> = if (clientRequestId != null) {
            Mono.fromCallable {
                turnRepository.findByClientRequestId(clientRequestId)
            }.subscribeOn(Schedulers.boundedElastic()).flatMap { existing ->
                if (existing != null) Mono.error(DuplicateRequestException(existing.id.toString()))
                else Mono.empty()
            }
        } else {
            Mono.empty()
        }

        // Persist turn + start streaming
        val setupMono: Mono<Triple<ChatTurn, List<ModelDispatchTarget>, LlmRequest>> =
            idempotencyCheck.then(
                Mono.fromCallable {
                    // (1) Validate session ownership
                    val session = sessionRepository.findById(sessionId).orElseThrow {
                        ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found")
                    }
                    if (session.user.id != userId) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found")

                    // (2) Persist ChatTurn
                    val seqNum = turnRepository.countBySessionId(sessionId).toInt() + 1
                    val turn = ChatTurn(
                        session = session,
                        sequenceNum = seqNum,
                        promptText = promptText,
                        attachments = attachments.ifEmpty { null },
                        selectedModelIds = selectedModelIds.toTypedArray(),
                        clientRequestId = clientRequestId,
                    )
                    turnRepository.save(turn)

                    // (3) Decrypt API keys and build dispatch targets
                    val modelConfigs = modelConfigRepository.findByUserId(userId)
                        .filter { config ->
                            config.isEnabled &&
                                config.providerApiKey != null &&
                                config.model.id in selectedModelIds
                        }
                    if (modelConfigs.size != selectedModelIds.size) {
                        val missing = selectedModelIds - modelConfigs.map { it.model.id }.toSet()
                        throw ResponseStatusException(HttpStatus.FORBIDDEN, "Models not enabled for user: $missing")
                    }

                    val targets = modelConfigs.map { config ->
                        val key = config.providerApiKey!!
                        val decryptedKey = encryptionService.decrypt(key.encryptedKey, key.keyIv)
                        ModelDispatchTarget(
                            modelId = config.model.id,
                            providerId = config.model.providerId,
                            decryptedApiKey = decryptedKey,
                            capabilityMatrix = config.model.capabilityMatrix,
                        )
                    }

                    // (4) Build LlmRequest with prior turns as history
                    val priorTurns = turnRepository.findBySessionIdOrderBySequenceNum(sessionId)
                        .dropLast(1) // exclude the turn we just saved
                    val history = priorTurns.flatMap { prior ->
                        val responses = responseRepository.findByTurnId(prior.id)
                        listOf(
                            HistoryTurn(role = "user", text = prior.promptText),
                        ) + responses.filter { it.status == "complete" && it.responseText != null }
                            .map { r -> HistoryTurn(role = "assistant", text = r.responseText!!) }
                    }
                    val llmAttachments = attachments.map { m ->
                        Attachment(
                            type = m["type"] ?: "",
                            data = m["data"] ?: "",
                            mimeType = m["mime_type"] ?: "",
                        )
                    }
                    val llmRequest = LlmRequest(prompt = promptText, history = history, attachments = llmAttachments)

                    Triple(turn, targets, llmRequest)
                }.subscribeOn(Schedulers.boundedElastic())
            )

        // Accumulate tokens in memory; INSERT ProviderResponse on terminal event
        val tokenBuffers = ConcurrentHashMap<String, StringBuilder>()
        val startTimes = ConcurrentHashMap<String, Long>()
        val startMs = System.currentTimeMillis()

        return setupMono.flatMapMany { (turn, targets, llmRequest) ->
            targets.forEach { t ->
                tokenBuffers[t.modelId] = StringBuilder()
                startTimes[t.modelId] = System.currentTimeMillis()
            }

            val turnCreatedEvent = LlmStreamEvent.CapabilityNotice(
                "__system__",
                """{"event":"turn_created","turnId":"${turn.id}","sequenceNum":${turn.sequenceNum}}""",
            )

            // We use CapabilityNotice with __system__ as a sentinel to carry turn_created JSON
            val llmFlux = orchestrator.stream(targets, llmRequest)
                .doOnNext { event ->
                    when (event) {
                        is LlmStreamEvent.Token -> tokenBuffers[event.modelId]?.append(event.delta)
                        is LlmStreamEvent.ModelComplete -> {
                            Mono.fromCallable {
                                responseRepository.save(
                                    ProviderResponse(
                                        turn = turn,
                                        modelId = event.modelId,
                                        status = "complete",
                                        responseText = tokenBuffers[event.modelId]?.toString(),
                                        inputTokens = event.inputTokens,
                                        outputTokens = event.outputTokens,
                                        latencyMs = event.latencyMs.toInt(),
                                    ),
                                )
                            }.subscribeOn(Schedulers.boundedElastic()).subscribe()
                        }
                        is LlmStreamEvent.ModelError -> {
                            Mono.fromCallable {
                                responseRepository.save(
                                    ProviderResponse(
                                        turn = turn,
                                        modelId = event.modelId,
                                        status = "error",
                                        errorMessage = event.error,
                                        latencyMs = (System.currentTimeMillis() - (startTimes[event.modelId] ?: startMs)).toInt(),
                                    ),
                                )
                            }.subscribeOn(Schedulers.boundedElastic()).subscribe()
                        }
                        else -> {}
                    }
                }

            Flux.concat(Flux.just(turnCreatedEvent), llmFlux)
        }
    }
}
