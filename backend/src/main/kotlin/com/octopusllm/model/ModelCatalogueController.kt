package com.octopusllm.model

import com.octopusllm.llm.CapabilityMatrix
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

data class ModelResponse(
    val id: String,
    val providerId: String,
    val displayName: String,
    val capabilityMatrix: CapabilityMatrix,
    val isActive: Boolean,
)

private fun ModelDefinition.toResponse() = ModelResponse(id, providerId, displayName, capabilityMatrix, isActive)

@RestController
@RequestMapping("/api/v1/models")
class ModelCatalogueController(private val service: ModelCatalogueService) {

    @GetMapping
    fun listModels(@RequestParam providerId: String?): Mono<Map<String, List<ModelResponse>>> =
        service.listActiveModels(providerId).map { models ->
            mapOf("models" to models.map { it.toResponse() })
        }

    @GetMapping("/{modelId}")
    fun getModel(@PathVariable modelId: String): Mono<ModelResponse> =
        service.getActiveModel(modelId).map { it.toResponse() }
}
