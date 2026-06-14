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
    private val image = mapOf("input_modalities" to listOf("text", "image"))

    val entries = listOf(
        CatalogueEntry("gpt-4o", "GPT-4o", "openai-compatible", "https://api.openai.com/v1", "OpenAI", image),
        CatalogueEntry("gpt-4o-mini", "GPT-4o Mini", "openai-compatible", "https://api.openai.com/v1", "OpenAI", image),
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
        CatalogueEntry("glm-4v", "GLM-4V", "openai-compatible", "https://open.bigmodel.cn/api/paas/v4", "Zhipu", image),
        CatalogueEntry("glm-4v-flash", "GLM-4V Flash", "openai-compatible", "https://open.bigmodel.cn/api/paas/v4", "Zhipu", image),
        CatalogueEntry("qwen-vl-max", "Qwen-VL Max", "openai-compatible", "https://dashscope.aliyuncs.com/compatible-mode/v1", "Alibaba", image),
        CatalogueEntry("claude-sonnet-4-6", "Claude Sonnet 4.6", "anthropic", "https://api.anthropic.com", "Anthropic", image),
        CatalogueEntry("MiniMax-Text-01", "MiniMax Text 01", "minimax", "https://api.minimax.chat/v1", "MiniMax"),
    )

    /** Exact catalogue lookup by protocol + model id (case-insensitive on the id). */
    fun find(protocol: String, modelId: String): CatalogueEntry? =
        entries.firstOrNull { it.protocol == protocol && it.modelId.equals(modelId, ignoreCase = true) }

    /**
     * Catalogue-declared input modalities for a model (feature 007 capability auto-detection), or null
     * when the model is not in the catalogue. There is no provider API that reports modalities, so the
     * curated catalogue is the source of truth; everything else is set manually via toggles.
     */
    fun modalitiesFor(protocol: String, modelId: String): List<String>? {
        val value = find(protocol, modelId)?.capabilityOverrides?.get("input_modalities") as? List<*>
        return value?.filterIsInstance<String>()?.takeIf { it.isNotEmpty() }
    }
}
