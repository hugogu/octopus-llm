package com.octopusllm.chat

import com.octopusllm.llm.ConcurrentLlmOrchestrator
import com.octopusllm.llm.LlmRequest
import com.octopusllm.llm.LlmStreamEvent
import com.octopusllm.llm.ModelDispatchTarget
import com.octopusllm.connection.ConfiguredModel
import com.octopusllm.connection.ConnectionService
import com.octopusllm.model.ProtocolDefinitions
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

/**
 * Shared provider execution seam. Persistence and ownership remain in [ChatService]; callers that do
 * not own a server session can use the same normalized provider stream without creating one.
 */
@Component
class LlmTurnRunner(
    private val orchestrator: ConcurrentLlmOrchestrator,
    private val connectionService: ConnectionService,
) {
    fun stream(targets: List<ModelDispatchTarget>, request: LlmRequest): Flux<LlmStreamEvent> =
        orchestrator.stream(targets, request)

    fun targetsFor(models: List<ConfiguredModel>): List<ModelDispatchTarget> = models.map { model ->
        val connection = model.connection
        val protocol = ProtocolDefinitions.require(connection.protocol)
        ModelDispatchTarget(
            configuredModelId = model.id,
            modelId = model.modelId,
            protocol = connection.protocol,
            decryptedApiKey = connectionService.decryptAndValidate(connection),
            capabilityMatrix = ProtocolDefinitions.mergeCapabilities(protocol.baseline, model.capabilityOverrides),
            customParams = model.customParams,
            baseUrl = connection.baseUrl,
            displayName = model.displayName,
            connectionLabel = connection.label,
            connectionId = connection.id,
            inputPricePerMtok = model.inputPricePerMtok,
            outputPricePerMtok = model.outputPricePerMtok,
            priceCurrency = model.priceCurrency,
        )
    }
}
