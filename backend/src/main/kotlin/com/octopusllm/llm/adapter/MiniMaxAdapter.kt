package com.octopusllm.llm.adapter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.octopusllm.llm.*
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux

@Component
class MiniMaxAdapter : LlmAdapter {
    override val protocolId: String = "minimax"
    private val defaultBaseUrl = "https://api.minimax.chat/v1"
    private val mapper = ObjectMapper()

    override fun stream(
        modelId: String,
        request: LlmRequest,
        decryptedApiKey: String,
        baseUrlOverride: String?,
    ): Flux<LlmStreamEvent> {
        return Flux.defer {
            val startMs = System.currentTimeMillis()
            val parser = ThinkingContentParser(modelId)
            val baseUrl = baseUrlOverride ?: defaultBaseUrl
            val client = StreamingWebClient.builder(baseUrl)
                .defaultHeader("Authorization", "Bearer $decryptedApiKey")
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build()

            client.post()
                .uri(providerEndpoint(baseUrl, "text/chatcompletion_v2"))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(buildBody(modelId, request))
                .exchangeToFlux(::responseBody)
                .transform(SseStreaming::dataPayloads)
                .concatMap { payload ->
                    if (payload == "[DONE]") {
                        Flux.empty<LlmStreamEvent>()
                    } else {
                        val events: List<LlmStreamEvent> = try {
                            parseChunk(modelId, mapper.readTree(payload), startMs, parser)
                        } catch (error: Exception) {
                            listOf(LlmStreamEvent.ModelError(modelId, "Invalid JSON: ${error.message}"))
                        }
                        Flux.fromIterable(events)
                    }
                }
                .onErrorResume { e ->
                    Flux.just(LlmStreamEvent.ModelError(modelId, e.message ?: "Unknown error"))
                }
        }
    }

    /**
     * Translate a parsed SSE JSON chunk into ordered [LlmStreamEvent]s. Reads two channels:
     *   1. Separate `reasoning_content` delta (the OpenAI-compat-style fallback).
     *   2. Inline `{{<THINK_OPEN>...{{</THINK_CLOSE>}} blocks embedded in the streamed content (MiniMax's
     *      convention) via [ThinkingContentParser], which handles tags that span chunk boundaries.
     * Emits [LlmStreamEvent.ModelComplete] on `finish_reason == "stop"`, flushing any unclosed think block.
     */
    private fun parseChunk(
        modelId: String,
        json: JsonNode,
        startMs: Long,
        parser: ThinkingContentParser,
    ): List<LlmStreamEvent> {
        val events = mutableListOf<LlmStreamEvent>()
        val choice = json.path("choices").get(0) ?: return events
        val delta = choice.path("delta")

        val reasoningContent = delta.path("reasoning_content").asText()
        if (reasoningContent.isNotEmpty()) {
            events.add(LlmStreamEvent.Reasoning(modelId, reasoningContent))
        }
        val content = delta.path("content").asText()
        if (content.isNotEmpty()) {
            events.addAll(parser.append(content))
        }

        val finishReason = choice.path("finish_reason").asText()
        if (finishReason == "stop") {
            events.addAll(parser.flush())
            val usage = json.path("usage")
            val inputTokens = usage.path("prompt_tokens").asInt().takeIf { it > 0 }
            val outputTokens = usage.path("completion_tokens").asInt().takeIf { it > 0 }
            events.add(
                LlmStreamEvent.ModelComplete(
                    modelId = modelId,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    latencyMs = System.currentTimeMillis() - startMs,
                ),
            )
        }
        return events
    }

