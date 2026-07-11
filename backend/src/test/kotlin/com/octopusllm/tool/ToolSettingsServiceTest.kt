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
    @Autowired private lateinit var providerRepository: WebSearchProviderSettingsRepository
    @Autowired private lateinit var encryption: ApiKeyEncryptionService

    private val service by lazy {
        ToolSettingsService(repository, providerRepository, encryption, "https://base/v1", "model-x", envApiKey = "")
    }
    private val admin = UUID.randomUUID()

    companion object {
        @Container
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("app.encryption.master-key") { "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=" }
        }
    }

    @Test
    fun `defaults to disabled with no active config`() {
        service.get()
        assertNull(service.webSearchConfig())
    }

    @Test
    fun `configuring a provider then activating it resolves a decrypted config`() {
        service.updateProvider(admin, "openrouter", WebSearchProviderUpdate(baseUrl = "https://or/v1", model = "m", apiKey = "or-key"))
        // Not active/enabled yet.
        assertNull(service.webSearchConfig())

        service.updateActivation(admin, ToolSettingsActivationUpdate(webSearchEnabled = true, webSearchActiveProvider = "openrouter"))

        val config = service.webSearchConfig()!!
        assertEquals("openrouter", config.provider)
        assertEquals("or-key", config.apiKey) // decrypted round-trip
        // Stored encrypted, not plaintext.
        assert(providerRepository.findByProvider("openrouter")!!.apiKey!!.let { it != "or-key" && it.contains(":") })
    }

    @Test
    fun `enabling an unconfigured active provider is rejected`() {
        assertThrows(ResponseStatusException::class.java) {
            service.updateActivation(admin, ToolSettingsActivationUpdate(webSearchEnabled = true, webSearchActiveProvider = "kimi"))
        }
    }

    @Test
    fun `providers coexist and keys are retained when switching the active provider`() {
        service.updateProvider(admin, "tavily", WebSearchProviderUpdate(baseUrl = "https://api.tavily.com", apiKey = "tvly"))
        service.updateProvider(admin, "openrouter", WebSearchProviderUpdate(baseUrl = "https://or/v1", model = "m", apiKey = "or-key"))

        // Tavily needs no model; activating it works.
        service.updateActivation(admin, ToolSettingsActivationUpdate(webSearchEnabled = true, webSearchActiveProvider = "tavily"))
        assertEquals("tavily", service.webSearchConfig()!!.provider)

        // Switch active to OpenRouter without re-entering its key.
        service.updateActivation(admin, ToolSettingsActivationUpdate(webSearchActiveProvider = "openrouter"))
        val config = service.webSearchConfig()!!
        assertEquals("openrouter", config.provider)
        assertEquals("or-key", config.apiKey)
        // Tavily's config is still there.
        assertEquals("tvly", providerRepository.findByProvider("tavily")!!.apiKey!!.let { encryptedRoundTrip(it) })
    }

    // Helper: confirm the stored value decrypts back to the original via the service's scheme.
    private fun encryptedRoundTrip(stored: String): String {
        val parts = stored.split(":", limit = 2)
        val dec = java.util.Base64.getDecoder()
        return encryption.decrypt(dec.decode(parts[1]), dec.decode(parts[0]))
    }
}
