package com.octopusllm.connection

import com.octopusllm.model.CapabilityDetector
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Fill-only model-metadata detection for a batch of models (feature 007). Refreshes the detection
 * index once, then enriches each model from detection — never overwriting a value the user already
 * set. Fills: media modalities, pricing (per 1M tokens, USD), context length, and function-calling
 * support. Returns the models that changed (callers persist them).
 */
@Component
class CapabilityFiller(private val detector: CapabilityDetector) {
    fun fill(models: List<ConfiguredModel>): List<ConfiguredModel> {
        if (models.isEmpty()) return emptyList()
        detector.refreshIndex()
        return models.mapNotNull { model -> if (enrich(model)) model.also { it.updatedAt = Instant.now() } else null }
    }

    private fun enrich(model: ConfiguredModel): Boolean {
        var changed = false
        val overrides = model.capabilityOverrides.toMutableMap()
        val detail = detector.detailFor(model.modelId)

        // Modalities: prefer the rich OpenRouter detail, fall back to the local catalogue.
        if (!overrides.containsKey("input_modalities")) {
            val modalities = detail?.modalities ?: detector.detectCached(model.connection.protocol, model.modelId)
            if (modalities != null) {
                overrides["input_modalities"] = modalities
                changed = true
            }
        }

        if (detail != null) {
            if (!overrides.containsKey("context_length_tokens") && detail.contextLengthTokens != null) {
                overrides["context_length_tokens"] = detail.contextLengthTokens
                changed = true
            }
            if (!overrides.containsKey("supports_function_calling") && detail.supportsFunctionCalling) {
                overrides["supports_function_calling"] = true
                changed = true
            }
            // Pricing fills only when fully unset, so partial manual pricing is never clobbered.
            if (model.inputPricePerMtok == null && model.outputPricePerMtok == null && model.priceCurrency == null &&
                (detail.inputPricePerMtok != null || detail.outputPricePerMtok != null)
            ) {
                model.inputPricePerMtok = detail.inputPricePerMtok
                model.outputPricePerMtok = detail.outputPricePerMtok
                model.priceCurrency = "USD"
                changed = true
            }
        }

        if (changed) model.capabilityOverrides = overrides
        return changed
    }
}
