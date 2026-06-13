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

    /**
     * Optional suggestion lookup against the endpoint's model-list API.
     * Configuration must never depend on this; manual model IDs stay valid.
     */
    fun listModels(decryptedApiKey: String, baseUrl: String): List<String> =
        throw UnsupportedOperationException("Model listing is not supported for protocol: $protocolId")
}
