package com.octopusllm.userconfig

import com.ninjasquad.springmockk.MockkBean
import com.octopusllm.auth.JwtTokenService
import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class UserConfigControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var jwtTokenService: JwtTokenService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userPreferenceRepository: UserPreferenceRepository

    companion object {
        @Container
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("app.jwt.secret") { "0123456789012345678901234567890123456789012345678901234567890123" }
            registry.add("app.jwt.expiry-seconds") { "3600" }
            registry.add("app.encryption.master-key") { "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=" }
            registry.add("app.frontend.url") { "http://localhost:3000" }
        }
    }

    private fun createUserAndToken(): Pair<User, String> {
        val user = userRepository.save(User(email = "config-${java.util.UUID.randomUUID()}@example.com", passwordHash = "hash", emailVerified = true))
        val token = jwtTokenService.issue(user.id, user.sessionEpoch)
        return user to token
    }

    @Test
    fun `get preferences returns default values for new user`() {
        val (_, token) = createUserAndToken()

        webTestClient.get()
            .uri("/api/v2/user/preferences")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.lastSelectedConfiguredModelId").isEmpty
            .jsonPath("$.themePreference").isEqualTo("system")
            .jsonPath("$.sidebarCollapsed").isEqualTo(false)
    }

    @Test
    fun `put preferences updates all fields`() {
        val (user, token) = createUserAndToken()
        val oldModelId = java.util.UUID.randomUUID()
        val newModelId = java.util.UUID.randomUUID()
        userPreferenceRepository.save(UserPreference(user = user, lastSelectedConfiguredModelId = oldModelId))

        val updateBody = mapOf(
            "lastSelectedConfiguredModelId" to newModelId,
            "themePreference" to "dark",
            "sidebarCollapsed" to true,
        )

        webTestClient.put()
            .uri("/api/v2/user/preferences")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updateBody)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.lastSelectedConfiguredModelId").isEqualTo(newModelId.toString())
            .jsonPath("$.themePreference").isEqualTo("dark")
            .jsonPath("$.sidebarCollapsed").isEqualTo(true)
    }

    @Test
    fun `patch preferences updates partial fields`() {
        val (user, token) = createUserAndToken()
        userPreferenceRepository.save(
            UserPreference(
                user = user,
                lastSelectedConfiguredModelId = java.util.UUID.randomUUID(),
                themePreference = "light",
                sidebarCollapsed = false,
            ),
        )

        val newModelId = java.util.UUID.randomUUID()
        val patchBody = mapOf("lastSelectedConfiguredModelId" to newModelId)

        webTestClient.patch()
            .uri("/api/v2/user/preferences")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(patchBody)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.lastSelectedConfiguredModelId").isEqualTo(newModelId.toString())
            .jsonPath("$.themePreference").isEqualTo("light")
            .jsonPath("$.sidebarCollapsed").isEqualTo(false)
    }

    @Test
    fun `put preferences with invalid theme returns bad request`() {
        val (_, token) = createUserAndToken()

        val updateBody = mapOf("themePreference" to "invalid-theme")

        webTestClient.put()
            .uri("/api/v2/user/preferences")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updateBody)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `get preferences without auth returns unauthorized`() {
        webTestClient.get()
            .uri("/api/v2/user/preferences")
            .exchange()
            .expectStatus().isUnauthorized
    }
}
