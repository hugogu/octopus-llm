package com.octopusllm.reaction

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
import com.octopusllm.testsupport.Feature003Fixtures
import com.octopusllm.testsupport.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

class ReactionControllerTest @Autowired constructor(
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
    fun `named like is owner scoped idempotent and toggleable`() {
        val user = users.save(User(email = "like-${UUID.randomUUID()}@example.com", passwordHash = "hash"))
        val connection = connections.save(Feature003Fixtures.connection(user))
        val model = models.save(
            ConfiguredModel(user = user, connection = connection, modelId = "liked-model", displayName = "Liked"),
        )
        val session = sessions.save(ChatSession(user = user, title = "Likes"))
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

        repeat(2) {
            web.put().uri("/api/v2/responses/${response.id}/like")
                .header("Authorization", "Bearer $bearer")
                .exchange().expectStatus().isOk
                .expectBody()
                .jsonPath("$.likeCount").isEqualTo(1)
                .jsonPath("$.likedByMe").isEqualTo(true)
        }

        web.delete().uri("/api/v2/responses/${response.id}/like")
            .header("Authorization", "Bearer $bearer")
            .exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.likeCount").isEqualTo(0)
            .jsonPath("$.likedByMe").isEqualTo(false)
    }
}
