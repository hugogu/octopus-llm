package com.octopusllm.connection

import com.octopusllm.model.CapabilityDetector
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Fill-only media-capability detection for a batch of models (feature 007). Refreshes the detection
 * index once, then sets `input_modalities` from detection for models that don't already have it set —
 * never overwriting a manual setting. Returns the models that changed (callers persist them).
 */
@Component
class CapabilityFiller(private val detector: CapabilityDetector) {
    fun fill(models: List<ConfiguredModel>): List<ConfiguredModel> {
        if (models.none { !it.capabilityOverrides.containsKey("input_modalities") }) return emptyList()
        detector.refreshIndex()
        return models
            .filter { !it.capabilityOverrides.containsKey("input_modalities") }
            .mapNotNull { model ->
                val modalities = detector.detectCached(model.connection.protocol, model.modelId)
                    ?: return@mapNotNull null
                model.capabilityOverrides = model.capabilityOverrides.toMutableMap()
                    .apply { put("input_modalities", modalities) }
                model.updatedAt = Instant.now()
                model
            }
    }
}
