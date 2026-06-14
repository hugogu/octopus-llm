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
                        "content_block_delta" -> parseContentDelta(modelId, json.path("delta"))
                        "message_delta" -> {
                            outputTokens = json.path("usage").intOrNull("output_tokens") ?: outputTokens
                            Flux.empty()
                        }
                        else -> Flux.empty()
                    }
                }
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
            messages.add(mapOf("role" to normalizedRole(turn.role), "content" to turn.text))
        }

        if (request.attachments.isEmpty()) {
            messages.add(mapOf("role" to "user", "content" to request.prompt))
        } else {
            val parts = mutableListOf<Map<String, Any>>()
            parts.add(mapOf("type" to "text", "text" to request.prompt))
            request.attachments
                .filter { it.type == "image" }
                .forEach { att ->
                    parts.add(
                        mapOf(
                            "type" to "image",
                            "source" to mapOf(
                                "type" to "base64",
                                "media_type" to att.mimeType,
                                "data" to att.data,
                            ),
                        ),
                    )
                }
            messages.add(mapOf("role" to "user", "content" to parts))
        }

        return mapOf(
            "model" to modelId,
            "max_tokens" to 4096,
            "messages" to messages,
            "stream" to true,
        )
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
