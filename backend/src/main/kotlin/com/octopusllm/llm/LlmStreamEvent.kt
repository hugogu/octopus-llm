package com.octopusllm.llm

sealed class LlmStreamEvent {
    data class Token(val modelId: String, val delta: String) : LlmStreamEvent()
    data class ModelComplete(
        val modelId: String,
        val inputTokens: Int?,
        val outputTokens: Int?,
        val latencyMs: Long,
    ) : LlmStreamEvent()
    data class ModelError(val modelId: String, val error: String) : LlmStreamEvent()
    data class CapabilityNotice(val modelId: String, val notice: String) : LlmStreamEvent()
}
