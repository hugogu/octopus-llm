package com.octopusllm.admin

import com.octopusllm.auth.JwtTokenService
import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import com.octopusllm.connection.ConfiguredModel
import com.octopusllm.connection.ConfiguredModelRepository
import com.octopusllm.connection.ConnectionRepository
import com.octopusllm.testsupport.AbstractPostgresIntegrationTest
import com.octopusllm.testsupport.Feature003Fixtures
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

class AdminModelAccessControllerTest @Autowired constructor(
    private val client: WebTestClient,
    private val users: UserRepository,
    private val connections: ConnectionRepository,
    private val models: ConfiguredModelRepository,
    private val jwt: JwtTokenService,
) : AbstractPostgresIntegrationTest() {
    @Test
    fun `public catalogue filters built-in anonymous models and bulk actions stay independent`() {
        val admin = users.save(
            User(
                email = "model-admin-${UUID.randomUUID()}@example.com",
                passwordHash = "hash",
                emailVerified = true,
                isAdmin = true,
                isActive = true,
            ),
        )
        val connection = Feature003Fixtures.connection(user = admin)
        val builtIn = com.octopusllm.connection.Connection(
            user = admin,
            protocol = connection.protocol,
            label = connection.label,
            baseUrl = connection.baseUrl,
            encryptedKey = connection.encryptedKey,
            keyIv = connection.keyIv,
            isBuiltin = true,
        )
        val savedConnection = connections.save(builtIn)
        val anonymousModel = models.save(
            ConfiguredModel(
                user = admin,
                connection = savedConnection,
                modelId = "anonymous-test-model",
                displayName = "Anonymous test model",
                isAnonymousAllowed = false,
            ),
        )
        val hiddenModel = models.save(
            ConfiguredModel(
                user = admin,
                connection = savedConnection,
                modelId = "hidden-test-model",
                displayName = "Hidden test model",
                isEnabled = false,
                isAnonymousAllowed = true,
            ),
        )
        val token = jwt.issue(admin.id, admin.sessionEpoch)

        client.get().uri("/api/v2/anonymous/models")
            .exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.items[?(@.id == '${anonymousModel.id}')]").doesNotExist()
            .jsonPath("$.items[?(@.id == '${hiddenModel.id}')]").doesNotExist()

        val preview = client.post().uri("/api/v2/admin/model-bulk-operations/preview")
            .header("Authorization", "Bearer $token")
            .bodyValue(
                mapOf(
                    "action" to "ALLOW_ANONYMOUS",
                    "selection" to mapOf("mode" to "IDS", "ids" to listOf(anonymousModel.id)),
                ),
            )
            .exchange().expectStatus().isCreated
            .expectBody().jsonPath("$.targetCount").isEqualTo(1)
            .returnResult().responseBody!!.let { String(it) }
        val operationId = Regex("\"operationId\":\"([^\"]+)\"").find(preview)!!.groupValues[1]

        client.post().uri("/api/v2/admin/model-bulk-operations/$operationId/execute")
            .header("Authorization", "Bearer $token")
            .header("Idempotency-Key", "anonymous-test-operation")
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.status").isEqualTo("COMPLETED")
            .jsonPath("$.changedCount").isEqualTo(1)

        client.get().uri("/api/v2/anonymous/models")
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.items[?(@.id == '${anonymousModel.id}')]").exists()

        // Allow/revoke is independent of display state: the hidden model remains hidden.
        val revokePreview = client.post().uri("/api/v2/admin/model-bulk-operations/preview")
            .header("Authorization", "Bearer $token")
            .bodyValue(
                mapOf(
                    "action" to "REVOKE_ANONYMOUS",
                    "selection" to mapOf("mode" to "IDS", "ids" to listOf(hiddenModel.id)),
                ),
            )
            .exchange().expectStatus().isCreated
            .expectBody().returnResult().responseBody!!.let { String(it) }
        val revokeOperationId = Regex("\"operationId\":\"([^\"]+)\"").find(revokePreview)!!.groupValues[1]
        client.post().uri("/api/v2/admin/model-bulk-operations/$revokeOperationId/execute")
            .header("Authorization", "Bearer $token")
            .header("Idempotency-Key", "anonymous-test-revoke")
            .exchange().expectStatus().isOk

        client.get().uri("/api/v2/anonymous/models")
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.items[?(@.id == '${hiddenModel.id}')]").doesNotExist()
    }
}
