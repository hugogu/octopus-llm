package com.octopusllm.chat

import com.ninjasquad.springmockk.MockkBean
import com.octopusllm.auth.JwtTokenService
import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import com.octopusllm.llm.CapabilityMatrix
import com.octopusllm.llm.ConcurrentLlmOrchestrator
import com.octopusllm.llm.LlmRequest
import com.octopusllm.llm.LlmStreamEvent
import com.octopusllm.llm.ModelDispatchTarget
import com.octopusllm.model.ModelDefinition
import com.octopusllm.model.ModelDefinitionRepository
import com.octopusllm.userconfig.ApiKeyEncryptionService
import com.octopusllm.userconfig.ProviderApiKey
import com.octopusllm.userconfig.ProviderApiKeyRepository
import com.octopusllm.userconfig.UserModelConfig
import com.octopusllm.userconfig.UserModelConfigRepository
import io.mockk.every
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
class ChatControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var jwtTokenService: JwtTokenService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var modelDefinitionRepository: ModelDefinitionRepository

    @Autowired
    private lateinit var providerApiKeyRepository: ProviderApiKeyRepository

    @Autowired
    private lateinit var userModelConfigRepository: UserModelConfigRepository

    @Autowired
    private lateinit var chatSessionRepository: ChatSessionRepository

    @Autowired
    private lateinit var chatTurnRepository: ChatTurnRepository

    @Autowired
    private lateinit var providerResponseRepository: ProviderResponseRepository

    @MockkBean
    private lateinit var orchestrator: ConcurrentLlmOrchestrator

    @MockkBean
    private lateinit var encryptionService: ApiKeyEncryptionService

    @Test
    fun `submit turn streams events and persists provider responses`() {
        val user = userRepository.save(
            User(email = "chat@example.com", passwordHash = "hash", emailVerified = true),
        )
        val session = chatSessionRepository.save(ChatSession(user = user, title = "session"))

        val openAiModel = modelDefinitionRepository.save(
            ModelDefinition(
                id = "gpt-4o-2024-11-20",
                providerId = "openai",
                displayName = "GPT-4o",
                capabilityMatrix = CapabilityMatrix(inputModalities = listOf("text", "image")),
            ),
        )
        val anthropicModel = modelDefinitionRepository.save(
            ModelDefinition(
                id = "claude-3-5-sonnet-20241022",
                providerId = "anthropic",
                displayName = "Claude 3.5 Sonnet",
                capabilityMatrix = CapabilityMatrix(inputModalities = listOf("text")),
            ),
        )

        val openAiKey = providerApiKeyRepository.save(
            ProviderApiKey(user = user, providerId = "openai", encryptedKey = byteArrayOf(1), keyIv = byteArrayOf(2)),
        )
        val anthropicKey = providerApiKeyRepository.save(
            ProviderApiKey(user = user, providerId = "anthropic", encryptedKey = byteArrayOf(3), keyIv = byteArrayOf(4)),
        )

        userModelConfigRepository.save(
            UserModelConfig(
                user = user,
                model = openAiModel,
                providerApiKey = openAiKey,
                customParams = mapOf("temperature" to 0.2),
            ),
        )
        userModelConfigRepository.save(UserModelConfig(user = user, model = anthropicModel, providerApiKey = anthropicKey))

        every { encryptionService.decrypt(any(), any()) } returns "decrypted-key"
        every {
            orchestrator.stream(
                match { targets ->
                    targets.firstOrNull { it.modelId == "gpt-4o-2024-11-20" }?.customParams?.get("temperature") == 0.2
                },
                any<LlmRequest>(),
            )
        } returns reactor.core.publisher.Flux.just(
            LlmStreamEvent.Token("gpt-4o-2024-11-20", "Hello"),
            LlmStreamEvent.Token("claude-3-5-sonnet-20241022", "Hi"),
            LlmStreamEvent.ModelComplete("gpt-4o-2024-11-20", 8, 12, 120L),
            LlmStreamEvent.ModelComplete("claude-3-5-sonnet-20241022", 7, 11, 150L),
        )

        val jwt = jwtTokenService.issue(user.id)

        val body = mapOf(
            "promptText" to "Say hello in 10 words",
            "selectedModelIds" to listOf("gpt-4o-2024-11-20", "claude-3-5-sonnet-20241022"),
            "clientRequestId" to "req-123",
        )

        val ssePayloads = webTestClient.post()
            .uri("/api/v1/chat/sessions/${session.id}/turns")
            .header("Authorization", "Bearer $jwt")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk
            .returnResult(String::class.java)
            .responseBody
            .collectList()
            .block()!!

        require(ssePayloads.any { it.contains("\"event\":\"turn_created\"") }) { "Expected turn_created event" }
        require(ssePayloads.any { it.contains("\"event\":\"token\"") && it.contains("gpt-4o-2024-11-20") }) {
            "Expected token event for GPT-4o"
        }
        require(ssePayloads.any { it.contains("\"event\":\"token\"") && it.contains("claude-3-5-sonnet-20241022") }) {
            "Expected token event for Claude"
        }
        require(ssePayloads.any { it.contains("\"event\":\"model_complete\"") && it.contains("gpt-4o-2024-11-20") }) {
            "Expected model_complete for GPT-4o"
        }
        require(ssePayloads.any { it.contains("\"event\":\"model_complete\"") && it.contains("claude-3-5-sonnet-20241022") }) {
            "Expected model_complete for Claude"
        }
        require(ssePayloads.last().contains("\"event\":\"all_complete\"")) { "Expected all_complete as final event" }

        val turn = waitForTurn(session.id)
        val responses = waitForResponses(turn.id)
        require(responses.size == 2) { "Expected two provider responses, got ${responses.size}" }
    }

    private fun waitForTurn(sessionId: java.util.UUID): ChatTurn {
        repeat(20) {
            val turns = chatTurnRepository.findBySessionIdOrderBySequenceNum(sessionId)
            if (turns.isNotEmpty()) return turns.single()
            Thread.sleep(50)
        }
        error("Timed out waiting for chat turn to be persisted")
    }

    private fun waitForResponses(turnId: java.util.UUID): List<ProviderResponse> {
        repeat(20) {
            val responses = providerResponseRepository.findByTurnId(turnId)
            if (responses.size == 2) return responses
            Thread.sleep(50)
        }
        error("Timed out waiting for provider responses to be persisted")
    }

    @Test
    fun `delete session removes session and all turns`() {
        val user = userRepository.save(
            User(email = "delete@example.com", passwordHash = "hash", emailVerified = true),
        )
        val session = chatSessionRepository.save(ChatSession(user = user, title = "To Delete"))
        val jwt = jwtTokenService.issue(user.id)

        webTestClient.delete()
            .uri("/api/v1/chat/sessions/${session.id}")
            .header("Authorization", "Bearer $jwt")
            .exchange()
            .expectStatus().isNoContent

        val found = chatSessionRepository.findById(session.id)
        require(!found.isPresent) { "Expected session to be deleted" }
    }

    @Test
    fun `delete session returns not found for other users session`() {
        val owner = userRepository.save(
            User(email = "owner@example.com", passwordHash = "hash", emailVerified = true),
        )
        val other = userRepository.save(
            User(email = "other@example.com", passwordHash = "hash", emailVerified = true),
        )
        val session = chatSessionRepository.save(ChatSession(user = owner, title = "Private"))
        val jwt = jwtTokenService.issue(other.id)

        webTestClient.delete()
            .uri("/api/v1/chat/sessions/${session.id}")
            .header("Authorization", "Bearer $jwt")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `create session with selectedModelId returns it in response`() {
        val user = userRepository.save(
            User(email = "model@example.com", passwordHash = "hash", emailVerified = true),
        )
        val jwt = jwtTokenService.issue(user.id)

        webTestClient.post()
            .uri("/api/v1/chat/sessions")
            .header("Authorization", "Bearer $jwt")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("title" to "Model Session", "selectedModelId" to "gpt-4o"))
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.title").isEqualTo("Model Session")
            .jsonPath("$.selectedModelId").isEqualTo("gpt-4o")
    }

    @Test
    fun `get session returns selectedModelId`() {
        val user = userRepository.save(
            User(email = "get@example.com", passwordHash = "hash", emailVerified = true),
        )
        val session = chatSessionRepository.save(
            ChatSession(user = user, title = "Test", selectedModelId = "claude-3"),
        )
        val jwt = jwtTokenService.issue(user.id)

        webTestClient.get()
            .uri("/api/v1/chat/sessions/${session.id}")
            .header("Authorization", "Bearer $jwt")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.selectedModelId").isEqualTo("claude-3")
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
