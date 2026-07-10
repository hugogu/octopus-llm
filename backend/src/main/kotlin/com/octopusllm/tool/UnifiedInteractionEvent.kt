package com.octopusllm.tool

/**
 * Provider-independent representation of a tool interaction within a turn (feature 009). The
 * application layer (chat orchestration, persistence, SSE) operates only on these events; each LLM
 * adapter is responsible for mapping its provider's native tool-calling protocol (OpenAI `tool_calls`,
 * Anthropic `tool_use`, …) to and from this shape so provider differences never leak upward.
 */
sealed class UnifiedInteractionEvent {
    /** A model asked to run [toolName] with [arguments]; [callId] is unique within the turn/model. */
    data class ToolCall(
        val callId: String,
        val toolName: String,
        val arguments: Map<String, Any?>,
    ) : UnifiedInteractionEvent()

    /** Execution-state transition for [callId], surfaced to the UI for transparency. */
    data class ToolStatus(
        val callId: String,
        val toolName: String,
        val status: ToolInvocationStatus,
    ) : UnifiedInteractionEvent()

    /** Terminal result for [callId], fed back to the requesting model(s) and emitted to the client. */
    data class ToolResultEvent(
        val callId: String,
        val toolName: String,
        val result: ToolResult,
    ) : UnifiedInteractionEvent()
}
