package com.octopusllm.share

import com.octopusllm.auth.JwtTokenService
import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import com.octopusllm.chat.ChatSession
import com.octopusllm.chat.ChatSessionRepository
import com.octopusllm.chat.ChatTurn
import com.octopusllm.chat.ChatTurnRepository
import com.octopusllm.chat.ProviderResponse
import com.octopusllm.chat.ProviderResponseRepository
import com.octopusllm.connection.ConfiguredModel
import com.octopusllm.connection.ConfiguredModelRepository
import com.octopusllm.connection.ConnectionRepository
import com.octopusllm.testsupport.AbstractPostgresIntegrationTest
import com.octopusllm.testsupport.Feature003Fixtures
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

class ShareControllerTest @Autowired constructor(
    private val web: WebTestClient,
    private val users: UserRepository,
    private val connections: ConnectionRepository,
    private val models: ConfiguredModelRepository,
    private val sessions: ChatSessionRepository,
    private val turns: ChatTurnRepository,
    private val responses: ProviderResponseRepository,
    private val jwt: JwtTokenService,
) : AbstractPostgresIntegrationTest() {
    @Test
    fun `owner creates one active share and anonymous likes are cookie deduplicated`() {
        val user = users.save(User(email = "share-${UUID.randomUUID()}@example.com", passwordHash = "hash"))
        val connection = connections.save(Feature003Fixtures.connection(user))
        val model = models.save(
            ConfiguredModel(user = user, connection = connection, modelId = "shared-model", displayName = "Shared"),
        )
        val session = sessions.save(ChatSession(user = user, title = "Shared chat"))
        val turn = turns.save(
            ChatTurn(
                session = session,
                sequenceNum = 1,
                promptText = "hello",
                selectedModelIds = arrayOf(model.modelId),
                selectedConfiguredModelIds = arrayOf(model.id),
            ),
        )
        val response = responses.save(
            ProviderResponse(
                turn = turn,
                modelId = model.modelId,
                configuredModelId = model.id,
                modelDisplayName = model.displayName,
                protocol = connection.protocol,
                status = "complete",
                responseText = "world",
                latencyMs = 10,
            ),
        )
        val bearer = jwt.issue(user.id, user.sessionEpoch)
        val share = web.post().uri("/api/v2/chat/sessions/${session.id}/shares")
            .header("Authorization", "Bearer $bearer")
            .exchange().expectStatus().isCreated
            .expectBody(ShareLinkDto::class.java)
            .returnResult().responseBody ?: error("missing share")

        web.post().uri("/api/v2/chat/sessions/${session.id}/shares")
            .header("Authorization", "Bearer $bearer")
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.token").isEqualTo(share.token)

        val read = web.get().uri("/api/v2/shared/${share.token}")
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.turns[0].responses[0].responseId").isEqualTo(response.id.toString())
            .returnResult()
        val visitor = read.responseHeaders.getFirst("Set-Cookie")
            ?.substringAfter("${AnonymousVisitorService.COOKIE_NAME}=")
            ?.substringBefore(";")
            ?: error("missing visitor cookie")

        repeat(2) {
            web.post().uri("/api/v2/shared/${share.token}/responses/${response.id}/like")
                .cookie(AnonymousVisitorService.COOKIE_NAME, visitor)
                .exchange().expectStatus().isOk
                .expectBody().jsonPath("$.anonymousLikeCount").isEqualTo(1)
        }
    }

    @Test
    fun `public shared read exposes no identity and revoked links 404`() {
        val owner = users.save(User(email = "owner-${UUID.randomUUID()}@example.com", passwordHash = "hash"))
        val connection = connections.save(Feature003Fixtures.connection(owner))
        val model = models.save(
            ConfiguredModel(user = owner, connection = connection, modelId = "shared-model", displayName = "Shared"),
        )
        val session = sessions.save(ChatSession(user = owner, title = "Shared chat"))
        val turn = turns.save(
            ChatTurn(
                session = session,
                sequenceNum = 1,
                promptText = "hello",
                selectedModelIds = arrayOf(model.modelId),
                selectedConfiguredModelIds = arrayOf(model.id),
                clientIp = "203.0.113.7",
            ),
        )
        val response = responses.save(
            ProviderResponse(
                turn = turn,
                modelId = model.modelId,
                configuredModelId = model.id,
                modelDisplayName = model.displayName,
                protocol = connection.protocol,
                connectionLabel = connection.label,
                connectionId = connection.id,
                status = "complete",
                responseText = "world",
                latencyMs = 10,
            ),
        )
        val ownerBearer = jwt.issue(owner.id, owner.sessionEpoch)
        val share = web.post().uri("/api/v2/chat/sessions/${session.id}/shares")
            .header("Authorization", "Bearer $ownerBearer")
            .exchange().expectStatus().isCreated
            .expectBody(ShareLinkDto::class.java)
            .returnResult().responseBody ?: error("missing share")

        // The anonymous-safe DTO must leak no owner identity, IP, connection, or named-like detail (FR-013/FR-015).
        val rawJson = web.get().uri("/api/v2/shared/${share.token}")
            .exchange().expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult().responseBody ?: error("missing body")
        listOf(
            owner.id.toString(),
            owner.email,
            "203.0.113.7",
            connection.id.toString(),
            "connectionId",
            "connectionLabel",
            "configuredModelId",
            "clientIp",
            "userId",
            "namedLikeCount",
        ).forEach { forbidden ->
            assert(!rawJson.contains(forbidden)) { "shared DTO must not expose '$forbidden': $rawJson" }
        }

        // A logged-in non-owner liking via the token-scoped endpoint is recorded as a NAMED like (FR-018).
        val visitor = users.save(User(email = "visitor-${UUID.randomUUID()}@example.com", passwordHash = "hash"))
        val visitorBearer = jwt.issue(visitor.id, visitor.sessionEpoch)
        web.put().uri("/api/v2/shared/${share.token}/responses/${response.id}/like")
            .header("Authorization", "Bearer $visitorBearer")
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.likeCount").isEqualTo(1)
            .jsonPath("$.likedByMe").isEqualTo(true)

        // Revoking the share makes both the public read and anonymous like inaccessible (FR-017).
        web.method(org.springframework.http.HttpMethod.DELETE)
            .uri("/api/v2/chat/sessions/${session.id}/shares/${share.token}")
            .header("Authorization", "Bearer $ownerBearer")
            .exchange().expectStatus().isNoContent
        web.get().uri("/api/v2/shared/${share.token}")
            .exchange().expectStatus().isNotFound
    }
}
