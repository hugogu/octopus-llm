package com.octopusllm.llm

import com.octopusllm.llm.adapter.AnthropicAdapter
import com.octopusllm.llm.adapter.MiniMaxAdapter
import com.octopusllm.llm.adapter.OpenAiCompatAdapter
import org.springframework.stereotype.Component

@Component
class AdapterRegistry {
    private val adapters: Map<String, LlmAdapter> = mapOf(
        "openai" to OpenAiCompatAdapter("openai", "https://api.openai.com/v1"),
        "moonshot" to OpenAiCompatAdapter("moonshot", "https://api.moonshot.cn/v1"),
        "deepseek" to OpenAiCompatAdapter("deepseek", "https://api.deepseek.com/v1"),
        "zhipu" to OpenAiCompatAdapter("zhipu", "https://open.bigmodel.cn/api/paas/v4"),
        "anthropic" to AnthropicAdapter(),
        "minimax" to MiniMaxAdapter(),
    )

    fun getAdapter(providerId: String): LlmAdapter =
        adapters[providerId] ?: throw IllegalArgumentException("No adapter for provider: $providerId")
}
