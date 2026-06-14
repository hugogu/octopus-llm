package com.octopusllm.model

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Cross-provider model capability source (feature 007). OpenRouter's public `/models` list exposes
 * `architecture.input_modalities` per model; this indexes them by a normalized bare model id so our
 * models (e.g. `gpt-4o`, `glm-4v`) can be matched against OpenRouter ids (e.g. `openai/gpt-4o`). The
 * index is cached with a TTL; the API key is optional (the list endpoint is public) and only sent when
 * configured.
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

    private data class Cache(val index: Map<String, List<String>>, val loadedAt: Instant)
    private val cache = AtomicReference<Cache?>(null)

    companion object {
        private val SUPPORTED = setOf("text", "image", "video", "audio")

        fun normalize(id: String): String = id.substringAfterLast('/').substringBefore(':').trim().lowercase()

        /** Build a normalized-id → modalities index from an OpenRouter models payload. Pure + testable. */
        fun buildIndex(objectMapper: ObjectMapper, jsonBody: String): Map<String, List<String>> {
            val root = objectMapper.readTree(jsonBody)
            val index = HashMap<String, List<String>>()
            root.path("data").forEach { model ->
                val id = model.path("id").asText("").ifBlank { return@forEach }
                val modalities = collectModalities(model)
                if (modalities.isEmpty()) return@forEach
                val key = normalize(id)
                val existing = index[key]
                // On bare-id collisions keep the most capable variant (favor surfacing the feature).
                if (existing == null || modalities.size > existing.size) index[key] = modalities
            }
            return index
        }

        private fun collectModalities(model: JsonNode): List<String> {
            val raw = model.path("architecture").path("input_modalities")
                .mapNotNull { it.asText("").takeIf { v -> v.isNotBlank() } }
                .filter { it in SUPPORTED }
                .distinct()
            // Ensure "text" is present whenever any non-text modality was detected.
            return if (raw.isEmpty()) emptyList() else (listOf("text") + raw.filter { it != "text" })
        }
    }

    /** Modalities for a model id from the cached index, or null if unknown / index not loaded. */
    fun modalitiesFor(modelId: String): List<String>? = cache.get()?.index?.get(normalize(modelId))

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
