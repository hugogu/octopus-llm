package com.octopusllm.llm

import org.springframework.stereotype.Component

@Component
class ProtocolAdapterRegistry(adapters: List<LlmAdapter>) {
    private val adaptersByProtocol: Map<String, LlmAdapter>

    init {
        val duplicates = adapters.groupBy { it.protocolId }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate protocol adapters: ${duplicates.sorted()}" }
        adaptersByProtocol = adapters.associateBy { it.protocolId }
    }

    fun getAdapter(protocolId: String): LlmAdapter =
        adaptersByProtocol[protocolId]
            ?: throw IllegalArgumentException("No adapter for protocol: $protocolId")
}
