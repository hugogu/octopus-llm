package com.octopusllm.llm

import reactor.core.publisher.Flux

interface LlmAdapter {
    val providerId: String
    fun stream(request: LlmRequest, decryptedApiKey: String): Flux<LlmStreamEvent>
}
