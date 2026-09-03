package com.octopusllm.anonymous

import com.octopusllm.chat.LlmTurnRunner
import com.octopusllm.chat.TimeContext
import com.octopusllm.connection.ConfiguredModel
import com.octopusllm.connection.ConfiguredModelRepository
import com.octopusllm.llm.HistoryTurn
import com.octopusllm.llm.LlmRequest
import com.octopusllm.llm.LlmStreamEvent
import com.octopusllm.model.ProtocolDefinitions
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeoutException

data class AnonymousModelView(
    val id: UUID,
    val modelId: String,
    val displayName: String,
    val protocol: String,
    val capabilities: Map<String, Any?>,
)

data class AnonymousHistoryInput(
    val role: String,
    val content: String,
)

data class AnonymousTurnInput(
    val clientConversationId: UUID,
    val clientRequestId: UUID,
    val promptText: String,
    val selectedConfiguredModelIds: List<UUID>,
    val history: List<AnonymousHistoryInput> = emptyList(),
    val attachments: List<Any>? = null,
    val tools: List<Any>? = null,
)

data class PreparedAnonymousTurn(
    val lease: AnonymousLease,
    val models: List<ConfiguredModel>,
    val targets: List<com.octopusllm.llm.ModelDispatchTarget>,
    val request: LlmRequest,
)

@Service
class AnonymousChatService(
    private val configuredModelRepository: ConfiguredModelRepository,
    private val runner: LlmTurnRunner,
    private val throttleService: AnonymousThrottleService,
    private val timeContext: TimeContext,
) {
    fun listModels(page: Int, size: Int): Mono<Page<AnonymousModelView>> =
        Mono.fromCallable {
            val pageable = anonymousPageRequest(page, size)
            configuredModelRepository.findAnonymousAllowed(pageable).map(::modelView)
        }.subscribeOn(Schedulers.boundedElastic())

    fun prepare(input: AnonymousTurnInput, clientIp: String?): Mono<PreparedAnonymousTurn> {
        val selectedIds = input.selectedConfiguredModelIds.distinct()
        val historyBytes = input.history.sumOf { it.role.length + it.content.toByteArray(Charsets.UTF_8).size }
        return throttleService.acquire(
            clientIp = clientIp,
            prompt = input.promptText,
            historyBytes = historyBytes,
            historyTurns = input.history.size,
            modelCount = selectedIds.size,
        ).flatMap { lease ->
            Mono.fromCallable {
                if (input.attachments.orEmpty().isNotEmpty() || input.tools.orEmpty().isNotEmpty()) {
                    throw badRequest("Attachments and tools are not available in anonymous chat")
                }
                val invalidHistory = input.history.firstOrNull {
                    it.role.uppercase() !in setOf("USER", "ASSISTANT") || it.content.isBlank()
                }
                if (invalidHistory != null) throw badRequest("Only user and assistant history is supported")
                if (selectedIds.isEmpty()) throw badRequest("At least one model is required")

                val models = configuredModelRepository.findAnonymousEligibleByIds(selectedIds)
                    .sortedBy { selectedIds.indexOf(it.id) }
                if (models.size != selectedIds.size) {
                    throw ResponseStatusException(HttpStatus.FORBIDDEN, "One or more selected models are unavailable")
                }
                val request = LlmRequest(
                    prompt = input.promptText,
                    history = input.history.map { HistoryTurn(it.role.lowercase(), it.content) },
                    systemPrompt = timeContext.systemPrompt(),
                    attachments = emptyList(),
                    tools = emptyList(),
                )
                PreparedAnonymousTurn(lease, models, runner.targetsFor(models), request)
            }.subscribeOn(Schedulers.boundedElastic())
                .onErrorResume { error -> lease.release().then(Mono.error(error)) }
        }
    }

    fun streamPrepared(prepared: PreparedAnonymousTurn): Flux<LlmStreamEvent> =
        runner.stream(prepared.targets, prepared.request)
            .timeout(Duration.ofSeconds(throttleService.executionTimeoutSeconds.coerceAtLeast(1)))
            .onErrorResume { error ->
                val message = if (error is TimeoutException) {
                    "The model did not finish in time"
                } else {
                    "The model could not complete this request"
                }
                Flux.fromIterable(
                    prepared.models.map { model ->
                        LlmStreamEvent.ModelError(
                            modelId = model.modelId,
                            error = message,
                            configuredModelId = model.id,
                        )
                    },
                )
            }
            .doFinally { prepared.lease.release().subscribe() }

    private fun modelView(model: ConfiguredModel): AnonymousModelView {
        val protocol = ProtocolDefinitions.require(model.connection.protocol)
        val capabilities = ProtocolDefinitions.mergeCapabilities(protocol.baseline, model.capabilityOverrides)
        return AnonymousModelView(
            id = model.id,
            modelId = model.modelId,
            displayName = model.displayName,
            protocol = protocol.id,
            capabilities = mapOf(
                "streaming" to capabilities.supportsStreaming,
                "vision" to ("image" in capabilities.inputModalities),
                "tools" to capabilities.supportsFunctionCalling,
            ),
        )
    }

    private fun anonymousPageRequest(page: Int, size: Int): PageRequest {
        if (page < 0 || size !in 1..100) throw badRequest("page must be at least 0 and size must be between 1 and 100")
        return PageRequest.of(
            page,
            size,
            Sort.by(
                Sort.Order.desc("isAnonymousDefault"),
                Sort.Order.asc("sortOrder"),
                Sort.Order.asc("createdAt"),
                Sort.Order.asc("id"),
            ),
        )
    }

    private fun badRequest(message: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, message)
}
