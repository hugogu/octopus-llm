package com.octopusllm.llm

import java.util.UUID

sealed class LlmStreamEvent {
    data class Token(
        val modelId: String,
        val delta: String,
        val configuredModelId: UUID? = null,
    ) : LlmStreamEvent()

    data class Reasoning(
        val modelId: String,
        val delta: String,
        val configuredModelId: UUID? = null,
    ) : LlmStreamEvent()

    data class ModelComplete(
        val modelId: String,
        val inputTokens: Int?,
        val outputTokens: Int?,
        val latencyMs: Long,
        val configuredModelId: UUID? = null,
        val responseId: UUID? = null,
    ) : LlmStreamEvent()

    data class ModelError(
        val modelId: String,
        val error: String,
        val configuredModelId: UUID? = null,
        val responseId: UUID? = null,
    ) : LlmStreamEvent()

    data class CapabilityNotice(
        val modelId: String,
        val notice: String,
        val configuredModelId: UUID? = null,
    ) : LlmStreamEvent()
}
