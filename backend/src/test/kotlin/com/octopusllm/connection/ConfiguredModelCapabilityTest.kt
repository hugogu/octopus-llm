package com.octopusllm.connection

import com.octopusllm.auth.UserRepository
import com.octopusllm.model.ModelCatalogue
import com.octopusllm.testsupport.Feature003Fixtures
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.util.Optional

class ConfiguredModelCapabilityTest {
    private val userRepository = mockk<UserRepository>()
    private val connectionService = mockk<ConnectionService>()
    private val repository = mockk<ConfiguredModelRepository>()
    private val service = ConfiguredModelService(userRepository, connectionService, repository)

    @Test
    fun `catalogue exposes modalities for known models only`() {
        assertEquals(listOf("text", "image"), ModelCatalogue.modalitiesFor("openai-compatible", "gpt-4o"))
        assertEquals(listOf("text", "image"), ModelCatalogue.modalitiesFor("openai-compatible", "GPT-4O")) // case-insensitive
        assertNull(ModelCatalogue.modalitiesFor("openai-compatible", "some-unknown-model"))
    }

    @Test
    fun `add auto-fills modalities from catalogue when not provided`() {
        val user = Feature003Fixtures.user()
        val connection = Feature003Fixtures.connection(user)
        val saved = slot<ConfiguredModel>()
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { connectionService.requireOwned(user.id, connection.id) } returns connection
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
    fun `refresh fills catalogue modalities only for models without a manual setting`() {
        val user = Feature003Fixtures.user()
        val connection = Feature003Fixtures.connection(user)
        val catalogueModel = ConfiguredModel(user = user, connection = connection, modelId = "gpt-4o", displayName = "GPT-4o")
        val manualModel = ConfiguredModel(
            user = user, connection = connection, modelId = "gpt-4o-mini", displayName = "Manual",
            capabilityOverrides = mapOf("input_modalities" to listOf("text")),
        )
        val unknownModel = ConfiguredModel(user = user, connection = connection, modelId = "custom-x", displayName = "Custom")
        every { repository.findByUserId(user.id) } returns listOf(catalogueModel, manualModel, unknownModel)
        every { repository.save(any()) } answers { firstArg() }

        StepVerifier.create(service.refreshCapabilities(user.id))
            .assertNext { updated -> assertEquals(1, updated.size) }
            .verifyComplete()

        assertEquals(listOf("text", "image"), catalogueModel.capabilityOverrides["input_modalities"])
        assertEquals(listOf("text"), manualModel.capabilityOverrides["input_modalities"])
        assertTrue(!unknownModel.capabilityOverrides.containsKey("input_modalities"))
    }
}
