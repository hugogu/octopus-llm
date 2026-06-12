package com.octopusllm.llm

import reactor.core.publisher.Flux

interface LlmAdapter {
    val protocolId: String
    fun stream(
        modelId: String,
        request: LlmRequest,
        decryptedApiKey: String,
        baseUrlOverride: String? = null,
    ): Flux<LlmStreamEvent>
}
