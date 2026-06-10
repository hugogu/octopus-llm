package com.octopusllm.llm

import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers

data class ModelDispatchTarget(
    val modelId: String,
    val providerId: String,
    val decryptedApiKey: String,
    val capabilityMatrix: CapabilityMatrix,
)

@Component
class ConcurrentLlmOrchestrator(private val adapterRegistry: AdapterRegistry) {

    fun stream(targets: List<ModelDispatchTarget>, request: LlmRequest): Flux<LlmStreamEvent> {
        val perModelFluxes = targets.map { target ->
            val adapter = adapterRegistry.getAdapter(target.providerId)

            // Filter attachments to only those supported by this model's capability matrix
            val supportedAttachments = request.attachments.filter { att ->
                att.type in target.capabilityMatrix.inputModalities
            }
            val droppedAttachments = request.attachments.filter { att ->
                att.type !in target.capabilityMatrix.inputModalities
            }

            val routedRequest = request.copy(attachments = supportedAttachments)

            val noticeFlux: Flux<LlmStreamEvent> = if (droppedAttachments.isNotEmpty()) {
                val types = droppedAttachments.map { it.type }.distinct().joinToString(", ")
                Flux.just(
                    LlmStreamEvent.CapabilityNotice(target.modelId, "$types input not supported — text only sent")
                )
            } else {
                Flux.empty()
            }

            val streamFlux = adapter.stream(target.modelId, routedRequest, target.decryptedApiKey)
                .map { event ->
                    when (event) {
                        is LlmStreamEvent.Token -> event.copy(modelId = target.modelId)
                        is LlmStreamEvent.ModelComplete -> event.copy(modelId = target.modelId)
                        is LlmStreamEvent.ModelError -> event.copy(modelId = target.modelId)
                        is LlmStreamEvent.CapabilityNotice -> event.copy(modelId = target.modelId)
                    }
                }
                .onErrorResume { e ->
                    Flux.just(LlmStreamEvent.ModelError(target.modelId, e.message ?: "Unknown error"))
                }
                .subscribeOn(Schedulers.boundedElastic())

            Flux.concat(noticeFlux, streamFlux)
        }

        return Flux.merge(perModelFluxes)
    }
}