    private fun buildBody(modelId: String, request: LlmRequest): Map<String, Any> {
        val messages = mutableListOf<Map<String, Any>>()
        request.history.forEach { turn ->
            messages.add(mapOf("role" to turn.role, "content" to turn.text))
        }
        if (request.attachments.isEmpty()) {
            messages.add(mapOf("role" to "user", "content" to request.prompt))
        } else {
            // OpenAI-style multimodal content parts (feature 007). MiniMax models only receive media
            // when capability-gated in; URL-referenced, with a base64 fallback for legacy turns.
            val parts = mutableListOf<Map<String, Any>>()
            parts.add(mapOf("type" to "text", "text" to request.prompt))
            request.attachments.forEach { att ->
                val url = att.url?.takeIf { it.isNotBlank() } ?: "data:${att.mimeType};base64,${att.data}"
                when (att.type) {
                    "image" -> parts.add(mapOf("type" to "image_url", "image_url" to mapOf("url" to url)))
                    "video" -> parts.add(mapOf("type" to "video_url", "video_url" to mapOf("url" to url)))
                }
            }
            messages.add(mapOf("role" to "user", "content" to parts))
        }
        return mapOf(
            "model" to modelId,
            "messages" to messages,
            "stream" to true,
        )
    }

    private fun responseBody(response: ClientResponse): Flux<DataBuffer> =
        if (response.statusCode().is2xxSuccessful) {
            response.bodyToFlux(DataBuffer::class.java)
        } else {
            response.releaseBody().thenMany(Flux.error(ProviderHttpException(response.statusCode())))
        }

    /**
     * Stateful streaming parser that separates an inline think block from the surrounding answer
     * text. Tags may be split across consecutive chunks, so each [append] runs the inner scanner to
     * completion and keeps the longest potential partial-tag suffix buffered until the next chunk
     * arrives. A [flush] drains any remaining buffer at end-of-stream.
     */
    internal class ThinkingContentParser(private val modelId: String) {
        private var inThinking = false
        private val buffer = StringBuilder()

        fun append(chunk: String): List<LlmStreamEvent> {
            if (chunk.isEmpty()) return emptyList()
            buffer.append(chunk)
            val events = mutableListOf<LlmStreamEvent>()
            while (true) {
                val needle = if (inThinking) THINK_CLOSE else THINK_OPEN
                val idx = buffer.indexOf(needle)
                if (idx < 0) {
                    val safeEnd = safePrefixEnd(buffer, needle)
                    if (safeEnd > 0) {
                        val prefix = buffer.substring(0, safeEnd)
                        events.add(
                            if (inThinking) LlmStreamEvent.Reasoning(modelId, prefix)
                            else LlmStreamEvent.Token(modelId, prefix),
                        )
                        buffer.delete(0, safeEnd)
                    }
                    return events
                }
                if (idx > 0) {
                    val prefix = buffer.substring(0, idx)
                    events.add(
                        if (inThinking) LlmStreamEvent.Reasoning(modelId, prefix)
                        else LlmStreamEvent.Token(modelId, prefix),
                    )
                }
                buffer.delete(0, idx + needle.length)
                inThinking = !inThinking
            }
            @Suppress("UNREACHABLE_CODE")
            return events
        }

        fun flush(): List<LlmStreamEvent> {
            if (buffer.isEmpty()) return emptyList()
            val remaining = buffer.toString()
            buffer.setLength(0)
            return listOf(
                if (inThinking) LlmStreamEvent.Reasoning(modelId, remaining)
                else LlmStreamEvent.Token(modelId, remaining),
            )
        }

        /**
         * Longest suffix of [buf] that is also a prefix of [needle]; returns the index just past
         * that suffix, so callers can emit [0, return) and keep the suffix buffered until the next
         * chunk decides whether the tag is opening/closing or just incidental punctuation.
         */
        private fun safePrefixEnd(buf: StringBuilder, needle: String): Int {
            val maxLen = needle.length
            val end = buf.length
            for (i in minOf(maxLen, end) downTo 1) {
                val tail = buf.substring(end - i)
                if (needle.startsWith(tail)) return end - i
            }
            return end
        }

        private companion object {
            // MiniMax reasoning responses wrap their thinking in this exact pair of tags inline in
            // the streamed content (unlike OpenAI/Anthropic which put reasoning in a separate field).
            const val THINK_OPEN: String = "<" + "think" + ">"
            const val THINK_CLOSE: String = "</" + "think" + ">"
        }
    }
}
