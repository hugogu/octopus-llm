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

/**
 * US3: a model that does not support tool calling is not offered tools and answers normally, without
 * breaking the turn for a capable model selected alongside it.
 */
class UnsupportedModelToolTest {

    private fun target(id: UUID, modelId: String, protocol: String, functionCalling: Boolean) = ModelDispatchTarget(
        configuredModelId = id,
        modelId = modelId,
        protocol = protocol,
        decryptedApiKey = "secret",
        capabilityMatrix = CapabilityMatrix(supportsFunctionCalling = functionCalling),
        baseUrl = "https://8.8.8.8/v1",
        displayName = modelId,
        connectionLabel = protocol,
    )

    @Test
    fun `unsupported model is not offered tools and the capable model still uses them`() {
        val mapper = jacksonObjectMapper()
        val tool = object : Tool {
            override val definition = ToolDefinition("current_time", "time", emptyMap())
            override fun execute(arguments: Map<String, Any?>) = ToolResult.Success(mapOf("ok" to true))
        }

        // Capable model: round 1 tool call, round 2 answer.
        val capable = mockk<LlmAdapter> { every { protocolId } returns "cap" }
        every { capable.stream(any(), any(), any(), any()) } returnsMany listOf(
            Flux.just(LlmStreamEvent.ToolCall("x", "c1", "current_time", emptyMap()), LlmStreamEvent.ModelComplete("x", 1, 1, 1)),
            Flux.just(LlmStreamEvent.Token("x", "answer"), LlmStreamEvent.ModelComplete("x", 1, 1, 1)),
        )
        // Unsupported model: single-shot answer; capture the request it received.
        val incapable = mockk<LlmAdapter> { every { protocolId } returns "plain" }
        val incapableRequests = mutableListOf<LlmRequest>()
        every { incapable.stream(any(), capture(incapableRequests), any(), any()) } returns
            Flux.just(LlmStreamEvent.Token("y", "no tools here"), LlmStreamEvent.ModelComplete("y", 1, 1, 1))

        val orchestrator = ConcurrentLlmOrchestrator(
            ProtocolAdapterRegistry(listOf(capable, incapable)),
            ToolExecutor(),
            ToolRegistry(listOf(tool)),
            mapper,
        )
        val capId = UUID.randomUUID()
        val plainId = UUID.randomUUID()
        val request = LlmRequest("what time?", tools = listOf(ToolDefinition("current_time", "time", emptyMap())))

        val events = orchestrator.stream(
            listOf(target(capId, "cap-model", "cap", true), target(plainId, "plain-model", "plain", false)),
            request,
        ).collectList().block()!!

        // The unsupported model was invoked exactly once, with no tools advertised.
        assertEquals(1, incapableRequests.size)
        assertTrue(incapableRequests.single().tools.isEmpty())
        // The capable model used the tool and answered.
        assertTrue(events.any { it is LlmStreamEvent.ToolCall && it.configuredModelId == capId })
        assertTrue(events.any { it is LlmStreamEvent.ToolResult && it.status == "success" && it.configuredModelId == capId })
        // The unsupported model answered normally; the turn stays intact with both completions.
        assertTrue(events.any { it is LlmStreamEvent.Token && it.configuredModelId == plainId })
        assertEquals(2, events.count { it is LlmStreamEvent.ModelComplete })
        assertTrue(events.none { it is LlmStreamEvent.ModelError })
    }
}
