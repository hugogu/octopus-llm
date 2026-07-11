package com.octopusllm.tool

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class WebSearchToolTest {
    private val mapper = jacksonObjectMapper()
    private var server: HttpServer? = null
    private var apiKeyHeader: String? = null

    @AfterEach
    fun stop() = server?.stop(0) ?: Unit

    private fun start(status: Int, responseBody: String, capture: (String) -> Unit = {}) {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1/chat/completions") { exchange: HttpExchange ->
                apiKeyHeader = exchange.requestHeaders.getFirst("api-key")
                capture(exchange.requestBody.use { String(it.readAllBytes(), StandardCharsets.UTF_8) })
                val bytes = responseBody.toByteArray(StandardCharsets.UTF_8)
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
    }

    /** A tool wired to a settings service that returns the given config (or null when unconfigured). */
    private fun tool(config: WebSearchRuntimeConfig?): WebSearchTool {
        val settings = mockk<ToolSettingsService> { every { webSearchConfig() } returns config }
        return WebSearchTool(settings, mapper, limit = 3)
    }

    private fun configuredTool() = tool(
        WebSearchRuntimeConfig(
            baseUrl = "http://127.0.0.1:${server!!.address.port}/v1",
            model = "mimo-v2.5-pro",
            apiKey = "test-key",
        ),
    )

    @Test
    fun `unavailable and fails clearly when not configured`() {
        val webSearch = tool(null)
        assertFalse(webSearch.isAvailable())
        val result = webSearch.execute(mapOf("query" to "x"))
        assertTrue(result is ToolResult.Failure)
    }

    @Test
    fun `sends a web_search request and returns answer with citations`() {
        var body: String? = null
        start(
            200,
            """
            {"choices":[{"message":{"content":"茅台今日约 1680 元。","annotations":[
              {"type":"url_citation","url":"https://x.com/a","title":"行情","summary":"1680"}
            ]}}]}
            """.trimIndent(),
        ) { body = it }

        val result = configuredTool().execute(mapOf("query" to "贵州茅台今天股价")) as ToolResult.Success

        assertEquals("test-key", apiKeyHeader)
        val sent = mapper.readTree(body)
        assertEquals("web_search", sent.path("tools").path(0).path("type").asText())
        assertTrue(sent.path("tools").path(0).path("force_search").asBoolean())
        assertEquals("mimo-v2.5-pro", sent.path("model").asText())
        assertEquals("茅台今日约 1680 元。", result.data["answer"])
        assertTrue((result.data["endpoint"] as String).endsWith("/chat/completions"))
        @Suppress("UNCHECKED_CAST")
        val citations = result.data["citations"] as List<Map<String, Any?>>
        assertEquals("https://x.com/a", citations.single()["url"])
    }

    @Test
    fun `missing query fails without calling the provider`() {
        // Configured, but the query guard returns before any HTTP call — no server needed.
        val result = tool(WebSearchRuntimeConfig("http://127.0.0.1:1/v1", "m", "k")).execute(emptyMap())
        assertTrue(result is ToolResult.Failure)
    }

    @Test
    fun `a provider error is surfaced as a failure with the URL`() {
        start(503, """{"error":"unavailable"}""")

        val result = configuredTool().execute(mapOf("query" to "上海天气"))

        val failure = result as ToolResult.Failure
        assertTrue(failure.errorMessage.contains("503"))
        assertTrue(failure.errorMessage.contains("/chat/completions"))
    }
}
