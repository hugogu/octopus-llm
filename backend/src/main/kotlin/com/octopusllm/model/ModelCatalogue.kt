package com.octopusllm.model

data class CatalogueEntry(
    val modelId: String,
    val displayName: String,
    val protocol: String,
    val suggestedBaseUrl: String,
    val providerLabel: String,
    val capabilityOverrides: Map<String, Any?> = emptyMap(),
    val customParams: Map<String, Any?> = emptyMap(),
)

object ModelCatalogue {
    val entries = listOf(
        CatalogueEntry("gpt-4o", "GPT-4o", "openai-compatible", "https://api.openai.com/v1", "OpenAI"),
        CatalogueEntry("gpt-4o-mini", "GPT-4o Mini", "openai-compatible", "https://api.openai.com/v1", "OpenAI"),
        CatalogueEntry(
            "kimi-k2.5",
            "Kimi K2.5",
            "openai-compatible",
            "https://api.moonshot.cn/v1",
            "Kimi",
            mapOf("input_modalities" to listOf("text", "image"), "context_length_tokens" to 256000),
        ),
        CatalogueEntry(
            "kimi-k2.6",
            "Kimi K2.6",
            "openai-compatible",
            "https://api.moonshot.cn/v1",
            "Kimi",
            mapOf("input_modalities" to listOf("text", "image"), "context_length_tokens" to 256000),
        ),
        CatalogueEntry("glm-4-flash", "GLM-4 Flash", "openai-compatible", "https://open.bigmodel.cn/api/paas/v4", "Zhipu"),
        CatalogueEntry("claude-sonnet-4-6", "Claude Sonnet 4.6", "anthropic", "https://api.anthropic.com", "Anthropic"),
        CatalogueEntry("MiniMax-Text-01", "MiniMax Text 01", "minimax", "https://api.minimax.chat/v1", "MiniMax"),
    )
}
