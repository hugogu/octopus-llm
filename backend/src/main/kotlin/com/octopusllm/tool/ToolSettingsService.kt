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

/** Admin-supplied tool-settings update (feature 009). Null fields are left unchanged. */
data class ToolSettingsUpdate(
    val webSearchEnabled: Boolean? = null,
    val webSearchProvider: String? = null,
    val webSearchBaseUrl: String? = null,
    val webSearchModel: String? = null,
    val webSearchApiKey: String? = null,
)

/**
 * Reads/updates the admin-managed tool configuration. First read seeds a row from the deploy-time
 * `app.tools.web-search.*` env (so existing env config keeps working), after which the admin panel is
 * authoritative. The provider key is stored encrypted and only ever decrypted for runtime execution.
 */
@Service
class ToolSettingsService(
    private val repository: ToolSettingsRepository,
    private val encryptionService: ApiKeyEncryptionService,
    @Value("\${app.tools.web-search.base-url:https://token-plan-cn.xiaomimimo.com/v1}") private val defaultBaseUrl: String,
    @Value("\${app.tools.web-search.model:mimo-v2.5-pro}") private val defaultModel: String,
    @Value("\${app.tools.web-search.api-key:}") private val envApiKey: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun get(): ToolSettings =
        repository.findById(ToolSettings.SINGLETON_ID).orElseGet {
            val seed = ToolSettings(webSearchBaseUrl = defaultBaseUrl, webSearchModel = defaultModel)
            if (envApiKey.isNotBlank()) {
                seed.webSearchApiKey = encryptSecret(envApiKey)
                seed.webSearchEnabled = true
            }
            repository.save(seed)
        }

    /** Decrypted web_search config, or null when the tool is disabled or incompletely configured. */
    fun webSearchConfig(): WebSearchRuntimeConfig? {
        val s = get()
        if (!s.webSearchEnabled) return null
        val key = s.webSearchApiKey?.let { runCatching { decryptSecret(it) }.getOrNull() }
        if (s.webSearchBaseUrl.isNullOrBlank() || key.isNullOrBlank() ||
            (needsModel(s.webSearchProvider) && s.webSearchModel.isNullOrBlank())
        ) {
            return null
        }
        return WebSearchRuntimeConfig(s.webSearchProvider, s.webSearchBaseUrl!!, s.webSearchModel.orEmpty(), key)
    }

    /** Chat-completions-style providers need a model; dedicated search APIs (glm, tavily) do not. */
    private fun needsModel(provider: String): Boolean =
        provider == "mimo" || provider == "mimo-standard" || provider == "openrouter" || provider == "kimi"

    @Transactional
    fun update(adminId: UUID, req: ToolSettingsUpdate): ToolSettings {
        val s = get()
        val enabled = req.webSearchEnabled ?: s.webSearchEnabled
        val baseUrl = req.webSearchBaseUrl ?: s.webSearchBaseUrl
        val model = req.webSearchModel ?: s.webSearchModel
        val newKeyPlain = req.webSearchApiKey?.takeIf { it.isNotBlank() }
        val hasKey = newKeyPlain != null || !s.webSearchApiKey.isNullOrBlank()

        val provider = req.webSearchProvider ?: s.webSearchProvider
        if (enabled && (baseUrl.isNullOrBlank() || !hasKey || (needsModel(provider) && model.isNullOrBlank()))) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "web_search requires a base URL and API key (and a model for chat-based providers) when enabled",
            )
        }

        req.webSearchProvider?.let { s.webSearchProvider = it }
        req.webSearchBaseUrl?.let { s.webSearchBaseUrl = it }
        req.webSearchModel?.let { s.webSearchModel = it }
        newKeyPlain?.let { s.webSearchApiKey = encryptSecret(it) }
        s.webSearchEnabled = enabled
        s.updatedBy = adminId
        s.updatedAt = Instant.now()
        log.info("tool_settings_updated by={} web_search_enabled={}", adminId.toString().take(8), enabled)
        return repository.save(s)
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
