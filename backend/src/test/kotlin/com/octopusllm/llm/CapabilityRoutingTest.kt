package com.octopusllm.llm

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

class CapabilityRoutingTest {

    private fun makeRegistry(vararg pairs: Pair<String, LlmAdapter>): AdapterRegistry {
        val registry = mockk<AdapterRegistry>()
        for ((id, adapter) in pairs) {
            every { registry.getAdapter(id) } returns adapter
        }
        return registry
    }

    @Test
    fun `image attachment dropped for text-only model emits CapabilityNotice`() {
        val textOnlyCaps = CapabilityMatrix(inputModalities = listOf("text"))
        val imageCaps = CapabilityMatrix(inputModalities = listOf("text", "image"))

        val capturedTextOnly = slot<LlmRequest>()
        val capturedImageModel = slot<LlmRequest>()

        val textAdapter = mockk<LlmAdapter>()
        every { textAdapter.stream(any(), capture(capturedTextOnly), any()) } returns
            Flux.just(LlmStreamEvent.ModelComplete("text-only", 1, 1, 100L))

        val imageAdapter = mockk<LlmAdapter>()
        every { imageAdapter.stream(any(), capture(capturedImageModel), any()) } returns
            Flux.just(LlmStreamEvent.ModelComplete("image-model", 1, 1, 100L))

        val registry = makeRegistry("text-only" to textAdapter, "image-model" to imageAdapter)
        val orchestrator = ConcurrentLlmOrchestrator(registry)

        val targets = listOf(
            ModelDispatchTarget("text-only", "text-only", "key1", textOnlyCaps),
            ModelDispatchTarget("image-model", "image-model", "key2", imageCaps),
        )
        val attachment = Attachment(type = "image", data = "base64data", mimeType = "image/png")
        val request = LlmRequest(prompt = "describe", history = emptyList(), attachments = listOf(attachment))

        StepVerifier.create(orchestrator.stream(targets, request))
            .thenConsumeWhile { true }
            .verifyComplete()

        assert(capturedTextOnly.captured.attachments.isEmpty()) {
            "Text-only model should receive no image attachments, got: ${capturedTextOnly.captured.attachments}"
        }
        assert(capturedImageModel.captured.attachments.size == 1) {
            "Image model should receive the image attachment"
        }
    }

    @Test
    fun `no CapabilityNotice when all selected models support the attachment type`() {
        val imageCaps = CapabilityMatrix(inputModalities = listOf("text", "image"))

        val imageAdapter = mockk<LlmAdapter>()
        every { imageAdapter.stream(any(), any(), any()) } returns Flux.just(
            LlmStreamEvent.Token("image-model", "hello"),
            LlmStreamEvent.ModelComplete("image-model", 5, 2, 200L),
        )

        val registry = makeRegistry("image-model" to imageAdapter)
        val orchestrator = ConcurrentLlmOrchestrator(registry)

        val targets = listOf(ModelDispatchTarget("image-model", "image-model", "key", imageCaps))
        val attachment = Attachment(type = "image", data = "base64", mimeType = "image/jpeg")
        val request = LlmRequest(prompt = "what", history = emptyList(), attachments = listOf(attachment))

        StepVerifier.create(orchestrator.stream(targets, request).filter { it is LlmStreamEvent.CapabilityNotice })
            .expectNextCount(0)
            .verifyComplete()
    }

    @Test
    fun `text-only request produces no CapabilityNotice`() {
        val textOnlyCaps = CapabilityMatrix(inputModalities = listOf("text"))

        val textAdapter = mockk<LlmAdapter>()
        every { textAdapter.stream(any(), any(), any()) } returns Flux.just(
            LlmStreamEvent.Token("text-model", "Hello!"),
            LlmStreamEvent.ModelComplete("text-model", 3, 2, 50L),
        )

        val registry = makeRegistry("text-model" to textAdapter)
        val orchestrator = ConcurrentLlmOrchestrator(registry)

        val targets = listOf(ModelDispatchTarget("text-model", "text-model", "key", textOnlyCaps))
        val request = LlmRequest(prompt = "hello", history = emptyList(), attachments = emptyList())

        StepVerifier.create(orchestrator.stream(targets, request).filter { it is LlmStreamEvent.CapabilityNotice })
            .expectNextCount(0)
            .verifyComplete()
    }
}
