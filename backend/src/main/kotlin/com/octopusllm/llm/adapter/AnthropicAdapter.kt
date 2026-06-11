package com.octopusllm.llm.adapter

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.*
import com.octopusllm.llm.*
import reactor.core.publisher.Flux

class AnthropicAdapter : LlmAdapter {
    override val providerId: String = "anthropic"

    override fun stream(modelId: String, request: LlmRequest, decryptedApiKey: String): Flux<LlmStreamEvent> {
        val startMs = System.currentTimeMillis()

        return Flux.create { sink ->
            try {
                val client: AnthropicClient = AnthropicOkHttpClient.builder()
                    .apiKey(decryptedApiKey)
                    .build()

                val messages = buildMessages(request)
                val params = MessageCreateParams.builder()
                    .model(Model.of(modelId))
                    .maxTokens(4096)
                    .messages(messages)
                    .build()

                var inputTokens: Int? = null
                var outputTokens: Int? = null

                client.messages().createStreaming(params).use { streamResponse ->
                    streamResponse.stream().forEach { event ->
                        when {
                            event.isStart() -> {
                                val start = event.asStart()
                                inputTokens = start.message().usage().inputTokens().toInt()
                            }
                            event.isContentBlockDelta() -> {
                                val blockDelta = event.asContentBlockDelta().delta()
                                val text = blockDelta.text().map { it.text() }.orElse(null)
                                if (!text.isNullOrEmpty()) {
                                    sink.next(LlmStreamEvent.Token(modelId, text))
                                }
                                val thinking = blockDelta.thinking().map { it.thinking() }.orElse(null)
                                if (!thinking.isNullOrEmpty()) {
                                    sink.next(LlmStreamEvent.Reasoning(modelId, thinking))
                                }
                            }
                            event.isDelta() -> {
                                outputTokens = event.asDelta().usage().outputTokens().toInt()
                            }
                        }
                    }
                }

                sink.next(
                    LlmStreamEvent.ModelComplete(
                        modelId = modelId,
                        inputTokens = inputTokens,
                        outputTokens = outputTokens,
                        latencyMs = System.currentTimeMillis() - startMs,
                    ),
                )
                sink.complete()
            } catch (e: Exception) {
                sink.next(LlmStreamEvent.ModelError(modelId, e.message ?: "Unknown error"))
                sink.complete()
            }
        }
    }

    private fun buildMessages(request: LlmRequest): List<MessageParam> {
        val messages = mutableListOf<MessageParam>()

        request.history.forEach { turn ->
            val role = if (turn.role == "assistant") MessageParam.Role.ASSISTANT else MessageParam.Role.USER
            messages.add(MessageParam.builder().role(role).content(turn.text).build())
        }

        if (request.attachments.isEmpty()) {
            messages.add(
                MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content(request.prompt)
                    .build()
            )
        } else {
            val parts = mutableListOf<ContentBlockParam>()
            parts.add(
                ContentBlockParam.ofText(
                    TextBlockParam.builder().text(request.prompt).build()
                )
            )
            request.attachments
                .filter { it.type == "image" }
                .forEach { att ->
                    parts.add(
                        ContentBlockParam.ofImage(
                            ImageBlockParam.builder()
                                .source(
                                    Base64ImageSource.builder()
                                        .data(att.data)
                                        .mediaType(Base64ImageSource.MediaType.of(att.mimeType))
                                        .build()
                                )
                                .build()
                        )
                    )
                }
            messages.add(
                MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content(MessageParam.Content.ofBlockParams(parts))
                    .build()
            )
        }

        return messages
    }
}
