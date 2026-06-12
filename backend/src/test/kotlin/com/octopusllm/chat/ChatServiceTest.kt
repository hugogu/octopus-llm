package com.octopusllm.chat

import com.octopusllm.auth.UserRepository
import com.octopusllm.connection.ConfiguredModelService
import com.octopusllm.connection.ConnectionService
import com.octopusllm.llm.ConcurrentLlmOrchestrator
import com.octopusllm.testsupport.Feature003Fixtures
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.util.Optional
import java.util.UUID

class ChatServiceTest {
    private val sessionRepository = mockk<ChatSessionRepository>()
    private val turnRepository = mockk<ChatTurnRepository>()
    private val responseRepository = mockk<ProviderResponseRepository>()
    private val userRepository = mockk<UserRepository>()
    private val configuredModelService = mockk<ConfiguredModelService>()
    private val connectionService = mockk<ConnectionService>()
    private val orchestrator = mockk<ConcurrentLlmOrchestrator>()
    private val service = ChatService(
        sessionRepository,
        turnRepository,
        responseRepository,
        userRepository,
        configuredModelService,
        connectionService,
        orchestrator,
    )

    @Test
    fun `create session persists owner and title`() {
        val user = Feature003Fixtures.user()
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { sessionRepository.save(any()) } answers { firstArg() }

        StepVerifier.create(service.createSession(user.id, "Test"))
            .assertNext {
                assert(it.user.id == user.id)
                assert(it.title == "Test")
            }
            .verifyComplete()
    }

    @Test
    fun `delete session is owner scoped`() {
        val user = Feature003Fixtures.user()
        val session = ChatSession(user = user, title = "Test")
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { sessionRepository.delete(session) } returns Unit

        StepVerifier.create(service.deleteSession(session.id, user.id))
            .expectNext(Unit)
            .verifyComplete()
        verify(exactly = 1) { sessionRepository.delete(session) }
    }

    @Test
    fun `delete session hides foreign session as not found`() {
        val owner = Feature003Fixtures.user("owner@example.com")
        val session = ChatSession(user = owner, title = "Test")
        every { sessionRepository.findById(session.id) } returns Optional.of(session)

        StepVerifier.create(service.deleteSession(session.id, UUID.randomUUID()))
            .expectErrorMatches {
                it is org.springframework.web.server.ResponseStatusException && it.statusCode.value() == 404
            }
            .verify()
    }
}
