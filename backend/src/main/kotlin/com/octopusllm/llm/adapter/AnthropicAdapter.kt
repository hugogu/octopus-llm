package com.octopusllm.llm.adapter

import com.anthropic.client.AnthropicClient
import com.anthropic.client.AnthropicClientImpl
import com.anthropic.backends.AnthropicBackend
import com.anthropic.core.ClientOptions
import com.anthropic.models.messages.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.octopusllm.llm.*
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ClientResponse
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class AnthropicAdapter(
    private val objectMapper: ObjectMapper,
) : LlmAdapter {
    override val protocolId: String = "anthropic"

    override fun stream(
        modelId: String,
        request: LlmRequest,
        decryptedApiKey: String,
        baseUrlOverride: String?,
    ): Flux<LlmStreamEvent> {
        return Flux.defer {
            val startMs = System.currentTimeMillis()
            var inputTokens: Int? = null
            var outputTokens: Int? = null
            var cacheReadTokens: Int? = null
            var cacheWriteTokens: Int? = null
            // Anthropic streams tool calls as content blocks: a tool_use block_start carries id/name,
            // then input_json_delta fragments build the arguments JSON. Accumulate by block index.
            val toolAcc = linkedMapOf<Int, ToolUseBuilder>()
            val baseUrl = requireNotNull(baseUrlOverride) { "baseUrlOverride is required" }
            val client = StreamingWebClient.builder(baseUrl)
                .defaultHeader("x-api-key", decryptedApiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .build()

            client.post()
                .uri(anthropicMessagesEndpoint(baseUrl))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(buildBody(modelId, request))
                .exchangeToFlux(::responseBody)
                .transform(SseStreaming::dataPayloads)
                .concatMap { payload ->
                    val json = parseProviderJson(payload)
                    when (json.path("type").asText()) {
                        "message_start" -> {
                            val usage = json.path("message").path("usage")
                            inputTokens = usage.intOrNull("input_tokens") ?: inputTokens
                            cacheReadTokens = usage.intOrNull("cache_read_input_tokens") ?: cacheReadTokens
                            cacheWriteTokens = usage.intOrNull("cache_creation_input_tokens") ?: cacheWriteTokens
                            Flux.empty()
                        }
                        "content_block_start" -> {
                            val block = json.path("content_block")
                            if (block.path("type").asText() == "tool_use") {
                                val builder = toolAcc.getOrPut(json.path("index").asInt(0)) { ToolUseBuilder() }
                                block.textOrNull("id")?.let { builder.id = it }
                                block.textOrNull("name")?.let { builder.name = it }
                            }
                            Flux.empty()
                        }
                        "content_block_delta" -> {
                            val delta = json.path("delta")
                            if (delta.path("type").asText() == "input_json_delta") {
                                toolAcc.getOrPut(json.path("index").asInt(0)) { ToolUseBuilder() }
                                    .arguments.append(delta.path("partial_json").asText())
                                Flux.empty()
                            } else {
                                parseContentDelta(modelId, delta)
                            }
                        }
                        "message_delta" -> {
                            outputTokens = json.path("usage").intOrNull("output_tokens") ?: outputTokens
                            Flux.empty()
                        }
                        else -> Flux.empty()
                    }
                }
                .concatWith(Flux.defer { Flux.fromIterable(toolCallEvents(modelId, toolAcc)) })
                .concatWith(
                    Mono.fromSupplier {
                        LlmStreamEvent.ModelComplete(
                            modelId = modelId,
                            inputTokens = inputTokens,
                            outputTokens = outputTokens,
                            latencyMs = System.currentTimeMillis() - startMs,
                            cacheReadTokens = cacheReadTokens,
                            cacheWriteTokens = cacheWriteTokens,
                        )
                    },
                )
                .onErrorResume { error ->
                    Flux.just(LlmStreamEvent.ModelError(modelId, error.message ?: "Unknown error"))
                }
        }
    }

    override fun listModels(decryptedApiKey: String, baseUrl: String): List<String> {
        val backend = AnthropicBackend.builder()
            .apiKey(decryptedApiKey)
            .baseUrl(baseUrl)
            .build()
        val client: AnthropicClient = AnthropicClientImpl(
            ClientOptions.builder()
                .httpClient(NoRedirectAnthropicTransport(backend))
                .build(),
        )
        try {
            return client.models().list().data().map { it.id() }.distinct().sorted()
        } finally {
            client.close()
        }
    }

    private fun buildBody(modelId: String, request: LlmRequest): Map<String, Any> {
        val messages = mutableListOf<Map<String, Any>>()
        request.history.forEach { turn ->
            when {
                // A tool result comes back as a user turn with a tool_result content block (feature 009).
                turn.role == "tool" && turn.toolCallId != null -> messages.add(
                    mapOf(
                        "role" to "user",
                        "content" to listOf(
                            mapOf("type" to "tool_result", "tool_use_id" to turn.toolCallId, "content" to turn.text),
                        ),
                    ),
                )
                // An assistant turn that requested tools emits tool_use blocks (+ any text block).
                turn.toolCalls.isNotEmpty() -> {
                    val content = mutableListOf<Map<String, Any>>()
                    if (turn.text.isNotBlank()) content.add(mapOf("type" to "text", "text" to turn.text))
                    turn.toolCalls.forEach { call ->
                        content.add(
                            mapOf(
                                "type" to "tool_use",
                                "id" to call.callId,
                                "name" to call.toolName,
                                "input" to call.arguments,
                            ),
                        )
                    }
                    messages.add(mapOf("role" to "assistant", "content" to content))
                }
                else -> messages.add(mapOf("role" to normalizedRole(turn.role), "content" to turn.text))
            }
        }

        if (request.attachments.isEmpty()) {
            // A blank prompt is a tool-loop continuation round: the trailing tool_result user message is
            // already the last turn, so we must not append an empty user message.
            if (request.prompt.isNotBlank()) {
                messages.add(mapOf("role" to "user", "content" to request.prompt))
            }
        } else {
            val parts = mutableListOf<Map<String, Any>>()
            parts.add(mapOf("type" to "text", "text" to request.prompt))
            request.attachments
                .filter { it.type == "image" }
                .forEach { att ->
                    // Prefer the public media URL (feature 007); fall back to inline base64 for legacy turns.
                    val source = if (!att.url.isNullOrBlank()) {
                        mapOf("type" to "url", "url" to att.url)
                    } else {
                        mapOf("type" to "base64", "media_type" to att.mimeType, "data" to att.data)
                    }
                    parts.add(mapOf("type" to "image", "source" to source))
                }
            messages.add(mapOf("role" to "user", "content" to parts))
        }

        return buildMap {
            put("model", modelId)
            put("max_tokens", 4096)
            // Anthropic carries the system prompt as a top-level field, not a message role.
            request.systemPrompt?.takeIf { it.isNotBlank() }?.let { put("system", it) }
            put("messages", messages)
            // Advertise available tools (feature 009) in Anthropic's tool schema.
            if (request.tools.isNotEmpty()) {
                put(
                    "tools",
                    request.tools.map { tool ->
                        mapOf(
                            "name" to tool.name,
                            "description" to tool.description,
                            "input_schema" to tool.parameters,
                        )
                    },
                )
            }
            put("stream", true)
        }
    }

    /** Mutable per-block accumulator for a streamed Anthropic tool_use. */
    private class ToolUseBuilder {
        var id: String? = null
        var name: String? = null
        val arguments = StringBuilder()
    }

    private fun toolCallEvents(modelId: String, acc: Map<Int, ToolUseBuilder>): List<LlmStreamEvent> =
        acc.values.mapNotNull { builder ->
            val name = builder.name ?: return@mapNotNull null
            LlmStreamEvent.ToolCall(
                modelId = modelId,
                callId = builder.id ?: java.util.UUID.randomUUID().toString(),
                toolName = name,
                arguments = parseArguments(builder.arguments.toString()),
            )
        }

    private fun parseArguments(raw: String): Map<String, Any?> {
        if (raw.isBlank()) return emptyMap()
        return try {
            @Suppress("UNCHECKED_CAST")
            objectMapper.readValue(raw, Map::class.java) as Map<String, Any?>
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun parseContentDelta(modelId: String, delta: JsonNode): Flux<LlmStreamEvent> =
        when (delta.path("type").asText()) {
            "text_delta" -> delta.textOrNull("text")
                ?.let { Flux.just(LlmStreamEvent.Token(modelId, it)) }
                ?: Flux.empty()
            "thinking_delta" -> delta.textOrNull("thinking")
                ?.let { Flux.just(LlmStreamEvent.Reasoning(modelId, it)) }
                ?: Flux.empty()
            else -> Flux.empty()
        }

    private fun normalizedRole(role: String): String =
        if (role == "assistant") "assistant" else "user"

    private fun anthropicMessagesEndpoint(baseUrl: String) =
        providerEndpoint(baseUrl, if (baseUrl.trimEnd('/').endsWith("/v1")) "messages" else "v1/messages")

    private fun responseBody(response: ClientResponse): Flux<DataBuffer> =
        if (response.statusCode().is2xxSuccessful) {
            response.bodyToFlux(DataBuffer::class.java)
        } else {
            response.releaseBody().thenMany(Flux.error(ProviderHttpException(response.statusCode())))
        }

    private fun parseProviderJson(payload: String): JsonNode =
        try {
            objectMapper.readTree(payload)
        } catch (_: Exception) {
            throw IllegalArgumentException("Provider stream returned invalid JSON")
        }
}
