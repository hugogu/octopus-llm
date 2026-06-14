package com.octopusllm.connection

import com.octopusllm.auth.UserRepository
import com.octopusllm.model.CapabilityDetector
import com.octopusllm.testsupport.Feature003Fixtures
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import reactor.test.StepVerifier
import java.util.Optional

class ConfiguredModelCapabilityTest {
    private val userRepository = mockk<UserRepository>()
    private val connectionService = mockk<ConnectionService>()
    private val repository = mockk<ConfiguredModelRepository>()
    private val detector = mockk<CapabilityDetector>()
    private val filler = mockk<CapabilityFiller>()
    private val service = ConfiguredModelService(userRepository, connectionService, repository, detector, filler)

    @Test
    fun `add auto-fills detected modalities when not provided`() {
        val user = Feature003Fixtures.user()
        val connection = Feature003Fixtures.connection(user)
        val saved = slot<ConfiguredModel>()
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { connectionService.requireOwned(user.id, connection.id) } returns connection
        every { detector.detectCached("openai-compatible", "gpt-4o") } returns listOf("text", "image")
        every { repository.countByConnectionId(connection.id) } returns 0
        every { repository.save(capture(saved)) } answers { firstArg() }

        StepVerifier.create(
            service.add(user.id, connection.id, "gpt-4o", "GPT-4o", emptyMap(), emptyMap(), true, null, null, null),
        ).expectNextCount(1).verifyComplete()

        assertEquals(listOf("text", "image"), saved.captured.capabilityOverrides["input_modalities"])
    }

    @Test
    fun `add does not overwrite explicitly provided modalities`() {
        val user = Feature003Fixtures.user()
        val connection = Feature003Fixtures.connection(user)
        val saved = slot<ConfiguredModel>()
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { connectionService.requireOwned(user.id, connection.id) } returns connection
        every { detector.detectCached(any(), any()) } returns listOf("text", "image", "video")
        every { repository.countByConnectionId(connection.id) } returns 0
        every { repository.save(capture(saved)) } answers { firstArg() }

        StepVerifier.create(
            service.add(
                user.id, connection.id, "gpt-4o", "GPT-4o",
                mapOf("input_modalities" to listOf("text")), emptyMap(), true, null, null, null,
            ),
        ).expectNextCount(1).verifyComplete()

        assertEquals(listOf("text"), saved.captured.capabilityOverrides["input_modalities"])
    }

    @Test
    fun `detect-capabilities is connection-scoped and persists only the filled models`() {
        val user = Feature003Fixtures.user()
        val connection = Feature003Fixtures.connection(user)
        val modelA = ConfiguredModel(user = user, connection = connection, modelId = "gpt-4o", displayName = "A")
        val modelB = ConfiguredModel(user = user, connection = connection, modelId = "custom", displayName = "B")
        every { connectionService.requireOwned(user.id, connection.id) } returns connection
        every { repository.findByConnectionId(connection.id, any()) } returns PageImpl(listOf(modelA, modelB))
        every { filler.fill(listOf(modelA, modelB)) } returns listOf(modelA)
        every { repository.save(modelA) } returns modelA

        StepVerifier.create(service.detectCapabilities(user.id, connection.id))
            .assertNext { updated -> assertEquals(1, updated.size) }
            .verifyComplete()
    }
}
