package com.octopusllm.migration

import com.octopusllm.auth.JwtTokenService
import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import com.octopusllm.chat.ChatSession
import com.octopusllm.chat.ChatSessionRepository
import com.octopusllm.chat.ChatTurn
import com.octopusllm.chat.ChatTurnRepository
import com.octopusllm.connection.ConfiguredModel
import com.octopusllm.connection.ConfiguredModelRepository
import com.octopusllm.connection.Connection
import com.octopusllm.connection.ConnectionRepository
import com.octopusllm.testsupport.AbstractPostgresIntegrationTest
import com.octopusllm.userconfig.ApiKeyEncryptionService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import java.util.UUID

/**
 * HTTP-level coverage for the admin migration endpoints (feature 008, T017–T019): admin gating,
 * acknowledgement/passphrase validation, an encrypted export → multipart import round-trip, and the
 * wrong-passphrase / missing-idempotency-key rejection paths.
 */
class MigrationControllerTest @Autowired constructor(
    private val webTestClient: WebTestClient,
    private val userRepository: UserRepository,
    private val jwtTokenService: JwtTokenService,
    private val connectionRepository: ConnectionRepository,
    private val configuredModelRepository: ConfiguredModelRepository,
    private val sessionRepository: ChatSessionRepository,
    private val turnRepository: ChatTurnRepository,
    private val encryptionService: ApiKeyEncryptionService,
) : AbstractPostgresIntegrationTest() {

    private val passphrase = "controller-passphrase-123456"

    private fun newUser(admin: Boolean) = userRepository.save(
        User(email = "u-${UUID.randomUUID()}@example.com", passwordHash = "h", emailVerified = true, isAdmin = admin),
    )

    private fun token(user: User) = jwtTokenService.issue(user.id, user.sessionEpoch)

    private fun seedQuest() {
        val owner = newUser(admin = false)
        val enc = encryptionService.encrypt("sk-${UUID.randomUUID().toString().take(6)}")
        val connection = connectionRepository.save(
            Connection(
                user = owner, protocol = "openai-compatible", label = "Conn-${UUID.randomUUID().toString().take(6)}",
                baseUrl = "https://8.8.8.8/v1", encryptedKey = enc.ciphertext, keyIv = enc.iv,
            ),
        )
        val model = configuredModelRepository.save(
            ConfiguredModel(user = owner, connection = connection, modelId = "gpt-4o", displayName = "GPT"),
        )
        val session = sessionRepository.save(ChatSession(user = owner, title = "Q-${UUID.randomUUID().toString().take(6)}"))
        turnRepository.save(
            ChatTurn(
                session = session, sequenceNum = 1, promptText = "hi",
                selectedModelIds = arrayOf("gpt-4o"), selectedConfiguredModelIds = arrayOf(model.id),
            ),
        )
    }

    private fun exportArtifact(adminToken: String): ByteArray =
        webTestClient.post().uri("/api/v2/admin/migration/export")
            .header("Authorization", "Bearer $adminToken")
            .bodyValue(mapOf("acknowledgeSensitiveExport" to true, "passphrase" to passphrase))
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType("application/zip")
            .expectBody().returnResult().responseBody!!

    @Test
    fun `export is admin-gated`() {
        webTestClient.post().uri("/api/v2/admin/migration/export")
            .header("Authorization", "Bearer ${token(newUser(admin = false))}")
            .bodyValue(mapOf("acknowledgeSensitiveExport" to true, "passphrase" to passphrase))
            .exchange().expectStatus().isForbidden
    }

    @Test
    fun `export requires acknowledgement and a long-enough passphrase`() {
        val adminToken = token(newUser(admin = true))
        webTestClient.post().uri("/api/v2/admin/migration/export")
            .header("Authorization", "Bearer $adminToken")
            .bodyValue(mapOf("acknowledgeSensitiveExport" to false, "passphrase" to passphrase))
            .exchange().expectStatus().isBadRequest

        webTestClient.post().uri("/api/v2/admin/migration/export")
            .header("Authorization", "Bearer $adminToken")
            .bodyValue(mapOf("acknowledgeSensitiveExport" to true, "passphrase" to "short"))
            .exchange().expectStatus().isBadRequest

        webTestClient.post().uri("/api/v2/admin/migration/export")
            .header("Authorization", "Bearer $adminToken")
            .bodyValue(mapOf("acknowledgeSensitiveExport" to true))
            .exchange().expectStatus().isBadRequest
    }

    @Test
    fun `round-trips through export and multipart import`() {
        seedQuest()
        val artifact = exportArtifact(token(newUser(admin = true)))

        val importer = newUser(admin = true)
        val body = MultipartBodyBuilder().apply {
            part("file", artifact).header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=file; filename=a.octopus")
            part("passphrase", passphrase)
        }.build()

        webTestClient.post().uri("/api/v2/admin/migration/import")
            .header("Authorization", "Bearer ${token(importer)}")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .body(BodyInserters.fromMultipartData(body))
            .exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.questsImported").value<Int> { assert(it >= 1) }
            .jsonPath("$.connectionsImported").value<Int> { assert(it >= 1) }
            .jsonPath("$.formatVersion").isEqualTo(1)
    }

    @Test
    fun `import rejects a wrong passphrase and a missing idempotency key`() {
        seedQuest()
        val artifact = exportArtifact(token(newUser(admin = true)))
        val importerToken = token(newUser(admin = true))

        val wrongPass = MultipartBodyBuilder().apply {
            part("file", artifact).header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=file; filename=a.octopus")
            part("passphrase", "totally-wrong-passphrase-9999")
        }.build()
        webTestClient.post().uri("/api/v2/admin/migration/import")
            .header("Authorization", "Bearer $importerToken")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .body(BodyInserters.fromMultipartData(wrongPass))
            .exchange().expectStatus().isBadRequest

        val noKey = MultipartBodyBuilder().apply {
            part("file", artifact).header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=file; filename=a.octopus")
            part("passphrase", passphrase)
        }.build()
        webTestClient.post().uri("/api/v2/admin/migration/import")
            .header("Authorization", "Bearer $importerToken")
            .body(BodyInserters.fromMultipartData(noKey))
            .exchange().expectStatus().isBadRequest
    }
}
