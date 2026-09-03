package com.octopusllm.anonymous

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.octopusllm.api.v2.PageResponse
import com.octopusllm.api.v2.toPageResponse
import com.octopusllm.config.TrustedClientIpResolver
import com.octopusllm.llm.LlmStreamEvent
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = false)
data class AnonymousHistoryRequest(
    @field:NotBlank val role: String,
    @field:NotBlank val content: String,
)

@JsonIgnoreProperties(ignoreUnknown = false)
data class AnonymousChatRequest(
    val clientConversationId: UUID,
    val clientRequestId: UUID,
    @field:NotBlank val promptText: String,
    @field:NotEmpty val selectedConfiguredModelIds: List<UUID>,
    @field:Valid val history: List<AnonymousHistoryRequest> = emptyList(),
    val attachments: List<Any>? = null,
    val tools: List<Any>? = null,
)

@RestController
@RequestMapping("/api/v2/anonymous")
class AnonymousChatController(
    private val service: AnonymousChatService,
    private val mapper: ObjectMapper,
    private val clientIpResolver: TrustedClientIpResolver,
) {
    @GetMapping("/models")
    fun models(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") size: Int,
        response: ServerHttpResponse,
    ): reactor.core.publisher.Mono<PageResponse<AnonymousModelView>> {
        response.headers.cacheControl = "no-store"
        return service.listModels(page, size).map { it.toPageResponse { model -> model } }
    }

    @PostMapping("/chat/turns", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chat(
        @Valid @RequestBody request: AnonymousChatRequest,
        exchange: ServerWebExchange,
    ): Flux<ServerSentEvent<String>> {
        exchange.response.headers.cacheControl = "no-store"
        val input = AnonymousTurnInput(
            clientConversationId = request.clientConversationId,
            clientRequestId = request.clientRequestId,
            promptText = request.promptText,
            selectedConfiguredModelIds = request.selectedConfiguredModelIds,
            history = request.history.map { AnonymousHistoryInput(it.role, it.content) },
            attachments = request.attachments,
            tools = request.tools,
        )
        return service.prepare(input, clientIpResolver.resolve(exchange)).flatMapMany { prepared ->
            Flux.concat(
                Flux.just(event("status", mapOf("state" to "STARTED"))),
                service.streamPrepared(prepared).map(::toSse),
                Flux.just(event("result", mapOf("state" to "COMPLETE"))),
            )
        }
    }

    private fun toSse(streamEvent: LlmStreamEvent): ServerSentEvent<String> = when (streamEvent) {
        is LlmStreamEvent.Token -> event(
            "token",
            mapOf("configuredModelId" to streamEvent.configuredModelId, "text" to streamEvent.delta),
        )
        is LlmStreamEvent.Reasoning -> event(
            "reasoning",
            mapOf("configuredModelId" to streamEvent.configuredModelId, "text" to streamEvent.delta),
        )
        is LlmStreamEvent.ModelComplete -> event(
            "model_complete",
            mapOf("configuredModelId" to streamEvent.configuredModelId, "status" to "COMPLETE"),
        )
        is LlmStreamEvent.ModelError -> event(
            "model_error",
            mapOf(
                "configuredModelId" to streamEvent.configuredModelId,
                "status" to "ERROR",
                "errorCode" to if (streamEvent.error.contains("time", ignoreCase = true)) "PROVIDER_TIMEOUT" else "PROVIDER_ERROR",
                "errorMessage" to if (streamEvent.error.contains("time", ignoreCase = true)) {
                    "The model did not finish in time."
                } else {
                    "The model could not complete this request."
                },
            ),
        )
        is LlmStreamEvent.CapabilityNotice -> event(
            "capability_notice",
            mapOf("configuredModelId" to streamEvent.configuredModelId, "notice" to "Some input was not used."),
        )
        is LlmStreamEvent.ToolCall,
        is LlmStreamEvent.ToolStatus,
        is LlmStreamEvent.ToolResult,
        -> event("error", mapOf("code" to "ANONYMOUS_TOOLS_UNSUPPORTED", "message" to "Tools are not available in anonymous chat."))
    }

    private fun event(name: String, payload: Any): ServerSentEvent<String> =
        ServerSentEvent.builder<String>()
            .event(name)
            .data(mapper.writeValueAsString(payload))
            .build()
}
