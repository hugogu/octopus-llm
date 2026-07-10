package com.octopusllm.llm

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.octopusllm.tool.Tool
import com.octopusllm.tool.ToolDefinition
import com.octopusllm.tool.ToolExecutor
import com.octopusllm.tool.ToolRegistry
import com.octopusllm.tool.ToolResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.UUID

class ConcurrentLlmOrchestratorTest {

    private fun newOrchestrator(registry: ProtocolAdapterRegistry, tools: List<Tool> = emptyList()) =
        ConcurrentLlmOrchestrator(registry, ToolExecutor(), ToolRegistry(tools), jacksonObjectMapper())
    @Test
    fun `same literal model id on two configured models remains independently attributed`() {
        val first = adapter("first")
        val second = adapter("second")
        every { first.stream(any(), any(), any(), any()) } returns Flux.just(
            LlmStreamEvent.Token("ignored", "A"),
            LlmStreamEvent.ModelComplete("ignored", 1, 2, 10),
        )
        every { second.stream(any(), any(), any(), any()) } returns Flux.just(
            LlmStreamEvent.Token("ignored", "B"),
            LlmStreamEvent.ModelComplete("ignored", 3, 4, 20),
        )
        val firstId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        val orchestrator = newOrchestrator(ProtocolAdapterRegistry(listOf(first, second)))

        StepVerifier.create(
            orchestrator.stream(
                listOf(
                    target(firstId, "same-model", "first"),
                    target(secondId, "same-model", "second"),
                ),
                LlmRequest("hello"),
            ).collectList(),
        ).assertNext { events ->
            assertEquals(setOf(firstId, secondId), events.mapNotNull(::configuredModelId).toSet())
            assertEquals(setOf("same-model"), events.map(::modelId).toSet())
        }.verifyComplete()

        verify(exactly = 1) { first.stream("same-model", any(), "secret", "https://8.8.8.8/v1") }
        verify(exactly = 1) { second.stream("same-model", any(), "secret", "https://8.8.8.8/v1") }
    }

    @Test
    fun `one adapter failure does not cancel another configured model`() {
        val failing = adapter("failing")
        val healthy = adapter("healthy")
        every { failing.stream(any(), any(), any(), any()) } returns Flux.error(IllegalStateException("boom"))
        every { healthy.stream(any(), any(), any(), any()) } returns Flux.just(
            LlmStreamEvent.Token("ignored", "still running"),
            LlmStreamEvent.ModelComplete("ignored", 1, 1, 5),
        )
        val failingId = UUID.randomUUID()
        val healthyId = UUID.randomUUID()
        val orchestrator = newOrchestrator(ProtocolAdapterRegistry(listOf(failing, healthy)))

        StepVerifier.create(
            orchestrator.stream(
                listOf(target(failingId, "m", "failing"), target(healthyId, "m", "healthy")),
                LlmRequest("hello"),
            ).collectList(),
        ).assertNext { events ->
            assertTrue(events.any { it is LlmStreamEvent.ModelError && it.configuredModelId == failingId })
            assertTrue(events.any { it is LlmStreamEvent.Token && it.configuredModelId == healthyId })
            assertTrue(events.any { it is LlmStreamEvent.ModelComplete && it.configuredModelId == healthyId })
        }.verifyComplete()
    }

    @Test
    fun `unsupported attachments are removed and produce an attributed notice`() {
        val adapter = adapter("text")
        every { adapter.stream(any(), any(), any(), any()) } answers {
            val request = secondArg<LlmRequest>()
            assertTrue(request.attachments.isEmpty())
            Flux.just(LlmStreamEvent.ModelComplete("ignored", 1, 1, 5))
        }
        val configuredModelId = UUID.randomUUID()
        val target = target(configuredModelId, "text-only", "text")
        val request = LlmRequest(
            prompt = "describe",
            attachments = listOf(Attachment("image", "data", "image/png")),
        )

        StepVerifier.create(
            newOrchestrator(ProtocolAdapterRegistry(listOf(adapter))).stream(listOf(target), request),
        )
            .assertNext {
                assertTrue(it is LlmStreamEvent.CapabilityNotice)
                assertEquals(configuredModelId, configuredModelId(it))
            }
            .expectNextMatches { it is LlmStreamEvent.ModelComplete }
            .verifyComplete()
    }

