package com.octopusllm.chat

import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.util.Optional
import java.util.UUID

class ChatServiceTest {

    private val sessionRepository: ChatSessionRepository = mockk()
    private val turnRepository: ChatTurnRepository = mockk()
    private val responseRepository: ProviderResponseRepository = mockk()
    private val userRepository: UserRepository = mockk()
    private val modelConfigRepository = mockk<com.octopusllm.userconfig.UserModelConfigRepository>()
    private val encryptionService = mockk<com.octopusllm.userconfig.ApiKeyEncryptionService>()
    private val orchestrator = mockk<com.octopusllm.llm.ConcurrentLlmOrchestrator>()

    private val chatService = ChatService(
        sessionRepository = sessionRepository,
        turnRepository = turnRepository,
        responseRepository = responseRepository,
        userRepository = userRepository,
        modelConfigRepository = modelConfigRepository,
        encryptionService = encryptionService,
        orchestrator = orchestrator,
    )

    @Test
    fun `createSession with selectedModelId stores it`() {
        val userId = UUID.randomUUID()
        val user = User(email = "test@example.com", passwordHash = "hash")
        val session = ChatSession(user = user, title = "Test", selectedModelId = "gpt-4o")

        every { userRepository.findById(userId) } returns Optional.of(user)
        every { sessionRepository.save(any()) } returns session

        StepVerifier.create(chatService.createSession(userId, "Test", "gpt-4o"))
            .assertNext { result ->
                assert(result.selectedModelId == "gpt-4o")
                assert(result.title == "Test")
            }
            .verifyComplete()
    }

    @Test
    fun `createSession without selectedModelId stores null`() {
        val userId = UUID.randomUUID()
        val user = User(email = "test@example.com", passwordHash = "hash")
        val session = ChatSession(user = user, title = "Test")

        every { userRepository.findById(userId) } returns Optional.of(user)
        every { sessionRepository.save(any()) } returns session

        StepVerifier.create(chatService.createSession(userId, "Test", null))
            .assertNext { result ->
                assert(result.selectedModelId == null)
            }
            .verifyComplete()
    }

    @Test
    fun `deleteSession removes session when user owns it`() {
        val user = User(email = "test@example.com", passwordHash = "hash")
        val userId = user.id
        val sessionId = UUID.randomUUID()
        val session = ChatSession(user = user, title = "Test")

        every { sessionRepository.findById(sessionId) } returns Optional.of(session)
        every { sessionRepository.delete(session) } returns Unit

        StepVerifier.create(chatService.deleteSession(sessionId, userId))
            .expectNext(Unit)
            .verifyComplete()

        verify { sessionRepository.delete(session) }
    }

    @Test
    fun `deleteSession returns not found when user does not own it`() {
        val owner = User(email = "owner@example.com", passwordHash = "hash")
        val otherUserId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val session = ChatSession(user = owner, title = "Test")

        every { sessionRepository.findById(sessionId) } returns Optional.of(session)

        StepVerifier.create(chatService.deleteSession(sessionId, otherUserId))
            .expectErrorMatches { it is org.springframework.web.server.ResponseStatusException && (it as org.springframework.web.server.ResponseStatusException).statusCode.value() == 404 }
            .verify()
    }

    @Test
    fun `deleteSession returns not found when session does not exist`() {
        val userId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()

        every { sessionRepository.findById(sessionId) } returns Optional.empty()

        StepVerifier.create(chatService.deleteSession(sessionId, userId))
            .expectErrorMatches { it is org.springframework.web.server.ResponseStatusException && (it as org.springframework.web.server.ResponseStatusException).statusCode.value() == 404 }
            .verify()
    }
}
