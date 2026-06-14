package com.octopusllm.model

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Detected metadata for one model (feature 007), sourced from OpenRouter's model list. Modalities,
 * pricing (per 1M tokens, USD), context length, and tool/function-calling support are all populated
 * fill-only by capability detection.
 */
data class DetectedModelInfo(
    val modalities: List<String>,
    val inputPricePerMtok: BigDecimal?,
    val outputPricePerMtok: BigDecimal?,
    val contextLengthTokens: Int?,
    val supportsFunctionCalling: Boolean,
)

/**
 * Cross-provider model metadata source (feature 007). OpenRouter's public `/models` list exposes per
 * model: `architecture.input_modalities`, `pricing` (per-token USD), `context_length`, and
 * `supported_parameters`. This indexes them by a normalized bare model id so our models (e.g.
 * `gpt-4o`) match OpenRouter ids (e.g. `openai/gpt-4o`). Cached with a TTL; the API key is optional.
 */
@Component
class OpenRouterModelCatalogue(
    private val objectMapper: ObjectMapper,
    @Value("\${openrouter.base-url:https://openrouter.ai/api/v1}") private val baseUrl: String,
    @Value("\${openrouter.api-key:}") private val apiKey: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ttl = Duration.ofHours(6)
    private val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    private data class Cache(val index: Map<String, DetectedModelInfo>, val loadedAt: Instant)
    private val cache = AtomicReference<Cache?>(null)

    companion object {
        private val SUPPORTED = setOf("text", "image", "video", "audio")
        private val MILLION = BigDecimal(1_000_000)

        fun normalize(id: String): String = id.substringAfterLast('/').substringBefore(':').trim().lowercase()

        /** Build a normalized-id → info index from an OpenRouter models payload. Pure + testable. */
        fun buildIndex(objectMapper: ObjectMapper, jsonBody: String): Map<String, DetectedModelInfo> {
            val root = objectMapper.readTree(jsonBody)
            val index = HashMap<String, DetectedModelInfo>()
            root.path("data").forEach { model ->
                val id = model.path("id").asText("").ifBlank { return@forEach }
                val modalities = collectModalities(model)
                if (modalities.isEmpty()) return@forEach
                val info = DetectedModelInfo(
                    modalities = modalities,
                    inputPricePerMtok = perMillion(model.path("pricing").path("prompt")),
                    outputPricePerMtok = perMillion(model.path("pricing").path("completion")),
                    contextLengthTokens = model.path("context_length").asInt(0).takeIf { it > 0 },
                    supportsFunctionCalling = model.path("supported_parameters")
                        .any { it.asText() == "tools" },
                )
                val key = normalize(id)
                val existing = index[key]
                // On bare-id collisions keep the most capable variant (favor surfacing the feature).
                if (existing == null || info.modalities.size > existing.modalities.size) index[key] = info
            }
            return index
        }

        private fun collectModalities(model: JsonNode): List<String> {
            val raw = model.path("architecture").path("input_modalities")
                .mapNotNull { it.asText("").takeIf { v -> v.isNotBlank() } }
                .filter { it in SUPPORTED }
                .distinct()
            return if (raw.isEmpty()) emptyList() else (listOf("text") + raw.filter { it != "text" })
        }

        /** Convert an OpenRouter per-token USD price string to a per-1M-token BigDecimal (4 dp). */
        private fun perMillion(node: JsonNode): BigDecimal? {
            val text = node.asText("").trim()
            if (text.isEmpty()) return null
            return runCatching { BigDecimal(text).multiply(MILLION).setScale(4, RoundingMode.HALF_UP) }.getOrNull()
        }
    }

    fun infoFor(modelId: String): DetectedModelInfo? = cache.get()?.index?.get(normalize(modelId))

    fun modalitiesFor(modelId: String): List<String>? = infoFor(modelId)?.modalities

    /** Refresh the index if empty or stale. Returns true if an index is available afterward. */
    fun ensureFresh(): Boolean {
        val existing = cache.get()
        if (existing != null && Duration.between(existing.loadedAt, Instant.now()) < ttl) return true
        return runCatching { refresh() }
            .onFailure { log.warn("openrouter_models_fetch_failed: {}", it.message) }
            .isSuccess
    }

    @Synchronized
    private fun refresh() {
        val request = HttpRequest.newBuilder(URI.create("${baseUrl.trimEnd('/')}/models"))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/json")
            .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey") }
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "OpenRouter HTTP ${response.statusCode()}" }
        val index = buildIndex(objectMapper, response.body())
        cache.set(Cache(index, Instant.now()))
        log.info("openrouter_models_indexed count={}", index.size)
    }
}
