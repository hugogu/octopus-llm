package com.octopusllm.llm

data class Attachment(
    val type: String,
    val data: String,
    val mimeType: String,
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
)
