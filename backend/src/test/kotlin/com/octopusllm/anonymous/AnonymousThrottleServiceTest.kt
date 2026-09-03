package com.octopusllm.anonymous

import com.octopusllm.auth.AuthActionThrottleRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import reactor.test.StepVerifier

class AnonymousThrottleServiceTest {
    private val throttles = mockk<AuthActionThrottleRepository>()
    private val leases = mockk<AnonymousRequestLeaseRepository>()
    private val service = AnonymousThrottleService(
        throttles,
        leases,
        rateLimit = 2,
        rateWindowSeconds = 60,
        concurrencyLimit = 2,
        promptMaxBytes = 100,
        historyMaxBytes = 100,
        historyMaxTurns = 3,
        modelMaxCount = 2,
        executionTimeoutSeconds = 10,
        hmacSecret = "test-secret",
    )

    @Test
    fun `fixed window rejects the request after the persisted count exceeds the limit`() {
        every { throttles.increment(any(), any(), any(), any()) } returns 1
        every { throttles.requestCount(any(), any(), any()) } returns 3

        StepVerifier.create(service.acquire("203.0.113.5", "hello", 0, 0, 1))
            .expectErrorMatches { it is ResponseStatusException && it.statusCode.value() == 429 }
            .verify()

        verify(exactly = 1) { throttles.requestCount("ANONYMOUS_CHAT", any(), any()) }
        verify(exactly = 0) { leases.claim(any(), any(), any(), any(), any()) }
    }
}
