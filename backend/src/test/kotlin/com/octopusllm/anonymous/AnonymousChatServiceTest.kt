package com.octopusllm.anonymous

import com.octopusllm.chat.LlmTurnRunner
import com.octopusllm.llm.LlmRequest
import com.octopusllm.llm.LlmStreamEvent
import com.octopusllm.testsupport.Feature003Fixtures
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.concurrent.TimeoutException

class AnonymousChatServiceTest {
    private val models = mockk<com.octopusllm.connection.ConfiguredModelRepository>()
    private val runner = mockk<LlmTurnRunner>()
    private val throttle = mockk<AnonymousThrottleService>()
    private val service = AnonymousChatService(models, runner, throttle, com.octopusllm.chat.TimeContext("Asia/Shanghai"))

    @Test
    fun `provider failures are not reported as timeouts`() {
        every { throttle.executionTimeoutSeconds } returns 10
        every { runner.stream(any(), any()) } returns Flux.error(IllegalStateException("provider unavailable"))
        val prepared = preparedTurn()

        StepVerifier.create(service.streamPrepared(prepared))
            .assertNext { event ->
                assertEquals("The model could not complete this request", (event as LlmStreamEvent.ModelError).error)
            }
            .verifyComplete()
    }

    @Test
    fun `timeouts are reported as timeouts`() {
        every { throttle.executionTimeoutSeconds } returns 10
        every { runner.stream(any(), any()) } returns Flux.error(TimeoutException("provider timed out"))
        val prepared = preparedTurn()

        StepVerifier.create(service.streamPrepared(prepared))
            .assertNext { event ->
                assertEquals("The model did not finish in time", (event as LlmStreamEvent.ModelError).error)
            }
            .verifyComplete()
    }

    private fun preparedTurn(): PreparedAnonymousTurn {
        val user = Feature003Fixtures.user()
        val connection = Feature003Fixtures.connection(user)
        val model = Feature003Fixtures.configuredModel(user, connection)
        return PreparedAnonymousTurn(
            lease = mockk {
                every { release() } returns Mono.just(Unit)
            },
            models = listOf(model),
            targets = emptyList(),
            request = LlmRequest("hello"),
        )
    }
}
