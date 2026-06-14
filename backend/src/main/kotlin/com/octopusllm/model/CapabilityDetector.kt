package com.octopusllm.model

import org.springframework.stereotype.Component

/**
 * Resolves a model's input modalities (feature 007). Source priority: OpenRouter's cross-provider
 * index first, then the local curated [ModelCatalogue] as a fallback. Models unknown to both stay
 * text-only until a user toggles modalities on.
 */
@Component
class CapabilityDetector(private val openRouter: OpenRouterModelCatalogue) {

    /** Detection from cached data only (no network) — safe for hot paths like model creation. */
    fun detectCached(protocol: String, modelId: String): List<String>? =
        openRouter.modalitiesFor(modelId) ?: ModelCatalogue.modalitiesFor(protocol, modelId)

    /** Refresh the OpenRouter index (best-effort) before a batch detection. */
    fun refreshIndex() {
        openRouter.ensureFresh()
    }
}
