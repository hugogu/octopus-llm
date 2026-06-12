package com.octopusllm.model

import com.octopusllm.llm.CapabilityMatrix
import com.octopusllm.llm.ProviderDefaults
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

data class ModelResponse(
    val id: String,
    val providerId: String,
    val displayName: String,
    val capabilityMatrix: CapabilityMatrix,
    val isActive: Boolean,
    val source: ModelSource,
)

private fun ModelDefinition.toResponse() = ModelResponse(id, providerId, displayName, capabilityMatrix, isActive, source)

@RestController
@RequestMapping("/api/v1/models")
class ModelCatalogueController(private val service: ModelCatalogueService) {

    @GetMapping
    fun listModels(
        @RequestParam(name = "provider_id", required = false) providerId: String?,
        @RequestParam(name = "input_modality", required = false) inputModality: String?,
    ): Mono<Map<String, List<ModelResponse>>> =
        service.listActiveModels(providerId, inputModality).map { models ->
            mapOf("models" to models.map { it.toResponse() })
        }

    @GetMapping("/{modelId}")
    fun getModel(@PathVariable modelId: String): Mono<ModelResponse> =
        service.getActiveModel(modelId).map { it.toResponse() }
}

data class ProviderResponse(val id: String, val defaultBaseUrl: String)

@RestController
@RequestMapping("/api/v1/providers")
class ProviderController {

    @GetMapping
    fun listProviders(): Map<String, List<ProviderResponse>> =
        mapOf("providers" to ProviderDefaults.baseUrls.map { (id, url) -> ProviderResponse(id, url) })
}
