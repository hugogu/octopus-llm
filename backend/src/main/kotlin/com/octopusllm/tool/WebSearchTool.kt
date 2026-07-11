package com.octopusllm.tool

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Built-in `web_search` tool (feature 009). Backed by a provider that performs the search server-side
 * (MiMo-style `{"type":"web_search"}` on an OpenAI-shaped chat endpoint): we send the query, the provider
 * searches and answers, and we return that answer plus source citations. Being an app-side [Tool], it is
 * available to *any* function-calling model through the unified loop, and subsumes weather/stock/news
 * lookups.
 *
 * The provider is admin-configured ([ToolSettingsService]); the tool advertises itself only while
 * configured/enabled, and execution failures degrade gracefully. The endpoint is included in every
 * outcome so a failure can be reproduced.
 */
@Component
class WebSearchTool(
    private val toolSettings: ToolSettingsService,
    private val objectMapper: ObjectMapper,
    @Value("\${app.tools.web-search.limit:3}") private val limit: Int = 3,
    private val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
) : Tool {
    override val definition = ToolDefinition(
        name = "web_search",
        description = "Search the web for up-to-date information — current news, prices, weather, sports, " +
            "or any fact that may have changed since training. Returns a concise answer with source citations.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "query" to mapOf(
                    "type" to "string",
                    "description" to "The search query, in natural language (e.g. '贵州茅台今天股价' or '上海明天天气').",
                ),
            ),
            "required" to listOf("query"),
        ),
    )

    override fun isAvailable(): Boolean = toolSettings.webSearchConfig() != null

    override fun execute(arguments: Map<String, Any?>): ToolResult {
        val config = toolSettings.webSearchConfig()
            ?: return ToolResult.Failure("web_search 未配置（请在管理后台 → Tools 配置搜索 Provider）")
        val query = (arguments["query"] as? String)?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Failure("web_search requires a non-empty 'query'")

        val endpoint = "${config.baseUrl.trimEnd('/')}/chat/completions"
        val body = objectMapper.writeValueAsString(
            mapOf(
                "model" to config.model,
                "messages" to listOf(mapOf("role" to "user", "content" to query)),
                // Provider-side web search; force it so the tool always performs a live lookup.
                "tools" to listOf(mapOf("type" to "web_search", "force_search" to true, "limit" to limit)),
                "stream" to false,
            ),
        )
        val request = HttpRequest.newBuilder(URI.create(endpoint))
            // MiMo authenticates with an `api-key` header, not `Authorization: Bearer`.
            .header("api-key", config.apiKey)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(14))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        // Surface the endpoint in every outcome so a failure can be reproduced/verified.
        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (error: Exception) {
            val reason = error.message ?: error.javaClass.simpleName
            return ToolResult.Failure("web search request failed (POST $endpoint): $reason")
        }
        if (response.statusCode() !in 200..299) {
            return ToolResult.Failure("web search returned HTTP ${response.statusCode()} (POST $endpoint)")
        }

        val message = objectMapper.readTree(response.body()).path("choices").path(0).path("message")
        val answer = message.path("content").asText("")
        val citations = message.path("annotations")
            .filter { it.path("type").asText() == "url_citation" }
            .map {
                mapOf(
                    "url" to it.path("url").asText(""),
                    "title" to it.path("title").asText(""),
                    "summary" to it.path("summary").asText(""),
                )
            }
        if (answer.isBlank() && citations.isEmpty()) {
            return ToolResult.Failure("web search returned no results (POST $endpoint)")
        }
        return ToolResult.Success(mapOf("answer" to answer, "citations" to citations, "endpoint" to endpoint))
    }
}
