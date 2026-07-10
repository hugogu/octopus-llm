package com.octopusllm.llm

import com.fasterxml.jackson.databind.ObjectMapper
import com.octopusllm.tool.ToolExecutor
import com.octopusllm.tool.ToolRegistry
import com.octopusllm.tool.ToolResult
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeoutException

data class ModelDispatchTarget(
    val configuredModelId: UUID,
    val modelId: String,
    val protocol: String,
    val decryptedApiKey: String,
    val capabilityMatrix: CapabilityMatrix,
    val customParams: Map<String, Any?> = emptyMap(),
    val baseUrl: String,
    val displayName: String,
    val connectionLabel: String?,
    val connectionId: UUID? = null,
    val inputPricePerMtok: BigDecimal? = null,
    val outputPricePerMtok: BigDecimal? = null,
    val priceCurrency: String? = null,
)

@Component
class ConcurrentLlmOrchestrator(
    private val adapterRegistry: ProtocolAdapterRegistry,
    private val toolExecutor: ToolExecutor,
    private val toolRegistry: ToolRegistry,
    private val objectMapper: ObjectMapper,
) {

    companion object {
        // Max silence between stream events before a model is declared dead.
        // Generous because reasoning models can pause before the first token,
        // but bounded so a hung provider connection surfaces as an error
        // instead of stalling the turn forever.
        private val STREAM_IDLE_TIMEOUT: Duration = Duration.ofSeconds(120)

        // Safety valve on the agentic tool loop (feature 009): bound how many tool-calling rounds a
        // single model may take in one turn before we stop and surface an error.
        private const val MAX_TOOL_ROUNDS = 5
    }

    fun stream(targets: List<ModelDispatchTarget>, request: LlmRequest): Flux<LlmStreamEvent> {
        // One dedup scope per turn, shared across every model so identical tool calls execute once.
        val turnScope = if (request.tools.isNotEmpty()) toolExecutor.newTurnScope() else null
        val perModelFluxes = targets.map { target ->
            val adapter = adapterRegistry.getAdapter(target.protocol)

            // Filter attachments to only those supported by this model's capability matrix
            val supportedAttachments = request.attachments.filter { att ->
                att.type in target.capabilityMatrix.inputModalities
            }
            val droppedAttachments = request.attachments.filter { att ->
                att.type !in target.capabilityMatrix.inputModalities
            }

            // Models that support a system role receive the time context (feature 009) natively; for the
            // rest we fold it into the user prompt so time-awareness still works without a system prompt.
            val supportsSystem = target.capabilityMatrix.supportsSystemPrompt
            val systemPrompt = request.systemPrompt?.takeIf { it.isNotBlank() }
            val routedRequest = request.copy(
                prompt = if (!supportsSystem && systemPrompt != null) {
                    "$systemPrompt\n\n${request.prompt}"
                } else {
                    request.prompt
                },
                systemPrompt = if (supportsSystem) systemPrompt else null,
                attachments = supportedAttachments,
                customParams = target.customParams,
            )

            val noticeFlux: Flux<LlmStreamEvent> = if (droppedAttachments.isNotEmpty()) {
                val types = droppedAttachments.map { it.type }.distinct().joinToString(", ")
                Flux.just(
                    LlmStreamEvent.CapabilityNotice(
                        target.modelId,
                        "$types input not supported - text only sent",
                        target.configuredModelId,
                    ),
                )
            } else {
                Flux.empty()
            }

            val streamFlux = streamTarget(adapter, target, routedRequest, turnScope)
                .timeout(STREAM_IDLE_TIMEOUT)
                .map { event ->
                    when (event) {
                        is LlmStreamEvent.Token -> event.copy(
                            modelId = target.modelId,
                            configuredModelId = target.configuredModelId,
                        )
                        is LlmStreamEvent.Reasoning -> event.copy(
                            modelId = target.modelId,
                            configuredModelId = target.configuredModelId,
                        )
                        is LlmStreamEvent.ModelComplete -> event.copy(
                            modelId = target.modelId,
                            configuredModelId = target.configuredModelId,
                        )
                        is LlmStreamEvent.ModelError -> event.copy(
                            modelId = target.modelId,
                            configuredModelId = target.configuredModelId,
                        )
                        is LlmStreamEvent.CapabilityNotice -> event.copy(
                            modelId = target.modelId,
                            configuredModelId = target.configuredModelId,
                        )
                        is LlmStreamEvent.ToolCall -> event.copy(
                            modelId = target.modelId,
                            configuredModelId = target.configuredModelId,
                        )
                        is LlmStreamEvent.ToolStatus -> event.copy(
                            modelId = target.modelId,
                            configuredModelId = target.configuredModelId,
                        )
                        is LlmStreamEvent.ToolResult -> event.copy(
                            modelId = target.modelId,
                            configuredModelId = target.configuredModelId,
                        )
                    }
                }
                .onErrorResume { e ->
                    val message = if (e is TimeoutException) {
                        "Provider did not respond within ${STREAM_IDLE_TIMEOUT.seconds}s - check provider availability"
                    } else {
                        e.message ?: "Unknown error"
                    }
                    Flux.just(LlmStreamEvent.ModelError(target.modelId, message, target.configuredModelId))
                }
                // Decouple this provider's read + idle-timeout from the shared SSE writer's demand.
                // All models fan into a single HTTP response via Flux.merge; without this buffer a fast
                // model monopolizes downstream demand, so other models' upstream gets no requests, their
                // .timeout() sees no onNext, and healthy providers falsely trip the idle timeout while
                // their output is withheld until the end. Buffering here keeps every model streaming
                // concurrently and makes the timeout measure real provider silence, not writer backlog.
                .onBackpressureBuffer()

            Flux.concat(noticeFlux, streamFlux)
        }

        return Flux.merge(perModelFluxes)
    }

    /**
     * Streams one model. When tool calling is enabled and the model is capable, drives the agentic tool
     * loop (feature 009); otherwise it is a single provider request. Deduplication is per-turn via [scope].
     */
    private fun streamTarget(
        adapter: LlmAdapter,
        target: ModelDispatchTarget,
        request: LlmRequest,
        scope: ToolExecutor.TurnScope?,
    ): Flux<LlmStreamEvent> {
        val toolsEnabled = scope != null &&
            request.tools.isNotEmpty() &&
            target.capabilityMatrix.supportsFunctionCalling
        return if (toolsEnabled) {
            toolRound(adapter, target, request, scope!!, 0)
        } else {
            adapter.stream(target.modelId, request, target.decryptedApiKey, target.baseUrl)
        }
    }

    /**
     * One round of the tool loop: stream the model, passing text through live while collecting any tool
     * calls. If the round produced tool calls, suppress its (intermediate) completion, execute the tools,
     * feed the results back, and recurse; otherwise the round's completion is the terminal event.
     */
    private fun toolRound(
        adapter: LlmAdapter,
        target: ModelDispatchTarget,
        request: LlmRequest,
        scope: ToolExecutor.TurnScope,
        depth: Int,
    ): Flux<LlmStreamEvent> {
        val toolCalls = CopyOnWriteArrayList<LlmStreamEvent.ToolCall>()
        val body = adapter.stream(target.modelId, request, target.decryptedApiKey, target.baseUrl)
            .concatMap<LlmStreamEvent> { event ->
                when (event) {
                    is LlmStreamEvent.ToolCall -> {
                        toolCalls.add(event)
                        Flux.just(event) // the client still sees the tool_call
                    }
                    is LlmStreamEvent.ModelComplete ->
                        if (toolCalls.isEmpty()) Flux.just(event) else Flux.empty()
                    else -> Flux.just(event)
                }
            }
        val continuation = Flux.defer<LlmStreamEvent> {
            when {
                toolCalls.isEmpty() -> Flux.empty()
                depth >= MAX_TOOL_ROUNDS -> Flux.just(
                    LlmStreamEvent.ModelError(target.modelId, "Tool call limit ($MAX_TOOL_ROUNDS rounds) exceeded"),
                )
                else -> executeAndContinue(adapter, target, request, scope, toolCalls.toList(), depth)
            }
        }
        return Flux.concat(body, continuation)
    }

    private data class ToolOutcome(val event: LlmStreamEvent.ToolResult, val toolTurn: HistoryTurn)

    /** Emits running status + results for [calls], then feeds them back and streams the next round. */
    private fun executeAndContinue(
        adapter: LlmAdapter,
        target: ModelDispatchTarget,
        request: LlmRequest,
        scope: ToolExecutor.TurnScope,
        calls: List<LlmStreamEvent.ToolCall>,
        depth: Int,
    ): Flux<LlmStreamEvent> {
        val running = Flux.fromIterable(
            calls.map { LlmStreamEvent.ToolStatus(target.modelId, it.callId, it.toolName, "running") },
        )
        // Sequential to keep event order deterministic; the dedup scope still shares repeated executions.
        val gathered = Flux.concat(calls.map { executeCall(target, scope, it) }).collectList()
        val next = gathered.flatMapMany { outcomes ->
            val resultEvents = Flux.fromIterable(outcomes.map { it.event })
            val assistantTurn = HistoryTurn(
                role = "assistant",
                text = "",
                toolCalls = calls.map { ToolCallRef(it.callId, it.toolName, it.arguments) },
            )
            val carried = request.history +
                (if (request.prompt.isNotBlank()) listOf(HistoryTurn("user", request.prompt)) else emptyList()) +
                assistantTurn +
                outcomes.map { it.toolTurn }
            // prompt is cleared: the next round continues the assistant/tool exchange, not a new user turn.
            val nextRequest = request.copy(history = carried, prompt = "")
            Flux.concat(resultEvents, toolRound(adapter, target, nextRequest, scope, depth + 1))
        }
        return Flux.concat(running, next)
    }

    private fun executeCall(
        target: ModelDispatchTarget,
        scope: ToolExecutor.TurnScope,
        call: LlmStreamEvent.ToolCall,
    ): Mono<ToolOutcome> {
        val tool = toolRegistry.find(call.toolName) ?: return Mono.just(
            ToolOutcome(
                LlmStreamEvent.ToolResult(
                    target.modelId, call.callId, call.toolName, "failed",
                    error = "Unknown tool: ${call.toolName}",
                ),
                HistoryTurn(
                    "tool",
                    objectMapper.writeValueAsString(mapOf("error" to "Unknown tool: ${call.toolName}")),
                    toolCallId = call.callId,
                ),
            ),
        )
        return toolExecutor.executeOnce(scope, tool, call.arguments).map { result ->
            when (result) {
                is ToolResult.Success -> ToolOutcome(
                    LlmStreamEvent.ToolResult(
                        target.modelId, call.callId, call.toolName, result.status.value, result = result.data,
                    ),
                    HistoryTurn("tool", objectMapper.writeValueAsString(result.data), toolCallId = call.callId),
                )
                is ToolResult.Failure -> ToolOutcome(
                    LlmStreamEvent.ToolResult(
                        target.modelId, call.callId, call.toolName, result.status.value, error = result.errorMessage,
                    ),
                    HistoryTurn(
                        "tool",
                        objectMapper.writeValueAsString(mapOf("error" to result.errorMessage)),
                        toolCallId = call.callId,
                    ),
                )
            }
        }
    }
}
