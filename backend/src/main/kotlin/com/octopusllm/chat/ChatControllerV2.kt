package com.octopusllm.chat

import com.fasterxml.jackson.databind.ObjectMapper
import com.octopusllm.llm.LlmStreamEvent
import com.octopusllm.config.TrustedClientIpResolver
import com.octopusllm.reaction.ReactionService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

data class CreateSessionRequestV2(val title: String? = null)

data class SubmitTurnRequestV2(
    @field:NotBlank val promptText: String,
    @field:NotEmpty val selectedConfiguredModelIds: List<UUID>,
    val clientRequestId: String? = null,
    val attachments: List<Map<String, String>> = emptyList(),
)

data class RetryModelRequestV2(
    @field:NotBlank @field:Size(max = 100) val clientRequestId: String,
)

data class SessionResponseV2(
    val id: UUID,
    val title: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ProviderResponseDtoV2(
    val responseId: UUID,
    val configuredModelId: UUID,
    val modelId: String,
    val modelDisplayName: String,
    val protocol: String,
    val connectionLabel: String?,
    val status: String,
    val responseText: String?,
    val reasoningText: String?,
    val errorMessage: String?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val cacheReadTokens: Int?,
    val cacheWriteTokens: Int?,
    val latencyMs: Int,
    val likeCount: Long,
    val likedByMe: Boolean,
    val anonymousLikeCount: Long,
)

data class TurnDtoV2(
    val id: UUID,
    val sequenceNum: Int,
    val promptText: String,
    val selectedModelIds: List<String>,
    val selectedConfiguredModelIds: List<UUID>,
    // Ordered media references (feature 007) so history renders the user's attachments.
    val attachments: List<Map<String, Any?>>,
    val responses: List<ProviderResponseDtoV2>,
    val createdAt: Instant,
)

@RestController
@RequestMapping("/api/v2/chat/sessions")
class ChatControllerV2(
    private val chatService: ChatService,
    private val mapper: ObjectMapper,
    private val reactionService: ReactionService,
    private val clientIpResolver: TrustedClientIpResolver,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal principal: String,
        @RequestBody(required = false) request: CreateSessionRequestV2?,
    ): Mono<SessionResponseV2> =
        chatService.createSession(userId(principal), request?.title).map(::sessionResponse)

    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: String,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(defaultValue = "0") page: Int,
    ): Mono<Map<String, Any>> =
        chatService.listSessions(userId(principal), size, page * size).map { (sessions, total) ->
            mapOf(
                "items" to sessions.map(::sessionResponse),
                "page" to page,
                "size" to size,
                "totalElements" to total,
                "totalPages" to if (total == 0L) 0 else ((total + size - 1) / size).toInt(),
            )
        }

    @GetMapping("/{sessionId}")
    fun get(
        @AuthenticationPrincipal principal: String,
        @PathVariable sessionId: UUID,
    ): Mono<Map<String, Any?>> =
        chatService.getSession(sessionId, userId(principal)).flatMap { (session, turns) ->
            val responseIds = turns.flatMap { it.second }.map { it.id }
            Mono.zip(
                reactionService.states(responseIds, userId(principal)),
                reactionService.anonymousCounts(responseIds),
            ).map { tuple ->
              val states = tuple.t1
              val anonymousCounts = tuple.t2
              mapOf(
                "id" to session.id,
                "title" to session.title,
                "turns" to turns.map { (turn, responses) ->
                    TurnDtoV2(
                        id = turn.id,
                        sequenceNum = turn.sequenceNum,
                        promptText = turn.promptText,
                        selectedModelIds = turn.selectedModelIds.toList(),
                        selectedConfiguredModelIds = turn.selectedConfiguredModelIds.toList(),
                        attachments = turn.attachments.orEmpty()
                            .sortedBy { (it["order"] as? Number)?.toInt() ?: 0 },
                        responses = responses.map { responseDto(it, states[it.id], anonymousCounts[it.id] ?: 0) },
                        createdAt = turn.createdAt,
                    )
                },
              )
            }
        }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @AuthenticationPrincipal principal: String,
        @PathVariable sessionId: UUID,
    ): Mono<Void> = chatService.deleteSession(sessionId, userId(principal)).then()

    @DeleteMapping("/{sessionId}/turns/{turnId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun redactTurn(
        @AuthenticationPrincipal principal: String,
        @PathVariable sessionId: UUID,
        @PathVariable turnId: UUID,
    ): Mono<Void> = chatService.redactTurn(sessionId, turnId, userId(principal)).then()

    @DeleteMapping("/{sessionId}/turns/{turnId}/responses/{responseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun redactResponse(
        @AuthenticationPrincipal principal: String,
        @PathVariable sessionId: UUID,
        @PathVariable turnId: UUID,
        @PathVariable responseId: UUID,
    ): Mono<Void> = chatService.redactResponse(sessionId, turnId, responseId, userId(principal)).then()

    @PostMapping("/{sessionId}/turns", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun submit(
        @AuthenticationPrincipal principal: String,
        @PathVariable sessionId: UUID,
        @Valid @RequestBody request: SubmitTurnRequestV2,
        exchange: ServerWebExchange,
    ): Flux<ServerSentEvent<String>> =
        chatService.submitTurn(
            sessionId,
            userId(principal),
            request.promptText,
            request.selectedConfiguredModelIds,
            request.attachments,
            request.clientRequestId,
            clientIpResolver.resolve(exchange),
        )
            .map(::toSse)
            .concatWithValues(
                ServerSentEvent.builder<String>()
                    .data("""{"event":"all_complete"}""")
                    .build(),
            )

    @PostMapping(
        "/{sessionId}/turns/{turnId}/models/{configuredModelId}/retry",
        produces = [MediaType.TEXT_EVENT_STREAM_VALUE],
    )
    fun retryModel(
        @AuthenticationPrincipal principal: String,
        @PathVariable sessionId: UUID,
        @PathVariable turnId: UUID,
        @PathVariable configuredModelId: UUID,
        @Valid @RequestBody request: RetryModelRequestV2,
    ): Flux<ServerSentEvent<String>> =
        chatService.retryModel(
            sessionId = sessionId,
            turnId = turnId,
            configuredModelId = configuredModelId,
            userId = userId(principal),
            clientRequestId = request.clientRequestId,
        )
            .map(::toSse)
            .concatWithValues(
                ServerSentEvent.builder<String>()
                    .data("""{"event":"all_complete"}""")
                    .build(),
            )

    private fun toSse(event: LlmStreamEvent): ServerSentEvent<String> {
        val data = when (event) {
            is LlmStreamEvent.CapabilityNotice ->
                if (event.modelId == "__system__") {
                    event.notice
                } else {
                    mapper.writeValueAsString(
                        mapOf(
                            "event" to "capability_notice",
                            "configuredModelId" to event.configuredModelId,
                            "modelId" to event.modelId,
                            "notice" to event.notice,
                        ),
                    )
                }
            is LlmStreamEvent.Token -> mapper.writeValueAsString(
                mapOf(
                    "event" to "token",
                    "configuredModelId" to event.configuredModelId,
                    "modelId" to event.modelId,
                    "delta" to event.delta,
                ),
            )
            is LlmStreamEvent.Reasoning -> mapper.writeValueAsString(
                mapOf(
                    "event" to "reasoning",
                    "configuredModelId" to event.configuredModelId,
                    "modelId" to event.modelId,
                    "delta" to event.delta,
                ),
            )
            is LlmStreamEvent.ModelComplete -> mapper.writeValueAsString(
                mapOf(
                    "event" to "model_complete",
                    "configuredModelId" to event.configuredModelId,
                    "modelId" to event.modelId,
                    "inputTokens" to event.inputTokens,
                    "outputTokens" to event.outputTokens,
                    "cacheReadTokens" to event.cacheReadTokens,
                    "cacheWriteTokens" to event.cacheWriteTokens,
                    "latencyMs" to event.latencyMs,
                    "responseId" to event.responseId,
                ),
            )
            is LlmStreamEvent.ModelError -> mapper.writeValueAsString(
                mapOf(
                    "event" to "model_error",
                    "configuredModelId" to event.configuredModelId,
                    "modelId" to event.modelId,
                    "error" to event.error,
                    "responseId" to event.responseId,
                ),
            )
            is LlmStreamEvent.ToolCall -> mapper.writeValueAsString(
                mapOf(
                    "event" to "tool_call",
                    "configuredModelId" to event.configuredModelId,
                    "modelId" to event.modelId,
                    "callId" to event.callId,
                    "toolName" to event.toolName,
                    "arguments" to event.arguments,
                ),
            )
            is LlmStreamEvent.ToolStatus -> mapper.writeValueAsString(
                mapOf(
                    "event" to "tool_status",
                    "configuredModelId" to event.configuredModelId,
                    "modelId" to event.modelId,
                    "callId" to event.callId,
                    "toolName" to event.toolName,
                    "status" to event.status,
                ),
            )
            is LlmStreamEvent.ToolResult -> mapper.writeValueAsString(
                mapOf(
                    "event" to "tool_result",
                    "configuredModelId" to event.configuredModelId,
                    "modelId" to event.modelId,
                    "callId" to event.callId,
                    "toolName" to event.toolName,
                    "status" to event.status,
                    "result" to event.result,
                    "error" to event.error,
                ),
            )
        }
        return ServerSentEvent.builder<String>().data(data).build()
    }

    private fun sessionResponse(session: ChatSession) =
        SessionResponseV2(session.id, session.title, session.createdAt, session.updatedAt)

    private fun responseDto(
        response: ProviderResponse,
        likeState: com.octopusllm.reaction.LikeState?,
        anonymousLikeCount: Long,
    ) =
        ProviderResponseDtoV2(
            responseId = response.id,
            configuredModelId = response.configuredModelId,
            modelId = response.modelId,
            modelDisplayName = response.modelDisplayName,
            protocol = response.protocol,
            connectionLabel = response.connectionLabel,
            status = response.status,
            responseText = response.responseText,
            reasoningText = response.reasoningText,
            errorMessage = response.errorMessage,
            inputTokens = response.inputTokens,
            outputTokens = response.outputTokens,
            cacheReadTokens = response.cacheReadTokens,
            cacheWriteTokens = response.cacheWriteTokens,
            latencyMs = response.latencyMs,
            likeCount = likeState?.likeCount ?: 0,
            likedByMe = likeState?.likedByMe ?: false,
            anonymousLikeCount = anonymousLikeCount,
        )

    private fun userId(principal: String) = UUID.fromString(principal)
}
