package com.octopusllm.llm

import com.octopusllm.llm.adapter.AnthropicAdapter
import com.octopusllm.llm.adapter.MiniMaxAdapter
import com.octopusllm.llm.adapter.OpenAiCompatAdapter
import org.springframework.stereotype.Component

@Component
class AdapterRegistry {
    private val adapters: Map<String, LlmAdapter> = mapOf(
        "openai" to OpenAiCompatAdapter("openai", ProviderDefaults.baseUrls.getValue("openai")),
        "moonshot" to OpenAiCompatAdapter("moonshot", ProviderDefaults.baseUrls.getValue("moonshot")),
        "deepseek" to OpenAiCompatAdapter("deepseek", ProviderDefaults.baseUrls.getValue("deepseek")),
        "zhipu" to OpenAiCompatAdapter("zhipu", ProviderDefaults.baseUrls.getValue("zhipu")),
        "anthropic" to AnthropicAdapter(),
        "minimax" to MiniMaxAdapter(),
    )

    fun getAdapter(providerId: String): LlmAdapter =
        adapters[providerId] ?: throw IllegalArgumentException("No adapter for provider: $providerId")
}
