package com.octopusllm.admin

import com.octopusllm.auth.JwtTokenService
import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import com.octopusllm.connection.ConfiguredModelRepository
import com.octopusllm.testsupport.AbstractPostgresIntegrationTest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

class AdminConnectionControllerTest @Autowired constructor(
    private val webTestClient: WebTestClient,
    private val userRepository: UserRepository,
    private val configuredModelRepository: ConfiguredModelRepository,
    private val jwtTokenService: JwtTokenService,
) : AbstractPostgresIntegrationTest() {

    private val bcrypt = BCryptPasswordEncoder(12)

    private fun newUser(admin: Boolean = false, active: Boolean = false): User =
        userRepository.save(
            User(
                email = "c-${UUID.randomUUID()}@example.com",
                passwordHash = bcrypt.encode("Password123!"),
                emailVerified = true,
                isAdmin = admin,
                isActive = active,
            ),
        )

    private fun token(user: User) = jwtTokenService.issue(user.id, user.sessionEpoch)

    private fun clearGuestDefaults() {
        val defaults = configuredModelRepository.findAll().filter { it.isAnonymousDefault }
        defaults.forEach { it.isAnonymousDefault = false }
        configuredModelRepository.saveAll(defaults)
    }

    private fun createBuiltin(adminToken: String): String =
        webTestClient.post().uri("/api/v2/admin/connections")
            .header("Authorization", "Bearer $adminToken")
            .bodyValue(
                mapOf(
                    "protocol" to "openai-compatible",
                    "baseUrl" to "https://8.8.8.8/v1",
                    "apiKey" to "super-secret-key",
                    "label" to "Shared",
                ),
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.hasKey").isEqualTo(true)
            .jsonPath("$.apiKey").doesNotExist()
            .returnResult()
            .let { String(it.responseBody!!) }
            .let { Regex("\"id\":\"([0-9a-f-]+)\"").find(it)!!.groupValues[1] }

    @Test
    fun `create lists and never returns the key`() {
        val admin = newUser(admin = true, active = true)
        val adminToken = token(admin)
        createBuiltin(adminToken)
        webTestClient.get().uri("/api/v2/admin/connections")
            .header("Authorization", "Bearer $adminToken")
            .exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.items").isArray
            .jsonPath("$.items[0].hasKey").isEqualTo(true)
    }

    @Test
    fun `endpoint-models is admin-gated and 404s for an unknown built-in connection`() {
        val admin = newUser(admin = true, active = true)
        webTestClient.get().uri("/api/v2/admin/connections/${UUID.randomUUID()}/endpoint-models")
            .header("Authorization", "Bearer ${token(admin)}")
            .exchange().expectStatus().isNotFound

        val regular = newUser(active = true)
        webTestClient.get().uri("/api/v2/admin/connections/${UUID.randomUUID()}/endpoint-models")
            .header("Authorization", "Bearer ${token(regular)}")
            .exchange().expectStatus().isForbidden
    }

    @Test
    fun `allocation requires an activated user`() {
        val admin = newUser(admin = true, active = true)
        val adminToken = token(admin)
        val connectionId = createBuiltin(adminToken)
        val inactive = newUser(active = false)

        webTestClient.put().uri("/api/v2/admin/connections/$connectionId/allocations/${inactive.id}")
            .header("Authorization", "Bearer $adminToken")
            .exchange().expectStatus().isEqualTo(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY)
    }

    @Test
    fun `allocated user sees the built-in read-only and cannot mutate it - revoke removes access`() {
        val admin = newUser(admin = true, active = true)
        val adminToken = token(admin)
        val connectionId = createBuiltin(adminToken)

        // add a model so it is usable
        webTestClient.post().uri("/api/v2/admin/connections/$connectionId/models")
            .header("Authorization", "Bearer $adminToken")
            .bodyValue(mapOf("modelId" to "gpt-test", "displayName" to "GPT Test"))
            .exchange().expectStatus().isCreated

        val user = newUser(active = true)
        val userToken = token(user)

        webTestClient.put().uri("/api/v2/admin/connections/$connectionId/allocations/${user.id}")
            .header("Authorization", "Bearer $adminToken")
            .exchange().expectStatus().isNoContent

        // user sees it read-only
        webTestClient.get().uri("/api/v2/connections")
            .header("Authorization", "Bearer $userToken")
            .exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.items[?(@.id == '$connectionId' && @.builtin == true && @.readOnly == true)]").exists()

        // the built-in model is selectable for chat (appears in the flat configured-models list)
        webTestClient.get().uri("/api/v2/configured-models")
            .header("Authorization", "Bearer $userToken")
            .exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.items[?(@.connectionId == '$connectionId' && @.modelId == 'gpt-test')]").exists()

        // user cannot mutate a connection they do not own
        webTestClient.patch().uri("/api/v2/connections/$connectionId")
            .header("Authorization", "Bearer $userToken")
            .bodyValue(mapOf("label" to "hijack"))
            .exchange().expectStatus().isNotFound

        // revoke removes access
        webTestClient.delete().uri("/api/v2/admin/connections/$connectionId/allocations/${user.id}")
            .header("Authorization", "Bearer $adminToken")
            .exchange().expectStatus().isNoContent

        webTestClient.get().uri("/api/v2/connections")
            .header("Authorization", "Bearer $userToken")
            .exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.items[?(@.id == '$connectionId')]").doesNotExist()
    }

    @Test
    fun `admin can toggle anonymous access for a built-in model`() {
        clearGuestDefaults()
        val admin = newUser(admin = true, active = true)
        val adminToken = token(admin)
        val connectionId = createBuiltin(adminToken)
        val modelId = webTestClient.post().uri("/api/v2/admin/connections/$connectionId/models")
            .header("Authorization", "Bearer $adminToken")
            .bodyValue(mapOf("modelId" to "anonymous-toggle-test", "displayName" to "Anonymous toggle test"))
            .exchange().expectStatus().isCreated
            .expectBody().returnResult().responseBody!!
            .let { Regex("\"id\":\"([0-9a-f-]+)\"").find(String(it))!!.groupValues[1] }

        webTestClient.patch().uri("/api/v2/admin/connections/$connectionId/models/$modelId")
            .header("Authorization", "Bearer $adminToken")
            .bodyValue(mapOf("isAnonymousAllowed" to true))
            .exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.isAnonymousAllowed").isEqualTo(true)

        webTestClient.patch().uri("/api/v2/admin/connections/$connectionId/models/$modelId")
            .header("Authorization", "Bearer $adminToken")
            .bodyValue(mapOf("isAnonymousDefault" to true))
            .exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.isAnonymousDefault").isEqualTo(true)

        webTestClient.get().uri("/api/v2/admin/connections/$connectionId/models")
            .header("Authorization", "Bearer $adminToken")
            .exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.items[?(@.id == '$modelId')].isAnonymousAllowed").isEqualTo(true)
            .jsonPath("$.items[?(@.id == '$modelId')].isAnonymousDefault").isEqualTo(true)
    }

    @Test
    fun `guest defaults require anonymous eligibility and are capped at three`() {
        clearGuestDefaults()
        val admin = newUser(admin = true, active = true)
        val adminToken = token(admin)
        val connectionId = createBuiltin(adminToken)
        val modelIds = (1..4).map { index ->
            webTestClient.post().uri("/api/v2/admin/connections/$connectionId/models")
                .header("Authorization", "Bearer $adminToken")
                .bodyValue(mapOf("modelId" to "guest-default-$index", "displayName" to "Guest default $index"))
                .exchange().expectStatus().isCreated
                .expectBody().returnResult().responseBody!!
                .let { Regex("\"id\":\"([0-9a-f-]+)\"").find(String(it))!!.groupValues[1] }
        }

        webTestClient.patch().uri("/api/v2/admin/connections/$connectionId/models/${modelIds.first()}")
            .header("Authorization", "Bearer $adminToken")
            .bodyValue(mapOf("isAnonymousDefault" to true))
            .exchange().expectStatus().isEqualTo(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY)

        modelIds.take(3).forEach { modelId ->
            webTestClient.patch().uri("/api/v2/admin/connections/$connectionId/models/$modelId")
                .header("Authorization", "Bearer $adminToken")
                .bodyValue(mapOf("isAnonymousAllowed" to true, "isAnonymousDefault" to true))
                .exchange().expectStatus().isOk
                .expectBody().jsonPath("$.isAnonymousDefault").isEqualTo(true)
        }

        webTestClient.patch().uri("/api/v2/admin/connections/$connectionId/models/${modelIds[3]}")
            .header("Authorization", "Bearer $adminToken")
            .bodyValue(mapOf("isAnonymousAllowed" to true, "isAnonymousDefault" to true))
            .exchange().expectStatus().isEqualTo(org.springframework.http.HttpStatus.CONFLICT)

        val publicModels = webTestClient.get().uri("/api/v2/anonymous/models?size=100")
            .exchange().expectStatus().isOk
            .expectBody().returnResult().responseBody!!
        val publicIds = jacksonObjectMapper().readTree(publicModels).path("items").map { it.path("id").asText() }
        assertEquals(modelIds.take(3).toSet(), publicIds.take(3).toSet())
    }
}
