package com.octopusllm.llm.adapter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.octopusllm.llm.*
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux

class MiniMaxAdapter : LlmAdapter {
    override val providerId: String = "minimax"
    private val baseUrl = "https://api.minimax.chat/v1"
    private val mapper = ObjectMapper()

    override fun stream(request: LlmRequest, decryptedApiKey: String): Flux<LlmStreamEvent> {
        val startMs = System.currentTimeMillis()
        val client = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer $decryptedApiKey")
            .defaultHeader("Content-Type", "application/json")
            .build()

        val body = buildBody(request)

        return client.post()
            .uri("/text/chatcompletion_v2")
            .bodyValue(body)
            .retrieve()
            .bodyToFlux(String::class.java)
            .filter { it.startsWith("data:") }
            .flatMap { line ->
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") Flux.empty()
                else Flux.just(mapper.readTree(data))
            }
            .concatMap { json -> parseChunk(json, startMs) }
            .onErrorResume { e ->
                Flux.just(LlmStreamEvent.ModelError(providerId, e.message ?: "Unknown error"))
            }
    }

    private fun parseChunk(json: JsonNode, startMs: Long): Flux<LlmStreamEvent> {
        val delta = json.path("choices").get(0)?.path("delta")?.path("content")?.asText()
        val finishReason = json.path("choices").get(0)?.path("finish_reason")?.asText()

        return if (!delta.isNullOrEmpty()) {
            if (finishReason == "stop") {
                val inputTokens = json.path("usage").path("prompt_tokens").asInt().takeIf { it > 0 }
                val outputTokens = json.path("usage").path("completion_tokens").asInt().takeIf { it > 0 }
                Flux.just(
                    LlmStreamEvent.Token(providerId, delta),
                    LlmStreamEvent.ModelComplete(providerId, inputTokens, outputTokens, System.currentTimeMillis() - startMs),
                )
            } else {
                Flux.just(LlmStreamEvent.Token(providerId, delta))
            }
        } else if (finishReason == "stop") {
            val inputTokens = json.path("usage").path("prompt_tokens").asInt().takeIf { it > 0 }
            val outputTokens = json.path("usage").path("completion_tokens").asInt().takeIf { it > 0 }
            Flux.just(LlmStreamEvent.ModelComplete(providerId, inputTokens, outputTokens, System.currentTimeMillis() - startMs))
        } else {
            Flux.empty()
        }
    }

    private fun buildBody(request: LlmRequest): Map<String, Any> {
        val messages = mutableListOf<Map<String, Any>>()
        request.history.forEach { turn ->
            messages.add(mapOf("role" to turn.role, "content" to turn.text))
        }
        messages.add(mapOf("role" to "user", "content" to request.prompt))
        return mapOf(
            "model" to "abab6.5s-chat",
            "messages" to messages,
            "stream" to true,
        )
    }
}
