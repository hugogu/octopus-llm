package com.octopusllm.llm

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

class ConcurrentLlmOrchestratorTest {

    @Test
    fun `merge interleaves events from multiple adapters and invokes both streams`() {
        val adapterRegistry = mockk<AdapterRegistry>()
        val openAiAdapter = mockk<LlmAdapter>()
        val claudeAdapter = mockk<LlmAdapter>()

        every { adapterRegistry.getAdapter("openai") } returns openAiAdapter
        every { adapterRegistry.getAdapter("anthropic") } returns claudeAdapter

        every { openAiAdapter.stream(any(), any()) } returns Flux.just(
            LlmStreamEvent.Token("openai", "Hello"),
            LlmStreamEvent.ModelComplete("openai", 8, 10, 120L),
        )
        every { claudeAdapter.stream(any(), any()) } returns Flux.just(
            LlmStreamEvent.Token("anthropic", "Hi"),
            LlmStreamEvent.ModelComplete("anthropic", 7, 9, 140L),
        )

        val orchestrator = ConcurrentLlmOrchestrator(adapterRegistry)
        val events = orchestrator.stream(
            targets = listOf(
                ModelDispatchTarget("gpt-4o", "openai", "key-openai", CapabilityMatrix()),
                ModelDispatchTarget("claude-3-5-sonnet", "anthropic", "key-anthropic", CapabilityMatrix()),
            ),
            request = LlmRequest(prompt = "Say hi", history = emptyList(), attachments = emptyList()),
        )

        StepVerifier.create(events)
            .recordWith { mutableListOf() }
            .thenConsumeWhile { true }
            .consumeRecordedWith { recorded ->
                val tokenModels = recorded.filterIsInstance<LlmStreamEvent.Token>().map { it.modelId }.toSet()
                require(tokenModels == setOf("gpt-4o", "claude-3-5-sonnet")) {
                    "Expected tokens from both models, got $tokenModels"
                }
                val completeModels = recorded.filterIsInstance<LlmStreamEvent.ModelComplete>().map { it.modelId }.toSet()
                require(completeModels == setOf("gpt-4o", "claude-3-5-sonnet")) {
                    "Expected completion events from both models, got $completeModels"
                }
            }
            .verifyComplete()

        verify(exactly = 1) { openAiAdapter.stream(any(), "key-openai") }
        verify(exactly = 1) { claudeAdapter.stream(any(), "key-anthropic") }
    }

    @Test
    fun `one adapter failure becomes ModelError without cancelling other streams`() {
        val adapterRegistry = mockk<AdapterRegistry>()
        val failingAdapter = mockk<LlmAdapter>()
        val healthyAdapter = mockk<LlmAdapter>()

        every { adapterRegistry.getAdapter("openai") } returns failingAdapter
        every { adapterRegistry.getAdapter("anthropic") } returns healthyAdapter

        every { failingAdapter.stream(any(), any()) } returns Flux.error(IllegalStateException("boom"))
        every { healthyAdapter.stream(any(), any()) } returns Flux.just(
            LlmStreamEvent.Token("anthropic", "still-running"),
            LlmStreamEvent.ModelComplete("anthropic", 4, 6, 90L),
        )

        val orchestrator = ConcurrentLlmOrchestrator(adapterRegistry)
        val events = orchestrator.stream(
            targets = listOf(
                ModelDispatchTarget("gpt-4o", "openai", "key-openai", CapabilityMatrix()),
                ModelDispatchTarget("claude-3-5-sonnet", "anthropic", "key-anthropic", CapabilityMatrix()),
            ),
            request = LlmRequest(prompt = "Test", history = emptyList(), attachments = emptyList()),
        )

        StepVerifier.create(events)
            .recordWith { mutableListOf() }
            .thenConsumeWhile { true }
            .consumeRecordedWith { recorded ->
                require(recorded.any { it is LlmStreamEvent.ModelError && it.modelId == "gpt-4o" }) {
                    "Expected ModelError for failing adapter"
                }
                require(recorded.any { it is LlmStreamEvent.Token && it.modelId == "claude-3-5-sonnet" }) {
                    "Expected healthy adapter token to continue streaming"
                }
                require(recorded.any { it is LlmStreamEvent.ModelComplete && it.modelId == "claude-3-5-sonnet" }) {
                    "Expected healthy adapter completion event"
                }
            }
            .verifyComplete()
    }
}
