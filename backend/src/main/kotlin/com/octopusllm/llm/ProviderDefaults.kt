package com.octopusllm.llm

/**
 * Single source of truth for each provider's default API base URL.
 * Referenced by the adapter registry (request routing), the model sync
 * service (model discovery), and exposed to the UI so users can see the
 * effective endpoint when no per-key override is set.
 */
object ProviderDefaults {
    val baseUrls: Map<String, String> = mapOf(
        "openai" to "https://api.openai.com/v1",
        "moonshot" to "https://api.moonshot.cn/v1",
        "deepseek" to "https://api.deepseek.com/v1",
        "zhipu" to "https://open.bigmodel.cn/api/paas/v4",
        "anthropic" to "https://api.anthropic.com",
        "minimax" to "https://api.minimax.chat/v1",
    )

    fun baseUrlFor(providerId: String): String? = baseUrls[providerId]
}
