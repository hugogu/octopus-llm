package com.octopusllm.chat

import com.fasterxml.jackson.databind.ObjectMapper
import com.octopusllm.config.DuplicateRequestException
import com.octopusllm.llm.LlmStreamEvent
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

data class CreateSessionRequest(val title: String? = null, val selectedModelId: String? = null)

data class SubmitTurnRequest(
    @field:NotBlank val promptText: String,
    @field:NotEmpty val selectedModelIds: List<String>,
    val clientRequestId: String? = null,
    val attachments: List<Map<String, String>> = emptyList(),
)

data class SessionResponse(val id: UUID, val title: String?, val selectedModelId: String?, val createdAt: Instant, val updatedAt: Instant)
private fun ChatSession.toResponse() = SessionResponse(id, title, selectedModelId, createdAt, updatedAt)

data class ProviderResponseDto(
    val modelId: String, val status: String, val responseText: String?, val reasoningText: String?,
    val errorMessage: String?, val inputTokens: Int?, val outputTokens: Int?, val latencyMs: Int,
)
private fun ProviderResponse.toDto() = ProviderResponseDto(modelId, status, responseText, reasoningText, errorMessage, inputTokens, outputTokens, latencyMs)

data class TurnDto(
    val id: UUID, val sequenceNum: Int, val promptText: String,
    val selectedModelIds: List<String>, val responses: List<ProviderResponseDto>, val createdAt: Instant,
)
private fun Pair<ChatTurn, List<ProviderResponse>>.toDto() =
    TurnDto(first.id, first.sequenceNum, first.promptText, first.selectedModelIds.toList(), second.map { it.toDto() }, first.createdAt)

@RestController
@RequestMapping("/api/v1/chat/sessions")
class ChatController(
    private val chatService: ChatService,
    private val mapper: ObjectMapper,
) {
    private fun userId(principal: String) = UUID.fromString(principal)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createSession(
        @AuthenticationPrincipal principal: String,
        @RequestBody(required = false) req: CreateSessionRequest?,
    ): Mono<SessionResponse> =
        chatService.createSession(userId(principal), req?.title, req?.selectedModelId).map { it.toResponse() }

    @GetMapping
    fun listSessions(
        @AuthenticationPrincipal principal: String,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
    ): Mono<Map<String, Any>> =
        chatService.listSessions(userId(principal), limit, offset).map { (sessions, total) ->
            mapOf("sessions" to sessions.map { it.toResponse() }, "total" to total)
        }

    @GetMapping("/{sessionId}")
    fun getSession(
        @AuthenticationPrincipal principal: String,
        @PathVariable sessionId: UUID,
    ): Mono<Map<String, Any?>> =
        chatService.getSession(sessionId, userId(principal)).map { (session, turns) ->
            mapOf(
                "id" to session.id,
                "title" to session.title,
                "selectedModelId" to session.selectedModelId,
                "turns" to turns.map { it.toDto() },
            )
        }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSession(
        @AuthenticationPrincipal principal: String,
        @PathVariable sessionId: UUID,
    ): Mono<Void> =
        chatService.deleteSession(sessionId, userId(principal)).then()

    @PostMapping("/{sessionId}/turns", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun submitTurn(
        @AuthenticationPrincipal principal: String,
        @PathVariable sessionId: UUID,
        @Valid @RequestBody req: SubmitTurnRequest,
    ): Flux<ServerSentEvent<String>> {
        return chatService.submitTurn(
            sessionId = sessionId,
            userId = userId(principal),
            promptText = req.promptText,
            selectedModelIds = req.selectedModelIds,
            attachments = req.attachments,
            clientRequestId = req.clientRequestId,
        ).map { event -> toSse(event) }
            .onErrorResume(DuplicateRequestException::class.java) { ex ->
                val body = mapper.writeValueAsString(mapOf("turnId" to ex.turnId))
                Flux.error(
                    org.springframework.web.server.ResponseStatusException(
                        HttpStatus.CONFLICT, body,
                    ),
                )
            }
            .concatWith(Mono.just(ServerSentEvent.builder<String>().data("""{"event":"all_complete"}""").build()))
    }

    private fun toSse(event: LlmStreamEvent): ServerSentEvent<String> {
        val data = when (event) {
            is LlmStreamEvent.CapabilityNotice ->
                if (event.modelId == "__system__") event.notice // raw JSON for turn_created
                else """{"event":"capability_notice","modelId":"${event.modelId}","notice":"${event.notice}"}"""
            is LlmStreamEvent.Token ->
                mapper.writeValueAsString(mapOf("event" to "token", "modelId" to event.modelId, "delta" to event.delta))
            is LlmStreamEvent.Reasoning ->
                mapper.writeValueAsString(mapOf("event" to "reasoning", "modelId" to event.modelId, "delta" to event.delta))
            is LlmStreamEvent.ModelComplete ->
                mapper.writeValueAsString(mapOf(
                    "event" to "model_complete", "modelId" to event.modelId,
                    "inputTokens" to event.inputTokens, "outputTokens" to event.outputTokens,
                    "latencyMs" to event.latencyMs,
                ))
            is LlmStreamEvent.ModelError ->
                mapper.writeValueAsString(mapOf("event" to "model_error", "modelId" to event.modelId, "error" to event.error))
        }
        return ServerSentEvent.builder<String>().data(data).build()
    }
}
