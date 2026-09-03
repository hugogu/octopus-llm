package com.octopusllm.anonymous

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.octopusllm.config.TrustedClientIpResolver
import com.octopusllm.llm.LlmStreamEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

class AnonymousChatControllerTest {
    private val service = mockk<AnonymousChatService>()
    private val ipResolver = mockk<TrustedClientIpResolver>()
    private val exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v2/anonymous/chat/turns").build())
    private val controller = AnonymousChatController(service, jacksonObjectMapper(), ipResolver)

    @Test
    fun `anonymous SSE uses safe model-specific events`() {
        val configuredModelId = UUID.randomUUID()
        val prepared = PreparedAnonymousTurn(
            lease = mockk { every { release() } returns Mono.just(Unit) },
            models = emptyList(),
            targets = emptyList(),
            request = com.octopusllm.llm.LlmRequest("hello"),
        )
        every { ipResolver.resolve(exchange) } returns "127.0.0.1"
        every { service.prepare(any(), "127.0.0.1") } returns Mono.just(prepared)
        every { service.streamPrepared(prepared) } returns Flux.just(
            LlmStreamEvent.Token("provider-model", "hello", configuredModelId),
            LlmStreamEvent.ModelError("provider-model", "https://secret-endpoint failed", configuredModelId),
        )

        val events = controller.chat(
            AnonymousChatRequest(
                UUID.randomUUID(), UUID.randomUUID(), "hello", listOf(configuredModelId), emptyList(), null, null,
            ),
            exchange,
        ).collectList().block().orEmpty()
        val names = events.mapNotNull { it.event() }
        val body = events.mapNotNull { it.data() }.joinToString("\n")

        assertTrue("status" in names)
        assertTrue("token" in names)
        assertTrue("model_error" in names)
        assertTrue(body.contains("The model could not complete this request."))
        assertTrue(!body.contains("secret-endpoint"))
        assertTrue(exchange.response.headers.cacheControl == "no-store")
    }
}
