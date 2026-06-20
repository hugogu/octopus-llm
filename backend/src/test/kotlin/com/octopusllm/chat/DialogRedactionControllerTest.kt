package com.octopusllm.chat

import com.octopusllm.auth.JwtTokenService
import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import com.octopusllm.connection.ConfiguredModel
import com.octopusllm.connection.ConfiguredModelRepository
import com.octopusllm.connection.ConnectionRepository
import com.octopusllm.share.ShareLinkDto
import com.octopusllm.testsupport.AbstractPostgresIntegrationTest
import com.octopusllm.testsupport.Feature003Fixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

class DialogRedactionControllerTest @Autowired constructor(
    private val web: WebTestClient,
    private val users: UserRepository,
    private val connections: ConnectionRepository,
    private val models: ConfiguredModelRepository,
    private val sessions: ChatSessionRepository,
    private val turns: ChatTurnRepository,
    private val responses: ProviderResponseRepository,
    private val redactions: DialogRedactionRepository,
    private val jwt: JwtTokenService,
) : AbstractPostgresIntegrationTest() {

    private data class Fixture(
        val owner: User,
        val outsider: User,
        val admin: User,
        val session: ChatSession,
        val firstTurn: ChatTurn,
        val secondTurn: ChatTurn,
        val firstResponse: ProviderResponse,
        val siblingResponse: ProviderResponse,
        val secondTurnResponse: ProviderResponse,
        val shareToken: String,
    )

    private fun fixture(): Fixture {
        val owner = users.save(User(email = "redact-owner-${UUID.randomUUID()}@example.com", passwordHash = "h"))
        val outsider = users.save(User(email = "redact-other-${UUID.randomUUID()}@example.com", passwordHash = "h"))
        val admin = users.save(
            User(email = "redact-admin-${UUID.randomUUID()}@example.com", passwordHash = "h", isAdmin = true),
        )
        val connection = connections.save(Feature003Fixtures.connection(owner))
        val firstModel = models.save(
            ConfiguredModel(user = owner, connection = connection, modelId = "first", displayName = "First"),
        )
        val secondModel = models.save(
            ConfiguredModel(user = owner, connection = connection, modelId = "second", displayName = "Second"),
        )
        val session = sessions.save(ChatSession(user = owner, title = "Redaction Quest"))
        val firstTurn = turns.save(
            ChatTurn(
                session = session,
                sequenceNum = 1,
                promptText = "remove one response",
                selectedModelIds = arrayOf(firstModel.modelId, secondModel.modelId),
                selectedConfiguredModelIds = arrayOf(firstModel.id, secondModel.id),
            ),
        )
        val firstResponse = responses.save(response(firstTurn, firstModel, "remove me"))
        val siblingResponse = responses.save(response(firstTurn, secondModel, "keep me"))
        val secondTurn = turns.save(
            ChatTurn(
                session = session,
                sequenceNum = 2,
                promptText = "remove whole turn",
                selectedModelIds = arrayOf(firstModel.modelId),
                selectedConfiguredModelIds = arrayOf(firstModel.id),
            ),
        )
        val secondTurnResponse = responses.save(response(secondTurn, firstModel, "hidden with turn"))
        val ownerBearer = jwt.issue(owner.id, owner.sessionEpoch)
        val share = web.post().uri("/api/v2/chat/sessions/${session.id}/shares")
            .header("Authorization", "Bearer $ownerBearer")
            .bodyValue(mapOf("scope" to "public"))
            .exchange().expectStatus().isCreated
            .expectBody(ShareLinkDto::class.java).returnResult().responseBody ?: error("missing share")
        return Fixture(
            owner, outsider, admin, session, firstTurn, secondTurn,
            firstResponse, siblingResponse, secondTurnResponse, share.token,
        )
    }

    @Test
    fun `response redaction is idempotent validates ancestry and preserves sibling analytics rows`() {
        val f = fixture()
        val ownerBearer = jwt.issue(f.owner.id, f.owner.sessionEpoch)
        val outsiderBearer = jwt.issue(f.outsider.id, f.outsider.sessionEpoch)
        val responseCount = responses.count()

        web.delete().uri(
            "/api/v2/chat/sessions/${f.session.id}/turns/${f.firstTurn.id}/responses/${f.firstResponse.id}",
        ).header("Authorization", "Bearer $outsiderBearer")
            .exchange().expectStatus().isForbidden

        web.delete().uri(
            "/api/v2/chat/sessions/${f.session.id}/turns/${f.secondTurn.id}/responses/${f.firstResponse.id}",
        ).header("Authorization", "Bearer $ownerBearer")
            .exchange().expectStatus().isNotFound

        repeat(2) {
            web.delete().uri(
                "/api/v2/chat/sessions/${f.session.id}/turns/${f.firstTurn.id}/responses/${f.firstResponse.id}",
            ).header("Authorization", "Bearer $ownerBearer")
                .exchange().expectStatus().isNoContent
        }

        assertEquals(responseCount, responses.count(), "redaction must preserve immutable analytics rows")
        assertTrue(responses.existsById(f.firstResponse.id))
        assertTrue(responses.existsById(f.siblingResponse.id))
        assertEquals(1, redactions.findByTurnIdIn(listOf(f.firstTurn.id)).count { it.responseId == f.firstResponse.id })

        web.get().uri("/api/v2/chat/sessions/${f.session.id}")
            .header("Authorization", "Bearer $ownerBearer")
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.turns[0].responses.length()").isEqualTo(1)
            .jsonPath("$.turns[0].responses[0].responseId").isEqualTo(f.siblingResponse.id.toString())
        web.get().uri("/api/v2/shared/${f.shareToken}")
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.turns[0].responses.length()").isEqualTo(1)
            .jsonPath("$.turns[0].responses[0].responseId").isEqualTo(f.siblingResponse.id.toString())
    }

    @Test
    fun `turn redaction is idempotent owner or admin and hides the entire turn`() {
        val f = fixture()
        val adminBearer = jwt.issue(f.admin.id, f.admin.sessionEpoch)
        val responseCount = responses.count()

        repeat(2) {
            web.delete().uri("/api/v2/chat/sessions/${f.session.id}/turns/${f.secondTurn.id}")
                .header("Authorization", "Bearer $adminBearer")
                .exchange().expectStatus().isNoContent
        }

        assertEquals(responseCount, responses.count())
        assertTrue(responses.existsById(f.secondTurnResponse.id))
        assertEquals(1, redactions.findByTurnIdIn(listOf(f.secondTurn.id)).count { it.scope == DialogRedaction.SCOPE_TURN })

        val ownerBearer = jwt.issue(f.owner.id, f.owner.sessionEpoch)
        web.get().uri("/api/v2/chat/sessions/${f.session.id}")
            .header("Authorization", "Bearer $ownerBearer")
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.turns.length()").isEqualTo(1)
            .jsonPath("$.turns[0].id").isEqualTo(f.firstTurn.id.toString())
        web.get().uri("/api/v2/shared/${f.shareToken}")
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.turns.length()").isEqualTo(1)
            .jsonPath("$.turns[0].promptText").isEqualTo(f.firstTurn.promptText)
    }

    private fun response(turn: ChatTurn, model: ConfiguredModel, text: String) =
        ProviderResponse(
            turn = turn,
            modelId = model.modelId,
            configuredModelId = model.id,
            modelDisplayName = model.displayName,
            protocol = model.connection.protocol,
            status = "complete",
            responseText = text,
            latencyMs = 1,
        )
}
