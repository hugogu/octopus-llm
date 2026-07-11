package com.octopusllm.tool

import com.octopusllm.userconfig.ApiKeyEncryptionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(ApiKeyEncryptionService::class)
class ToolSettingsServiceTest {

    @Autowired private lateinit var repository: ToolSettingsRepository
    @Autowired private lateinit var encryption: ApiKeyEncryptionService

    private val service by lazy {
        ToolSettingsService(repository, encryption, "https://base/v1", "model-x", envApiKey = "")
    }

    companion object {
        @Container
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            // ApiKeyEncryptionService needs a 32-byte base64 AES key.
            registry.add("app.encryption.master-key") { "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=" }
        }
    }

    @Test
    fun `seeds a disabled default from env base url and model`() {
        val settings = service.get()
        assertEquals("https://base/v1", settings.webSearchBaseUrl)
        assertEquals("model-x", settings.webSearchModel)
        assertNull(service.webSearchConfig()) // disabled by default
    }

    @Test
    fun `enabling requires a key and then resolves a decrypted config`() {
        val admin = UUID.randomUUID()
        // Missing key while enabling is rejected.
        assertThrows(ResponseStatusException::class.java) {
            service.update(admin, ToolSettingsUpdate(webSearchEnabled = true))
        }

        service.update(
            admin,
            ToolSettingsUpdate(webSearchEnabled = true, webSearchBaseUrl = "https://mimo/v1", webSearchModel = "m", webSearchApiKey = "secret-key"),
        )

        val config = service.webSearchConfig()!!
        assertEquals("https://mimo/v1", config.baseUrl)
        assertEquals("secret-key", config.apiKey) // decrypted round-trip
        // The stored key is encrypted, not plaintext.
        assert(service.get().webSearchApiKey!!.let { it != "secret-key" && it.contains(":") })
    }

    @Test
    fun `disabling makes the config unavailable without dropping the stored key`() {
        val admin = UUID.randomUUID()
        service.update(admin, ToolSettingsUpdate(webSearchEnabled = true, webSearchBaseUrl = "https://m/v1", webSearchModel = "m", webSearchApiKey = "k"))
        service.update(admin, ToolSettingsUpdate(webSearchEnabled = false))

        assertNull(service.webSearchConfig())
        assert(!service.get().webSearchApiKey.isNullOrBlank()) // key retained for re-enable
    }
}
