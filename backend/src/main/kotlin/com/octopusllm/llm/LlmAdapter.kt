package com.octopusllm.llm

import reactor.core.publisher.Flux

interface LlmAdapter {
    val providerId: String
    fun stream(
        modelId: String,
        request: LlmRequest,
        decryptedApiKey: String,
        baseUrlOverride: String? = null,
    ): Flux<LlmStreamEvent>
}
