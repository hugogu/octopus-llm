package com.octopusllm.llm.adapter

import com.octopusllm.llm.*
import com.openai.core.JsonString
import com.openai.core.JsonValue
import okhttp3.HttpUrl.Companion.toHttpUrl
import com.openai.client.OpenAIClient
import com.openai.client.OpenAIClientImpl
import com.openai.core.ClientOptions
import com.openai.models.ReasoningEffort
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionMessageParam
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

@Component
class OpenAiCompatAdapter : LlmAdapter {
    override val protocolId: String = "openai-compatible"

    override fun stream(
        modelId: String,
        request: LlmRequest,
        decryptedApiKey: String,
        baseUrlOverride: String?,
    ): Flux<LlmStreamEvent> {
        val startMs = System.currentTimeMillis()

        return Flux.create { sink ->
            try {
                val baseUrl = requireNotNull(baseUrlOverride) { "baseUrlOverride is required" }
                val client: OpenAIClient = OpenAIClientImpl(
                    ClientOptions.builder()
                        .apiKey(decryptedApiKey)
                        .baseUrl(baseUrl)
                        .httpClient(NoRedirectOpenAiTransport(baseUrl.toHttpUrl()))
                        .build(),
                )

                try {
                    val messages = buildMessages(request)
                    val paramsBuilder = ChatCompletionCreateParams.builder()
                        .messages(messages)
                        .model(modelId)
                    applyCustomParams(paramsBuilder, request.customParams)
                    val params = paramsBuilder.build()

                    var inputTokens: Int? = null
                    var outputTokens: Int? = null
                    var accumulated = 0

                    client.chat().completions().createStreaming(params).use { streamResponse ->
                        streamResponse.stream().forEach { chunk ->
                            val chunkDelta = chunk.choices().firstOrNull()?.delta()
                            val delta = chunkDelta?.content()?.orElse(null)
                            if (delta != null && delta.isNotEmpty()) {
                                sink.next(LlmStreamEvent.Token(modelId, delta))
                                accumulated++
                            }
                            // DeepSeek-style reasoning channel, not part of the OpenAI schema
                            val reasoningDelta = chunkDelta?._additionalProperties()
                                ?.let { props -> props["reasoning_content"] ?: props["reasoning"] }
                                ?.let { (it as? JsonString)?.value }
                            if (!reasoningDelta.isNullOrEmpty()) {
                                sink.next(LlmStreamEvent.Reasoning(modelId, reasoningDelta))
                            }
                            chunk.usage().ifPresent { usage ->
                                inputTokens = usage.promptTokens().toInt()
                                outputTokens = usage.completionTokens().toInt()
                            }
                        }
                    }

                    sink.next(
                        LlmStreamEvent.ModelComplete(
                            modelId = modelId,
                            inputTokens = inputTokens,
                            outputTokens = outputTokens ?: accumulated,
                            latencyMs = System.currentTimeMillis() - startMs,
                        ),
                    )
                    sink.complete()
                } finally {
                    client.close()
                }
            } catch (e: Exception) {
                sink.next(LlmStreamEvent.ModelError(modelId, e.message ?: "Unknown error"))
                sink.complete()
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

    private fun buildMessages(request: LlmRequest): List<ChatCompletionMessageParam> {
        val messages = mutableListOf<ChatCompletionMessageParam>()

        request.history.forEach { turn ->
            messages.add(
                if (turn.role == "assistant")
                    ChatCompletionMessageParam.ofAssistant(
                        com.openai.models.chat.completions.ChatCompletionAssistantMessageParam.builder()
                            .content(turn.text)
                            .build()
                    )
                else
                    ChatCompletionMessageParam.ofUser(
                        com.openai.models.chat.completions.ChatCompletionUserMessageParam.builder()
                            .content(turn.text)
                            .build()
                    )
            )
        }

        if (request.attachments.isEmpty()) {
            messages.add(
                ChatCompletionMessageParam.ofUser(
                    com.openai.models.chat.completions.ChatCompletionUserMessageParam.builder()
                        .content(request.prompt)
                        .build()
                )
            )
        } else {
            val parts = mutableListOf<com.openai.models.chat.completions.ChatCompletionContentPart>()
            parts.add(
                com.openai.models.chat.completions.ChatCompletionContentPart.ofText(
                    com.openai.models.chat.completions.ChatCompletionContentPartText.builder()
                        .text(request.prompt)
                        .build()
                )
            )
            request.attachments
                .filter { it.type == "image" }
                .forEach { att ->
                    parts.add(
                        com.openai.models.chat.completions.ChatCompletionContentPart.ofImageUrl(
                            com.openai.models.chat.completions.ChatCompletionContentPartImage.builder()
                                .imageUrl(
                                    com.openai.models.chat.completions.ChatCompletionContentPartImage.ImageUrl.builder()
                                        .url("data:${att.mimeType};base64,${att.data}")
                                        .build()
                                )
                                .build()
                        )
                    )
                }
            messages.add(
                ChatCompletionMessageParam.ofUser(
                    com.openai.models.chat.completions.ChatCompletionUserMessageParam.builder()
                        .contentOfArrayOfContentParts(parts)
                        .build()
                )
            )
        }

        return messages
    }

    private fun applyCustomParams(
        builder: ChatCompletionCreateParams.Builder,
        customParams: Map<String, Any?>,
    ) {
        customParams.forEach { (key, value) ->
            when (key) {
                "temperature" -> (value as? Number)?.toDouble()?.let(builder::temperature)
                "top_p" -> (value as? Number)?.toDouble()?.let(builder::topP)
                "max_tokens" -> (value as? Number)?.toLong()?.let(builder::maxTokens)
                "max_completion_tokens" -> (value as? Number)?.toLong()?.let(builder::maxCompletionTokens)
                "presence_penalty" -> (value as? Number)?.toDouble()?.let(builder::presencePenalty)
                "frequency_penalty" -> (value as? Number)?.toDouble()?.let(builder::frequencyPenalty)
                "reasoning_effort" -> (value as? String)?.lowercase()?.let { effort ->
                    when (effort) {
                        "low" -> builder.reasoningEffort(ReasoningEffort.LOW)
                        "medium" -> builder.reasoningEffort(ReasoningEffort.MEDIUM)
                        "high" -> builder.reasoningEffort(ReasoningEffort.HIGH)
                    }
                }
                else -> if (value != null) {
                    builder.putAdditionalBodyProperty(key, JsonValue.from(value))
                }
            }
        }
    }
}
