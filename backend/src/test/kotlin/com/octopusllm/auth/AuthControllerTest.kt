package com.octopusllm.auth

import com.ninjasquad.springmockk.MockkBean
import io.mockk.justRun
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
class AuthControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var emailVerificationRepository: EmailVerificationRepository

    @MockkBean(relaxed = true)
    private lateinit var emailService: EmailService

    @Test
    fun `register verify login and logout flow works end to end`() {
        justRun { emailService.sendVerificationEmail(any(), any()) }

        val registerBody = mapOf("email" to "test@example.com", "password" to "Test1234!")
        webTestClient.post()
            .uri("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerBody)
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.message").isEqualTo("Registration successful. Check your email to verify.")

        val user = userRepository.findByEmail("test@example.com")
        requireNotNull(user) { "Expected registered user to be persisted" }
        val token = emailVerificationRepository.findAll().single().token

        webTestClient.post()
            .uri("/api/v1/auth/verify-email")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("token" to token))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.message").isEqualTo("Email verified. You may now log in.")

        val loginResponse = webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerBody)
            .exchange()
            .expectStatus().isOk
            .expectBody(LoginResponse::class.java)
            .returnResult()
            .responseBody

        val bearer = loginResponse?.token
        require(!bearer.isNullOrBlank()) { "Expected to extract JWT from login response body" }

        webTestClient.post()
            .uri("/api/v1/chat/sessions")
            .header("Authorization", "Bearer $bearer")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("title" to "auth-check"))
            .exchange()
            .expectStatus().isCreated

        webTestClient.post()
            .uri("/api/v1/auth/logout")
            .header("Authorization", "Bearer $bearer")
            .exchange()
            .expectStatus().isNoContent

        webTestClient.post()
            .uri("/api/v1/chat/sessions")
            .header("Authorization", "Bearer $bearer")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("title" to "after-logout"))
            .exchange()
            .expectStatus().isUnauthorized
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("app.jwt.secret") { "0123456789012345678901234567890123456789012345678901234567890123" }
            registry.add("app.jwt.expiry-seconds") { "3600" }
            registry.add("app.encryption.master-key") { "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=" }
            registry.add("app.frontend.url") { "http://localhost:3000" }
        }
    }
}
