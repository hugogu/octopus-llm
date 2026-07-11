package com.octopusllm.tool

import com.octopusllm.userconfig.ApiKeyEncryptionService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Base64
import java.util.UUID

/** Resolved, decrypted web_search provider config for runtime use. [provider] selects the request shape. */
data class WebSearchRuntimeConfig(val provider: String, val baseUrl: String, val model: String, val apiKey: String)

/** Admin-supplied update for a single provider's config (feature 009). Null fields are left unchanged. */
data class WebSearchProviderUpdate(
    val baseUrl: String? = null,
    val model: String? = null,
    val apiKey: String? = null,
)

/** Admin-supplied update for the web_search activation (which provider is on, and whether enabled). */
data class ToolSettingsActivationUpdate(
    val webSearchEnabled: Boolean? = null,
    val webSearchActiveProvider: String? = null,
)

/**
 * Reads/updates the admin-managed tool configuration. Each web_search provider's url/model/key is stored
 * in its own row so they coexist; [ToolSettings] holds only the enabled flag and the active provider.
 * First read seeds a MiMo provider row from the deploy-time `app.tools.web-search.*` env (backward
 * compatibility). Provider keys are stored encrypted and only decrypted for runtime execution.
 */
@Service
class ToolSettingsService(
    private val repository: ToolSettingsRepository,
    private val providerRepository: WebSearchProviderSettingsRepository,
    private val encryptionService: ApiKeyEncryptionService,
    @Value("\${app.tools.web-search.base-url:https://token-plan-cn.xiaomimimo.com/v1}") private val defaultBaseUrl: String,
    @Value("\${app.tools.web-search.model:mimo-v2.5-pro}") private val defaultModel: String,
    @Value("\${app.tools.web-search.api-key:}") private val envApiKey: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun get(): ToolSettings =
        repository.findById(ToolSettings.SINGLETON_ID).orElseGet {
            val settings = repository.save(ToolSettings())
            if (envApiKey.isNotBlank() && providerRepository.findByProvider("mimo") == null) {
                providerRepository.save(
                    WebSearchProviderSettings(
                        provider = "mimo",
                        baseUrl = defaultBaseUrl,
                        model = defaultModel,
                        apiKey = encryptSecret(envApiKey),
                    ),
                )
                settings.webSearchEnabled = true
                repository.save(settings)
            }
            settings
        }

    /** All saved provider rows, keyed by provider id. */
    fun providerConfigs(): Map<String, WebSearchProviderSettings> =
        providerRepository.findAll().associateBy { it.provider }

    /** Decrypted config for the active provider, or null when disabled or incompletely configured. */
    fun webSearchConfig(): WebSearchRuntimeConfig? {
        val settings = get()
        if (!settings.webSearchEnabled) return null
        val row = providerRepository.findByProvider(settings.webSearchActiveProvider) ?: return null
        val key = row.apiKey?.let { runCatching { decryptSecret(it) }.getOrNull() }
        if (row.baseUrl.isNullOrBlank() || key.isNullOrBlank() ||
            (WebSearchProviders.needsModel(settings.webSearchActiveProvider) && row.model.isNullOrBlank())
        ) {
            return null
        }
        return WebSearchRuntimeConfig(settings.webSearchActiveProvider, row.baseUrl!!, row.model.orEmpty(), key)
    }

    @Transactional
    fun updateProvider(adminId: UUID, provider: String, req: WebSearchProviderUpdate): WebSearchProviderSettings {
        val row = providerRepository.findByProvider(provider) ?: WebSearchProviderSettings(provider = provider)
        req.baseUrl?.let { row.baseUrl = it }
        req.model?.let { row.model = it }
        req.apiKey?.takeIf { it.isNotBlank() }?.let { row.apiKey = encryptSecret(it) }
        row.updatedBy = adminId
        row.updatedAt = Instant.now()
        log.info("web_search_provider_updated by={} provider={}", adminId.toString().take(8), provider)
        return providerRepository.save(row)
    }

    @Transactional
    fun updateActivation(adminId: UUID, req: ToolSettingsActivationUpdate): ToolSettings {
        val settings = get()
        val activeProvider = req.webSearchActiveProvider ?: settings.webSearchActiveProvider
        val enabled = req.webSearchEnabled ?: settings.webSearchEnabled

        if (enabled) {
            val row = providerRepository.findByProvider(activeProvider)
            val hasKey = !row?.apiKey.isNullOrBlank()
            if (row == null || row.baseUrl.isNullOrBlank() || !hasKey ||
                (WebSearchProviders.needsModel(activeProvider) && row.model.isNullOrBlank())
            ) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "provider '$activeProvider' is not fully configured (base URL, API key" +
                        "${if (WebSearchProviders.needsModel(activeProvider)) ", model" else ""} required)",
                )
            }
        }

        settings.webSearchActiveProvider = activeProvider
        settings.webSearchEnabled = enabled
        settings.updatedBy = adminId
        settings.updatedAt = Instant.now()
        log.info("tool_settings_updated by={} active={} enabled={}", adminId.toString().take(8), activeProvider, enabled)
        return repository.save(settings)
    }

    private fun encryptSecret(plain: String): String {
        val enc = encryptionService.encrypt(plain)
        val b64 = Base64.getEncoder()
        return "${b64.encodeToString(enc.iv)}:${b64.encodeToString(enc.ciphertext)}"
    }

    private fun decryptSecret(stored: String): String {
        val parts = stored.split(":", limit = 2)
        require(parts.size == 2) { "malformed stored secret" }
        val dec = Base64.getDecoder()
        return encryptionService.decrypt(dec.decode(parts[1]), dec.decode(parts[0]))
    }
}
