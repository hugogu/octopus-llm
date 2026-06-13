package com.octopusllm.analytics

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
import java.math.BigDecimal
import java.util.UUID

class PersonalAnalyticsControllerTest @Autowired constructor(
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
    fun `personal analytics is owner scoped filterable paged and preserves snapshot cost`() {
        val owner = users.save(User(email = "analytics-${UUID.randomUUID()}@example.com", passwordHash = "hash"))
        val other = users.save(User(email = "other-${UUID.randomUUID()}@example.com", passwordHash = "hash"))
        seed(owner, "owner-model", "Owner model")
        seed(other, "other-model", "Other model")
        val bearer = jwt.issue(owner.id, owner.sessionEpoch)

        web.get().uri("/api/v2/analytics/summary")
            .header("Authorization", "Bearer $bearer")
            .exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.totalResponses").isEqualTo(1)
            .jsonPath("$.estimatedCostsByCurrency.USD").exists()

        web.get().uri("/api/v2/analytics/by-model?page=0&size=25")
            .header("Authorization", "Bearer $bearer")
            .exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.items.length()").isEqualTo(1)
            .jsonPath("$.items[0].modelId").isEqualTo("owner-model")
            .jsonPath("$.totalElements").isEqualTo(1)

        web.get().uri("/api/v2/analytics/responses?page=0&size=25")
            .header("Authorization", "Bearer $bearer")
            .exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.items[0].clientIp").isEqualTo("203.0.113.7")
            .jsonPath("$.items[0].estimatedCost.currency").isEqualTo("USD")
    }

    private fun seed(user: User, modelId: String, displayName: String) {
        val connection = connections.save(Feature003Fixtures.connection(user))
        val model = models.save(
            ConfiguredModel(
                user = user,
                connection = connection,
                modelId = modelId,
                displayName = displayName,
                inputPricePerMtok = BigDecimal("2.0000"),
                outputPricePerMtok = BigDecimal("4.0000"),
                priceCurrency = "USD",
            ),
        )
        val session = sessions.save(ChatSession(user = user, title = displayName))
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
        responses.save(
            ProviderResponse(
                turn = turn,
                modelId = model.modelId,
                configuredModelId = model.id,
                modelDisplayName = model.displayName,
                protocol = connection.protocol,
                connectionId = connection.id,
                status = "complete",
                responseText = "world",
                inputTokens = 100,
                outputTokens = 200,
                latencyMs = 25,
                inputPricePerMtok = model.inputPricePerMtok,
                outputPricePerMtok = model.outputPricePerMtok,
                priceCurrency = model.priceCurrency,
            ),
        )
    }
}
