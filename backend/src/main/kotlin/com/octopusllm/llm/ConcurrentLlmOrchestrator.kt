package com.octopusllm.llm

import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.concurrent.TimeoutException

data class ModelDispatchTarget(
    val modelId: String,
    val providerId: String,
    val decryptedApiKey: String,
    val capabilityMatrix: CapabilityMatrix,
    val customParams: Map<String, Any?> = emptyMap(),
    val baseUrl: String? = null,
)

@Component
class ConcurrentLlmOrchestrator(private val adapterRegistry: AdapterRegistry) {

    companion object {
        // Max silence between stream events before a model is declared dead.
        // Generous because reasoning models can pause before the first token,
        // but bounded so a hung provider connection surfaces as an error
        // instead of stalling the turn forever.
        private val STREAM_IDLE_TIMEOUT: Duration = Duration.ofSeconds(120)
    }

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

            val routedRequest = request.copy(
                attachments = supportedAttachments,
                customParams = target.customParams,
            )

            val noticeFlux: Flux<LlmStreamEvent> = if (droppedAttachments.isNotEmpty()) {
                val types = droppedAttachments.map { it.type }.distinct().joinToString(", ")
                Flux.just(
                    LlmStreamEvent.CapabilityNotice(target.modelId, "$types input not supported — text only sent")
                )
            } else {
                Flux.empty()
            }

            val streamFlux = adapter.stream(target.modelId, routedRequest, target.decryptedApiKey, target.baseUrl)
                .timeout(STREAM_IDLE_TIMEOUT)
                .map { event ->
                    when (event) {
                        is LlmStreamEvent.Token -> event.copy(modelId = target.modelId)
                        is LlmStreamEvent.Reasoning -> event.copy(modelId = target.modelId)
                        is LlmStreamEvent.ModelComplete -> event.copy(modelId = target.modelId)
                        is LlmStreamEvent.ModelError -> event.copy(modelId = target.modelId)
                        is LlmStreamEvent.CapabilityNotice -> event.copy(modelId = target.modelId)
                    }
                }
                .onErrorResume { e ->
                    val message = if (e is TimeoutException) {
                        "Provider did not respond within ${STREAM_IDLE_TIMEOUT.seconds}s — check provider availability"
                    } else {
                        e.message ?: "Unknown error"
                    }
                    Flux.just(LlmStreamEvent.ModelError(target.modelId, message))
                }
                .subscribeOn(Schedulers.boundedElastic())

            Flux.concat(noticeFlux, streamFlux)
        }

        return Flux.merge(perModelFluxes)
    }
}
