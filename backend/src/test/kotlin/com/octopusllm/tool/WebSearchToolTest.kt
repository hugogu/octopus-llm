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
    private var authHeader: String? = null

    @AfterEach
    fun stop() = server?.stop(0) ?: Unit

    private fun start(status: Int, responseBody: String, capture: (String) -> Unit = {}) {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1/chat/completions") { exchange: HttpExchange ->
                apiKeyHeader = exchange.requestHeaders.getFirst("api-key")
                authHeader = exchange.requestHeaders.getFirst("Authorization")
                capture(exchange.requestBody.use { String(it.readAllBytes(), StandardCharsets.UTF_8) })
                val bytes = responseBody.toByteArray(StandardCharsets.UTF_8)
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
    }

    private fun tool(config: WebSearchRuntimeConfig?): WebSearchTool {
        val settings = mockk<ToolSettingsService> { every { webSearchConfig() } returns config }
        return WebSearchTool(settings, mapper, limit = 3)
    }

    private fun configuredTool(provider: String = "mimo") = tool(
        WebSearchRuntimeConfig(
            provider = provider,
            baseUrl = "http://127.0.0.1:${server!!.address.port}/v1",
            model = "test-model",
            apiKey = "test-key",
        ),
    )

    @Test
    fun `unavailable and fails clearly when not configured`() {
        val webSearch = tool(null)
        assertFalse(webSearch.isAvailable())
        assertTrue(webSearch.execute(mapOf("query" to "x")) is ToolResult.Failure)
    }

    @Test
    fun `mimo uses api-key header, web_search tool, flat annotations`() {
        var body: String? = null
        start(
            200,
            """{"choices":[{"message":{"content":"茅台约 1680。","annotations":[
              {"type":"url_citation","url":"https://x.com/a","title":"行情","summary":"1680"}]}}]}""",
        ) { body = it }

        val result = configuredTool("mimo").execute(mapOf("query" to "贵州茅台今天股价")) as ToolResult.Success

        assertEquals("test-key", apiKeyHeader)
        val sent = mapper.readTree(body)
        assertEquals("web_search", sent.path("tools").path(0).path("type").asText())
        assertTrue(sent.path("tools").path(0).path("force_search").asBoolean())
        assertEquals("茅台约 1680。", result.data["answer"])
        assertTrue((result.data["endpoint"] as String).endsWith("/chat/completions"))
        @Suppress("UNCHECKED_CAST")
        val citations = result.data["citations"] as List<Map<String, Any?>>
        assertEquals("https://x.com/a", citations.single()["url"])
        assertEquals("1680", citations.single()["summary"])
    }

    @Test
    fun `openrouter uses bearer, web plugin, and nested annotations`() {
        var body: String? = null
        start(
            200,
            """{"choices":[{"message":{"content":"Mars news.","annotations":[
              {"type":"url_citation","url_citation":{"url":"https://o.com/x","title":"O","content":"nested body"}}]}}]}""",
        ) { body = it }

        val result = configuredTool("openrouter").execute(mapOf("query" to "mars rover news")) as ToolResult.Success

        assertEquals("Bearer test-key", authHeader)
        val sent = mapper.readTree(body)
        assertEquals("web", sent.path("plugins").path(0).path("id").asText())
        assertTrue(sent.path("tools").isMissingNode)
        @Suppress("UNCHECKED_CAST")
        val citations = result.data["citations"] as List<Map<String, Any?>>
        assertEquals("https://o.com/x", citations.single()["url"])
        assertEquals("nested body", citations.single()["summary"])
    }

    @Test
    fun `glm uses bearer, web_search tool, and top-level web_search results`() {
        var body: String? = null
        start(
            200,
            """{"choices":[{"message":{"content":"答案"}}],"web_search":[
              {"title":"G","link":"https://g.cn/y","content":"glm body"}]}""",
        ) { body = it }

        val result = configuredTool("glm").execute(mapOf("query" to "上海天气")) as ToolResult.Success

        assertEquals("Bearer test-key", authHeader)
        val sent = mapper.readTree(body)
        assertEquals("web_search", sent.path("tools").path(0).path("type").asText())
        assertTrue(sent.path("tools").path(0).path("web_search").path("enable").asBoolean())
        @Suppress("UNCHECKED_CAST")
        val citations = result.data["citations"] as List<Map<String, Any?>>
        assertEquals("https://g.cn/y", citations.single()["url"])
    }

    @Test
    fun `missing query fails without calling the provider`() {
        val result = tool(WebSearchRuntimeConfig("mimo", "http://127.0.0.1:1/v1", "m", "k")).execute(emptyMap())
        assertTrue(result is ToolResult.Failure)
    }

    @Test
    fun `a provider error is surfaced as a failure with the URL`() {
        start(503, """{"error":"unavailable"}""")

        val failure = configuredTool("mimo").execute(mapOf("query" to "上海天气")) as ToolResult.Failure

        assertTrue(failure.errorMessage.contains("503"))
        assertTrue(failure.errorMessage.contains("/chat/completions"))
    }
}
