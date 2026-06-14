package com.octopusllm.llm.adapter

import com.octopusllm.llm.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import com.openai.client.OpenAIClient
import com.openai.client.OpenAIClientImpl
import com.openai.core.ClientOptions
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ClientResponse
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class OpenAiCompatAdapter(
    private val objectMapper: ObjectMapper,
) : LlmAdapter {
    override val protocolId: String = "openai-compatible"

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
            var chunks = 0
            val baseUrl = requireNotNull(baseUrlOverride) { "baseUrlOverride is required" }
            val client = StreamingWebClient.builder(baseUrl)
                .defaultHeader("Authorization", "Bearer $decryptedApiKey")
                .build()

            client.post()
                .uri(providerEndpoint(baseUrl, "chat/completions"))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(buildBody(modelId, request))
                .exchangeToFlux(::responseBody)
                .transform(SseStreaming::dataPayloads)
                .takeUntil { it == "[DONE]" }
                .filter { it != "[DONE]" }
                .concatMap { payload ->
                    val json = parseProviderJson(payload)
                    json.path("usage").takeIf(JsonNode::isObject)?.let { usage ->
                        inputTokens = usage.intOrNull("prompt_tokens") ?: inputTokens
                        outputTokens = usage.intOrNull("completion_tokens") ?: outputTokens
                        // OpenAI-compatible cache reporting: prompt_tokens_details.cached_tokens →
                        // cache-read. There is no cache-write dimension in this shape.
                        cacheReadTokens = usage.path("prompt_tokens_details").intOrNull("cached_tokens")
                            ?: cacheReadTokens
                    }
                    parseChunk(modelId, json).doOnNext {
                        if (it is LlmStreamEvent.Token) chunks++
                    }
                }
                .concatWith(
                    Mono.fromSupplier {
                        LlmStreamEvent.ModelComplete(
                            modelId = modelId,
                            inputTokens = inputTokens,
                            outputTokens = outputTokens ?: chunks,
                            latencyMs = System.currentTimeMillis() - startMs,
                            cacheReadTokens = cacheReadTokens,
                        )
                    },
                )
                .onErrorResume { error ->
                    Flux.just(LlmStreamEvent.ModelError(modelId, error.message ?: "Unknown error"))
                }
        }
    }

    override fun listModels(decryptedApiKey: String, baseUrl: String): List<String> {
        val client: OpenAIClient = OpenAIClientImpl(
            ClientOptions.builder()
                .apiKey(decryptedApiKey)
                .baseUrl(baseUrl)
                .httpClient(NoRedirectOpenAiTransport(baseUrl.toHttpUrl()))
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
            request.attachments.forEach { att ->
                // Prefer the public media URL (feature 007); fall back to a base64 data URI for legacy turns.
                val url = att.url?.takeIf { it.isNotBlank() } ?: "data:${att.mimeType};base64,${att.data}"
                when (att.type) {
                    "image" -> parts.add(mapOf("type" to "image_url", "image_url" to mapOf("url" to url)))
                    // Video input part name varies by OpenAI-compatible provider (e.g. GLM-4V `video_url`).
                    "video" -> parts.add(mapOf("type" to "video_url", "video_url" to mapOf("url" to url)))
                }
            }
            messages.add(mapOf("role" to "user", "content" to parts))
        }

        return mutableMapOf<String, Any>().apply {
            request.customParams.forEach { (key, value) ->
                when (key) {
                    "temperature", "top_p", "presence_penalty", "frequency_penalty" ->
                        (value as? Number)?.toDouble()?.let { this[key] = it }
                    "max_tokens", "max_completion_tokens" ->
                        (value as? Number)?.toLong()?.let { this[key] = it }
                    "reasoning_effort" ->
                        (value as? String)
                            ?.lowercase()
                            ?.takeIf { it in setOf("low", "medium", "high") }
                            ?.let { this[key] = it }
                    else -> if (value != null) {
                        this[key] = value
                    }
                }
            }
            this["model"] = modelId
            this["messages"] = messages
            this["stream"] = true
        }
    }

    private fun parseChunk(modelId: String, json: JsonNode): Flux<LlmStreamEvent> {
        val delta = json.path("choices").path(0).path("delta")
        val events = mutableListOf<LlmStreamEvent>()
        delta.textOrNull("reasoning_content")?.let {
            events.add(LlmStreamEvent.Reasoning(modelId, it))
        }
        if (!delta.hasNonNull("reasoning_content")) {
            delta.textOrNull("reasoning")?.let {
                events.add(LlmStreamEvent.Reasoning(modelId, it))
            }
        }
        delta.textOrNull("content")?.let {
            events.add(LlmStreamEvent.Token(modelId, it))
        }
        return Flux.fromIterable(events)
    }

    private fun normalizedRole(role: String): String =
        if (role == "assistant") "assistant" else "user"

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
