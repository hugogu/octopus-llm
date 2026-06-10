package com.octopusllm.model

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Service
class ModelCatalogueService(private val repository: ModelDefinitionRepository) {

    fun listActiveModels(providerId: String? = null, inputModality: String? = null): Mono<List<ModelDefinition>> =
        Mono.fromCallable {
            val models = if (providerId != null) repository.findByProviderIdAndIsActiveTrue(providerId)
            else repository.findByIsActiveTrue()

            if (inputModality != null) {
                models.filter { inputModality in it.capabilityMatrix.inputModalities }
            } else {
                models
            }
        }.subscribeOn(Schedulers.boundedElastic())

    fun getActiveModel(modelId: String): Mono<ModelDefinition> =
        Mono.fromCallable {
            repository.findByIdAndIsActiveTrue(modelId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found: $modelId")
        }.subscribeOn(Schedulers.boundedElastic())
}