    @Test
    fun `tool loop executes a tool and continues to a final answer`() {
        val adapter = adapter("openai")
        val requests = mutableListOf<LlmRequest>()
        every { adapter.stream(any(), capture(requests), any(), any()) } returnsMany listOf(
            // Round 1: the model requests a tool.
            Flux.just(
                LlmStreamEvent.ToolCall("ignored", "c1", "current_time", mapOf("timezone" to "UTC")),
                LlmStreamEvent.ModelComplete("ignored", 1, 1, 5),
            ),
            // Round 2: with the tool result in hand, it answers.
            Flux.just(
                LlmStreamEvent.Token("ignored", "It is 10:30."),
                LlmStreamEvent.ModelComplete("ignored", 2, 3, 8),
            ),
        )
        val currentTime = object : Tool {
            override val definition = ToolDefinition("current_time", "time", emptyMap())
            override fun execute(arguments: Map<String, Any?>) = ToolResult.Success(mapOf("time" to "10:30"))
        }
        val id = UUID.randomUUID()
        val request = LlmRequest(
            prompt = "what time is it in UTC?",
            tools = listOf(ToolDefinition("current_time", "time", emptyMap())),
        )

        val events = newOrchestrator(ProtocolAdapterRegistry(listOf(adapter)), listOf(currentTime))
            .stream(listOf(toolTarget(id, "gpt", "openai")), request)
            .collectList().block()!!

        // Client sees: tool_call, running status, success result, then the streamed answer + one completion.
        assertTrue(events.any { it is LlmStreamEvent.ToolCall && it.toolName == "current_time" && it.configuredModelId == id })
        assertTrue(events.any { it is LlmStreamEvent.ToolStatus && it.status == "running" })
        assertTrue(events.any { it is LlmStreamEvent.ToolResult && it.status == "success" && it.result?.get("time") == "10:30" })
        assertTrue(events.any { it is LlmStreamEvent.Token && it.delta == "It is 10:30." })
        assertEquals(1, events.count { it is LlmStreamEvent.ModelComplete })

        // The second round carried the tool exchange and dropped the user prompt.
        assertEquals(2, requests.size)
        val round2 = requests[1]
        assertEquals("", round2.prompt)
        assertTrue(round2.history.any { it.role == "assistant" && it.toolCalls.any { c -> c.toolName == "current_time" } })
        assertTrue(round2.history.any { it.role == "tool" && it.toolCallId == "c1" })
    }

    private fun adapter(protocol: String): LlmAdapter =
        mockk<LlmAdapter>().also { every { it.protocolId } returns protocol }

    private fun target(id: UUID, modelId: String, protocol: String) = ModelDispatchTarget(
        configuredModelId = id,
        modelId = modelId,
        protocol = protocol,
        decryptedApiKey = "secret",
        capabilityMatrix = CapabilityMatrix(),
        baseUrl = "https://8.8.8.8/v1",
        displayName = modelId,
        connectionLabel = protocol,
    )

    private fun toolTarget(id: UUID, modelId: String, protocol: String) = ModelDispatchTarget(
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

    private fun modelId(event: LlmStreamEvent): String = when (event) {
        is LlmStreamEvent.Token -> event.modelId
        is LlmStreamEvent.Reasoning -> event.modelId
        is LlmStreamEvent.ModelComplete -> event.modelId
        is LlmStreamEvent.ModelError -> event.modelId
        is LlmStreamEvent.CapabilityNotice -> event.modelId
        is LlmStreamEvent.ToolCall -> event.modelId
        is LlmStreamEvent.ToolStatus -> event.modelId
        is LlmStreamEvent.ToolResult -> event.modelId
    }
}
