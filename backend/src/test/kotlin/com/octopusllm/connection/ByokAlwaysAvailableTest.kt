package com.octopusllm.connection

import com.octopusllm.auth.JwtTokenService
import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import com.octopusllm.testsupport.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

class ByokAlwaysAvailableTest @Autowired constructor(
    private val webTestClient: WebTestClient,
    private val userRepository: UserRepository,
    private val jwtTokenService: JwtTokenService,
) : AbstractPostgresIntegrationTest() {

    private val bcrypt = BCryptPasswordEncoder(12)

    @Test
    fun `a verified non-activated non-disabled user can create and use BYOK connections`() {
        val user = userRepository.save(
            User(
                email = "byok-${UUID.randomUUID()}@example.com",
                passwordHash = bcrypt.encode("Password123!"),
                emailVerified = true,
                isActive = false, // NOT administratively activated
                isDisabled = false,
            ),
        )
        val token = jwtTokenService.issue(user.id, user.sessionEpoch)

        val body = webTestClient.post().uri("/api/v2/connections")
            .header("Authorization", "Bearer $token")
            .bodyValue(
                mapOf(
                    "protocol" to "openai-compatible",
                    "baseUrl" to "https://8.8.8.8/v1",
                    "apiKey" to "byok-secret",
                ),
            )
            .exchange().expectStatus().isCreated
            .expectBody()
            .jsonPath("$.builtin").isEqualTo(false)
            .jsonPath("$.readOnly").isEqualTo(false)
            .returnResult().responseBody!!.let { String(it) }
        val connectionId = Regex("\"id\":\"([0-9a-f-]+)\"").find(body)!!.groupValues[1]

        webTestClient.post().uri("/api/v2/configured-models")
            .header("Authorization", "Bearer $token")
            .bodyValue(mapOf("connectionId" to connectionId, "modelId" to "m", "displayName" to "M"))
            .exchange().expectStatus().isCreated

        // No built-in connections are visible without activation+allocation.
        webTestClient.get().uri("/api/v2/connections")
            .header("Authorization", "Bearer $token")
            .exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.items[?(@.builtin == true)]").doesNotExist()
    }
}
