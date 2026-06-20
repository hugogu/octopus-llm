package com.octopusllm.share

import com.octopusllm.admin.StorageSettingsService
import com.octopusllm.auth.JwtTokenService
import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import com.octopusllm.chat.ChatService
import com.octopusllm.chat.ChatSession
import com.octopusllm.chat.ChatSessionRepository
import com.octopusllm.chat.ChatTurn
import com.octopusllm.chat.ChatTurnRepository
import com.octopusllm.chat.DialogRedaction
import com.octopusllm.chat.DialogRedactionRepository
import com.octopusllm.chat.ProviderResponse
import com.octopusllm.chat.ProviderResponseRepository
import com.octopusllm.connection.ConfiguredModel
import com.octopusllm.connection.ConfiguredModelRepository
import com.octopusllm.connection.ConnectionRepository
import com.octopusllm.media.MediaRepository
import com.octopusllm.media.MediaService
import com.octopusllm.media.MediaStorageFactory
import com.octopusllm.testsupport.AbstractPostgresIntegrationTest
import com.octopusllm.testsupport.Feature003Fixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class SharedQuestImportTest @Autowired constructor(
    private val web: WebTestClient,
    private val users: UserRepository,
    private val connections: ConnectionRepository,
    private val models: ConfiguredModelRepository,
    private val sessions: ChatSessionRepository,
    private val turns: ChatTurnRepository,
    private val responses: ProviderResponseRepository,
    private val redactions: DialogRedactionRepository,
    private val media: MediaRepository,
    private val mediaService: MediaService,
    private val storageSettingsService: StorageSettingsService,
    private val storageFactory: MediaStorageFactory,
    private val chatService: ChatService,
    private val jwt: JwtTokenService,
) : AbstractPostgresIntegrationTest() {

    companion object {
        private val tempDir: Path = Files.createTempDirectory("octopus-shared-import")

        @JvmStatic
        @DynamicPropertySource
        fun mediaProps(registry: DynamicPropertyRegistry) {
            registry.add("media.local.dir") { tempDir.toString() }
            registry.add("media.local.public-base-url") { "http://localhost:8080/media" }
        }
    }

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private data class Fixture(
        val owner: User,
        val session: ChatSession,
        val turn: ChatTurn,
        val visibleResponse: ProviderResponse,
        val sourceMediaId: UUID,
        val token: String,
    )

    private fun fixture(): Fixture {
        val owner = users.save(User(email = "share-owner-${UUID.randomUUID()}@example.com", passwordHash = "h"))
        val connection = connections.save(Feature003Fixtures.connection(owner))
        val model = models.save(
            ConfiguredModel(user = owner, connection = connection, modelId = "source-model", displayName = "Source"),
        )
        val sourceMedia = mediaService.upload(owner.id, png, "image/png", "source.png")
        val session = sessions.save(ChatSession(user = owner, title = "Importable Quest"))
        val turn = turns.save(
            ChatTurn(
                session = session,
                sequenceNum = 1,
                promptText = "compare this",
                attachments = listOf(mapOf("media_id" to sourceMedia.id.toString(), "url" to sourceMedia.publicUrl)),
                selectedModelIds = arrayOf(model.modelId, "hidden-model"),
                selectedConfiguredModelIds = arrayOf(model.id, UUID.randomUUID()),
            ),
        )
        sourceMedia.turnId = turn.id
        media.save(sourceMedia)
        val visible = responses.save(
            ProviderResponse(
                turn = turn,
                modelId = model.modelId,
                configuredModelId = model.id,
                modelDisplayName = model.displayName,
                protocol = connection.protocol,
                status = "complete",
                responseText = "visible answer",
                latencyMs = 10,
            ),
        )
        val hidden = responses.save(
            ProviderResponse(
                turn = turn,
                modelId = "hidden-model",
                configuredModelId = turn.selectedConfiguredModelIds[1],
                modelDisplayName = "Hidden",
                protocol = connection.protocol,
                status = "complete",
                responseText = "redacted answer",
                latencyMs = 11,
            ),
        )
        redactions.save(
            DialogRedaction(
                scope = DialogRedaction.SCOPE_RESPONSE,
                turnId = turn.id,
                responseId = hidden.id,
                redactedBy = owner.id,
            ),
        )
        val bearer = jwt.issue(owner.id, owner.sessionEpoch)
        val token = web.post().uri("/api/v2/chat/sessions/${session.id}/shares")
            .header("Authorization", "Bearer $bearer")
            .exchange().expectStatus().isCreated
            .expectBody(ShareLinkDto::class.java).returnResult().responseBody!!.token
        return Fixture(owner, session, turn, visible, sourceMedia.id, token)
    }

    @Test
    fun `authenticated import deep copies visible history and media while anonymous is rejected`() {
        val source = fixture()
        val importer = users.save(User(email = "share-importer-${UUID.randomUUID()}@example.com", passwordHash = "h"))
        val bearer = jwt.issue(importer.id, importer.sessionEpoch)
        val key = UUID.randomUUID().toString()

        web.post().uri("/api/v2/shared/${source.token}/import")
            .header("Idempotency-Key", key)
            .exchange().expectStatus().isUnauthorized

        val importedId = web.post().uri("/api/v2/shared/${source.token}/import")
            .header("Authorization", "Bearer $bearer")
            .header("Idempotency-Key", key)
            .exchange().expectStatus().isCreated
            .expectBody().jsonPath("$.importedFromLabel").isEqualTo("Imported from a shared Quest")
            .jsonPath("$.sessionId").value<String> { UUID.fromString(it) }
            .returnResult().responseBody!!
            .let { com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readTree(it).path("sessionId").asText() }
            .let(UUID::fromString)

        val imported = sessions.findById(importedId).orElseThrow()
        assertEquals(importer.id, imported.user.id)
        assertNotEquals(source.session.id, imported.id)
        val importedTurn = turns.findBySessionIdOrderBySequenceNum(imported.id).single()
        assertNotEquals(source.turn.id, importedTurn.id)
        val importedResponses = responses.findByTurnId(importedTurn.id)
        assertEquals(1, importedResponses.size)
        assertEquals("visible answer", importedResponses.single().responseText)
        assertNotEquals(source.visibleResponse.id, importedResponses.single().id)

        val importedMediaId = UUID.fromString(importedTurn.attachments!!.single()["media_id"] as String)
        assertNotEquals(source.sourceMediaId, importedMediaId)
        val importedMedia = media.findById(importedMediaId).orElseThrow()
        assertEquals(importer.id, importedMedia.ownerUserId)

        chatService.deleteSession(source.session.id, source.owner.id).block()
        val storage = storageFactory.resolve(storageSettingsService.get())
        assertTrue(storage.read(importedMedia.storageKey)?.contentEquals(png) == true)
    }

    @Test
    fun `shared import replay is stable conflict is rejected and a new key creates a new copy`() {
        val firstSource = fixture()
        val secondSource = fixture()
        val importer = users.save(User(email = "share-replay-${UUID.randomUUID()}@example.com", passwordHash = "h"))
        val bearer = jwt.issue(importer.id, importer.sessionEpoch)
        val key = UUID.randomUUID().toString()

        fun import(token: String, idempotencyKey: String, expectedStatus: Int): String? {
            val result = web.post().uri("/api/v2/shared/$token/import")
                .header("Authorization", "Bearer $bearer")
                .header("Idempotency-Key", idempotencyKey)
                .exchange().expectStatus().isEqualTo(expectedStatus)
                .expectBody(String::class.java).returnResult().responseBody
            return result?.let { com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readTree(it).path("sessionId").asText() }
        }

        val firstId = import(firstSource.token, key, 201)
        assertEquals(firstId, import(firstSource.token, key, 200))
        import(secondSource.token, key, 409)
        val independentId = import(firstSource.token, UUID.randomUUID().toString(), 201)
        assertNotEquals(firstId, independentId)
        assertEquals(
            2,
            sessions.findByUserIdOrderByCreatedAtDesc(importer.id, org.springframework.data.domain.Pageable.unpaged())
                .content.count { it.title == firstSource.session.title },
        )
    }
}
