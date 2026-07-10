package com.octopusllm.llm

import com.octopusllm.tool.ToolDefinition

/**
 * A media attachment on a request. [type] is the media family ("image" | "video" | "audio") and is
 * matched against a model's `input_modalities` for capability gating. Bytes are referenced by [url]
 * (a public, opaque media URL, feature 007) and/or inline base64 [data] for backward compatibility;
 * adapters prefer [url] when present.
 */
data class Attachment(
    val type: String,
    val data: String = "",
    val mimeType: String,
    val url: String? = null,
)

/** A tool call an assistant turn requested, carried in history so a follow-up round has full context. */
data class ToolCallRef(
    val callId: String,
    val toolName: String,
    val arguments: Map<String, Any?>,
)

/**
 * A prior message in the conversation. [role] is "user" | "assistant" | "tool". An assistant turn that
 * requested tools carries [toolCalls]; a "tool" turn carries the matching [toolCallId] and holds the
 * tool result in [text] (feature 009). Adapters serialize these into each provider's tool protocol.
 */
data class HistoryTurn(
    val role: String,
    val text: String,
    val attachments: List<Attachment> = emptyList(),
    val toolCalls: List<ToolCallRef> = emptyList(),
    val toolCallId: String? = null,
)

data class LlmRequest(
    val prompt: String,
    val history: List<HistoryTurn> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val customParams: Map<String, Any?> = emptyMap(),
    /**
     * Provider-independent system prompt (feature 009). Carries the always-on time context so models
     * can resolve relative terms like "今天"/"下周". Adapters emit it in their native shape (a leading
     * `system` message for OpenAI-compatible/MiniMax, a top-level `system` field for Anthropic). When a
     * model does not support a system prompt, the orchestrator folds it into the user prompt instead.
     */
    val systemPrompt: String? = null,
    /**
     * Tools advertised to the model this turn (feature 009). Empty when tool calling is disabled or the
     * model is not capable; adapters translate each definition into the provider's tool/function schema.
     */
    val tools: List<ToolDefinition> = emptyList(),
)
