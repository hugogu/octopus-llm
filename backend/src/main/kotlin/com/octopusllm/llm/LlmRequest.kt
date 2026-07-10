package com.octopusllm.llm

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

data class HistoryTurn(
    val role: String,
    val text: String,
    val attachments: List<Attachment> = emptyList(),
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
)
