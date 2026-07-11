package com.octopusllm.tool

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.octopusllm.llm.CapabilityMatrix
import com.octopusllm.llm.ConcurrentLlmOrchestrator
import com.octopusllm.llm.LlmAdapter
import com.octopusllm.llm.LlmRequest
import com.octopusllm.llm.LlmStreamEvent
import com.octopusllm.llm.ModelDispatchTarget
import com.octopusllm.llm.ProtocolAdapterRegistry
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * US3: when two models in the same turn request the same tool with the same arguments, the unified
 * layer executes it once and shares the result, while each model still emits its own tool events.
 */
class CrossModelToolDeduplicationTest {

    private fun target(id: UUID, modelId: String, protocol: String) = ModelDispatchTarget(
        configuredModelId = id,
        modelId = modelId,
        protocol = protocol,
        decryptedApiKey = "secret",
        capabilityMatrix = CapabilityMatrix(supportsFunctionCalling = true),
        baseUrl = "https://8.8.8.8/v1",
        displayName = modelId,
        connectionLabel = protocol,
    )

    private fun configuredModelId(event: LlmStreamEvent): UUID? = when (event) {
        is LlmStreamEvent.Token -> event.configuredModelId
        is LlmStreamEvent.Reasoning -> event.configuredModelId
        is LlmStreamEvent.ModelComplete -> event.configuredModelId
        is LlmStreamEvent.ModelError -> event.configuredModelId
        is LlmStreamEvent.CapabilityNotice -> event.configuredModelId
        is LlmStreamEvent.ToolCall -> event.configuredModelId
        is LlmStreamEvent.ToolStatus -> event.configuredModelId
        is LlmStreamEvent.ToolResult -> event.configuredModelId
    }

    @Test
    fun `identical tool calls across two models execute once and share the result`() {
        val mapper = jacksonObjectMapper()
        val executions = AtomicInteger(0)
        val countingTool = object : Tool {
            override val definition = ToolDefinition("current_time", "time", emptyMap())
            override fun execute(arguments: Map<String, Any?>) =
                ToolResult.Success(mapOf("runs" to executions.incrementAndGet()))
        }

        // Each model: round 1 requests the same tool with the same (empty) args; round 2 answers.
        val adapterA = mockk<LlmAdapter> { every { protocolId } returns "pa" }
        val adapterB = mockk<LlmAdapter> { every { protocolId } returns "pb" }
        every { adapterA.stream(any(), any(), any(), any()) } returnsMany listOf(
            Flux.just(LlmStreamEvent.ToolCall("x", "a1", "current_time", emptyMap()), LlmStreamEvent.ModelComplete("x", 1, 1, 1)),
            Flux.just(LlmStreamEvent.Token("x", "A"), LlmStreamEvent.ModelComplete("x", 1, 1, 1)),
        )
        every { adapterB.stream(any(), any(), any(), any()) } returnsMany listOf(
            Flux.just(LlmStreamEvent.ToolCall("y", "b1", "current_time", emptyMap()), LlmStreamEvent.ModelComplete("y", 1, 1, 1)),
            Flux.just(LlmStreamEvent.Token("y", "B"), LlmStreamEvent.ModelComplete("y", 1, 1, 1)),
        )

        val orchestrator = ConcurrentLlmOrchestrator(
            ProtocolAdapterRegistry(listOf(adapterA, adapterB)),
            ToolExecutor(),
            ToolRegistry(listOf(countingTool)),
            mapper,
        )
        val idA = UUID.randomUUID()
        val idB = UUID.randomUUID()
        val request = LlmRequest("what time?", tools = listOf(ToolDefinition("current_time", "time", emptyMap())))

        val events = orchestrator
            .stream(listOf(target(idA, "ma", "pa"), target(idB, "mb", "pb")), request)
            .collectList().block()!!

        // The tool ran exactly once despite two models requesting it.
        assertEquals(1, executions.get())
        // Both models received a successful result, and it is the same shared execution (runs == 1).
        val results = events.filterIsInstance<LlmStreamEvent.ToolResult>()
        assertEquals(2, results.size)
        assertTrue(results.all { it.status == "success" && it.result?.get("runs") == 1 })
        assertEquals(setOf(idA, idB), results.mapNotNull { configuredModelId(it) }.toSet())
        // Both models completed their turn independently.
        assertEquals(2, events.count { it is LlmStreamEvent.ModelComplete })
    }
}
