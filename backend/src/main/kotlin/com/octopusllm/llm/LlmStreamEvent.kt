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
        // Normalized cache usage (feature 006, Q3). Null when the provider does not report it.
        val cacheReadTokens: Int? = null,
        val cacheWriteTokens: Int? = null,
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

    /**
     * A model requested a tool invocation (feature 009). Emitted by an adapter after it normalizes the
     * provider's native tool-call representation; the tool loop executes it and feeds the result back.
     */
    data class ToolCall(
        val modelId: String,
        val callId: String,
        val toolName: String,
        val arguments: Map<String, Any?>,
        val configuredModelId: UUID? = null,
    ) : LlmStreamEvent()

    /** Execution-state transition for a tool call, surfaced to the client for transparency. */
    data class ToolStatus(
        val modelId: String,
        val callId: String,
        val toolName: String,
        val status: String,
        val configuredModelId: UUID? = null,
    ) : LlmStreamEvent()

    /** Terminal outcome of a tool call, emitted to the client and fed back to the model. */
    data class ToolResult(
        val modelId: String,
        val callId: String,
        val toolName: String,
        val status: String,
        val result: Map<String, Any?>? = null,
        val error: String? = null,
        val configuredModelId: UUID? = null,
    ) : LlmStreamEvent()
}
