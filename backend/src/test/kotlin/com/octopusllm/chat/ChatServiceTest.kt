package com.octopusllm.chat

import com.octopusllm.auth.UserRepository
import com.octopusllm.connection.ConfiguredModelService
import com.octopusllm.connection.ConnectionService
import com.octopusllm.admin.StorageSettings
import com.octopusllm.connection.ConfiguredModel
import com.octopusllm.llm.ConcurrentLlmOrchestrator
import com.octopusllm.llm.LlmStreamEvent
import com.octopusllm.llm.ModelDispatchTarget
import com.octopusllm.media.Media
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import com.octopusllm.testsupport.Feature003Fixtures
import io.mockk.every
import io.mockk.slot
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
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
    private val mediaRepository = mockk<com.octopusllm.media.MediaRepository>()
    private val storageSettingsService = mockk<com.octopusllm.admin.StorageSettingsService>()
    private val mediaStorageFactory = mockk<com.octopusllm.media.MediaStorageFactory>()
    private val service = ChatService(
        sessionRepository,
        turnRepository,
        responseRepository,
        userRepository,
        configuredModelService,
        connectionService,
        orchestrator,
        mediaRepository,
        storageSettingsService,
        mediaStorageFactory,
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

    @Test
    fun `get session returns only latest response attempt per configured model`() {
        val user = Feature003Fixtures.user()
        val session = ChatSession(user = user)
        val configuredModelId = UUID.randomUUID()
        val turn = ChatTurn(
            session = session,
            sequenceNum = 1,
            promptText = "hello",
            selectedModelIds = arrayOf("model"),
            selectedConfiguredModelIds = arrayOf(configuredModelId),
        )
        val first = response(turn, configuredModelId, attempt = 1, status = "error")
        val second = response(turn, configuredModelId, attempt = 2, status = "complete")
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { turnRepository.findBySessionIdOrderBySequenceNum(session.id) } returns listOf(turn)
        every { responseRepository.findByTurnId(turn.id) } returns listOf(first, second)

        StepVerifier.create(service.getSession(session.id, user.id))
            .assertNext { (_, turns) ->
                assertEquals(listOf(second.id), turns.single().second.map(ProviderResponse::id))
            }
            .verifyComplete()
    }

    @Test
    fun `retry failed model creates next attempt and preserves original response`() {
        val user = Feature003Fixtures.user()
        val connection = Feature003Fixtures.connection(user)
        val model = Feature003Fixtures.configuredModel(user, connection)
        val session = ChatSession(user = user)
        val turn = ChatTurn(
            session = session,
            sequenceNum = 1,
            promptText = "hello",
            selectedModelIds = arrayOf(model.modelId),
            selectedConfiguredModelIds = arrayOf(model.id),
        )
        val failed = response(turn, model.id, attempt = 1, status = "error")
        val saved = slot<ProviderResponse>()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { turnRepository.findById(turn.id) } returns Optional.of(turn)
        every { turnRepository.findBySessionIdOrderBySequenceNum(session.id) } returns listOf(turn)
        every { responseRepository.findByRetryRequestId("retry-1") } returns null
        every {
            responseRepository.findFirstByTurnIdAndConfiguredModelIdOrderByAttemptNumberDesc(turn.id, model.id)
        } returns failed
        every { configuredModelService.requireSelectable(user.id, listOf(model.id)) } returns listOf(model)
        every { connectionService.decryptAndValidate(connection) } returns "secret"
        every { orchestrator.stream(any(), any()) } returns Flux.just(
            LlmStreamEvent.Token(model.modelId, "answer", model.id),
            LlmStreamEvent.ModelComplete(model.modelId, 3, 4, 20, configuredModelId = model.id),
        )
        every { responseRepository.save(capture(saved)) } answers { saved.captured }

        StepVerifier.create(service.retryModel(session.id, turn.id, model.id, user.id, "retry-1"))
            .expectNextMatches { it is LlmStreamEvent.Token && it.delta == "answer" }
            .expectNextMatches { it is LlmStreamEvent.ModelComplete && it.responseId == saved.captured.id }
            .verifyComplete()

        assertEquals(2, saved.captured.attemptNumber)
        assertEquals("retry-1", saved.captured.retryRequestId)
        assertEquals("answer", saved.captured.responseText)
        verify(exactly = 0) { responseRepository.delete(any()) }
    }

    @Test
    fun `retry request id replays saved result without dispatching provider again`() {
        val user = Feature003Fixtures.user()
        val session = ChatSession(user = user)
        val configuredModelId = UUID.randomUUID()
        val turn = ChatTurn(
            session = session,
            sequenceNum = 1,
            promptText = "hello",
            selectedModelIds = arrayOf("model"),
            selectedConfiguredModelIds = arrayOf(configuredModelId),
        )
        val existing = response(
            turn,
            configuredModelId,
            attempt = 2,
            status = "complete",
            retryRequestId = "retry-1",
            responseText = "saved",
        )
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { turnRepository.findById(turn.id) } returns Optional.of(turn)
        every { responseRepository.findByRetryRequestId("retry-1") } returns existing

        StepVerifier.create(service.retryModel(session.id, turn.id, configuredModelId, user.id, "retry-1"))
            .expectNextMatches { it is LlmStreamEvent.Token && it.delta == "saved" }
            .expectNextMatches { it is LlmStreamEvent.ModelComplete && it.responseId == existing.id }
            .verifyComplete()

        verify(exactly = 0) { orchestrator.stream(any(), any()) }
        verify(exactly = 0) { configuredModelService.requireSelectable(any(), any()) }
    }

    @Test
    fun `media turn dispatches only capable models and excludes the rest with a notice`() {
        val user = Feature003Fixtures.user()
        val connection = Feature003Fixtures.connection(user)
        val vision = ConfiguredModel(
            user = user,
            connection = connection,
            modelId = "vision",
            displayName = "Vision",
            capabilityOverrides = mapOf("input_modalities" to listOf("text", "image")),
        )
        val textOnly = Feature003Fixtures.configuredModel(user, connection, modelId = "text", displayName = "Text only")
        val session = ChatSession(user = user)
        val media = Media(
            ownerUserId = user.id,
            mediaType = "image",
            mimeType = "image/png",
            sizeBytes = 128,
            storageBackend = "local",
            storageKey = "k1",
            publicUrl = "http://localhost:8080/media/k1",
        )
        val targets = slot<List<ModelDispatchTarget>>()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { configuredModelService.requireSelectable(user.id, listOf(vision.id, textOnly.id)) } returns listOf(vision, textOnly)
        every { mediaRepository.findAllByIdInAndOwnerUserId(setOf(media.id), user.id) } returns listOf(media)
        every { storageSettingsService.get() } returns StorageSettings()
        every { turnRepository.countBySessionId(session.id) } returns 0
        every { turnRepository.save(any()) } answers { firstArg() }
        every { turnRepository.findBySessionIdOrderBySequenceNum(session.id) } returns emptyList()
        every { mediaRepository.saveAll(any<Iterable<Media>>()) } returns emptyList()
        every { sessionRepository.save(any()) } answers { firstArg() }
        every { connectionService.decryptAndValidate(connection) } returns "secret"
        every { responseRepository.save(any()) } answers { firstArg() }
        every { orchestrator.stream(capture(targets), any()) } returns Flux.just(
            LlmStreamEvent.ModelComplete(vision.modelId, 1, 1, 5, configuredModelId = vision.id),
        )

        StepVerifier.create(
            service.submitTurn(
                session.id,
                user.id,
                "what is this?",
                listOf(vision.id, textOnly.id),
                listOf(mapOf("media_id" to media.id.toString(), "order" to "0")),
                null,
                null,
            ),
        ).thenConsumeWhile { true }.verifyComplete()

        assertEquals(1, targets.captured.size)
        assertEquals(vision.id, targets.captured.single().configuredModelId)
        assert(media.turnId != null) // media bound to the saved turn
    }

    @Test
    fun `media turn with no capable model is rejected`() {
        val user = Feature003Fixtures.user()
        val connection = Feature003Fixtures.connection(user)
        val textOnly = Feature003Fixtures.configuredModel(user, connection, modelId = "text", displayName = "Text only")
        val session = ChatSession(user = user)
        val media = Media(
            ownerUserId = user.id,
            mediaType = "image",
            mimeType = "image/png",
            sizeBytes = 128,
            storageBackend = "local",
            storageKey = "k2",
            publicUrl = "http://localhost:8080/media/k2",
        )
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { configuredModelService.requireSelectable(user.id, listOf(textOnly.id)) } returns listOf(textOnly)
        every { mediaRepository.findAllByIdInAndOwnerUserId(setOf(media.id), user.id) } returns listOf(media)
        every { storageSettingsService.get() } returns StorageSettings()

        StepVerifier.create(
            service.submitTurn(
                session.id,
                user.id,
                "what is this?",
                listOf(textOnly.id),
                listOf(mapOf("media_id" to media.id.toString())),
                null,
                null,
            ),
        ).expectErrorMatches { it is ResponseStatusException && it.statusCode == HttpStatus.CONFLICT }
            .verify()

        verify(exactly = 0) { orchestrator.stream(any(), any()) }
    }

    private fun response(
        turn: ChatTurn,
        configuredModelId: UUID,
        attempt: Int,
        status: String,
        retryRequestId: String? = null,
        responseText: String? = null,
    ) = ProviderResponse(
        turn = turn,
        configuredModelId = configuredModelId,
        attemptNumber = attempt,
        retryRequestId = retryRequestId,
        modelId = "model",
        modelDisplayName = "Model",
        protocol = "openai-compatible",
        status = status,
        responseText = responseText,
        errorMessage = if (status == "error") "failed" else null,
        latencyMs = 10,
    )
}
