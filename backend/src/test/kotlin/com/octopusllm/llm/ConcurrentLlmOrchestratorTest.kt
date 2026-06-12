package com.octopusllm.llm

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
        val orchestrator = ConcurrentLlmOrchestrator(ProtocolAdapterRegistry(listOf(first, second)))

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
        val orchestrator = ConcurrentLlmOrchestrator(ProtocolAdapterRegistry(listOf(failing, healthy)))

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
            ConcurrentLlmOrchestrator(ProtocolAdapterRegistry(listOf(adapter))).stream(listOf(target), request),
        )
            .assertNext {
                assertTrue(it is LlmStreamEvent.CapabilityNotice)
                assertEquals(configuredModelId, configuredModelId(it))
            }
            .expectNextMatches { it is LlmStreamEvent.ModelComplete }
            .verifyComplete()
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

    private fun configuredModelId(event: LlmStreamEvent): UUID? = when (event) {
        is LlmStreamEvent.Token -> event.configuredModelId
        is LlmStreamEvent.Reasoning -> event.configuredModelId
        is LlmStreamEvent.ModelComplete -> event.configuredModelId
        is LlmStreamEvent.ModelError -> event.configuredModelId
        is LlmStreamEvent.CapabilityNotice -> event.configuredModelId
    }

    private fun modelId(event: LlmStreamEvent): String = when (event) {
        is LlmStreamEvent.Token -> event.modelId
        is LlmStreamEvent.Reasoning -> event.modelId
        is LlmStreamEvent.ModelComplete -> event.modelId
        is LlmStreamEvent.ModelError -> event.modelId
        is LlmStreamEvent.CapabilityNotice -> event.modelId
    }
}
