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

    private fun start(status: Int, responseBody: String, path: String = "/v1/chat/completions", capture: (String) -> Unit = {}) {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext(path) { exchange: HttpExchange ->
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

    /** Serves the given responses in order across successive requests (for multi-round flows). */
    private fun startRounds(vararg responses: String, capture: (String) -> Unit = {}) {
        val idx = java.util.concurrent.atomic.AtomicInteger(0)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1/chat/completions") { exchange: HttpExchange ->
                authHeader = exchange.requestHeaders.getFirst("Authorization")
                capture(exchange.requestBody.use { String(it.readAllBytes(), StandardCharsets.UTF_8) })
                val i = idx.getAndIncrement().coerceAtMost(responses.size - 1)
                val bytes = responses[i].toByteArray(StandardCharsets.UTF_8)
                exchange.sendResponseHeaders(200, bytes.size.toLong())
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
    fun `mimo uses api-key header and top-level web_search feature`() {
        var body: String? = null
        start(
            200,
            """{"choices":[{"message":{"content":"茅台约 1680。","annotations":[
              {"type":"url_citation","url":"https://x.com/a","title":"行情","summary":"1680"}]}}]}""",
        ) { body = it }

        val result = configuredTool("mimo").execute(mapOf("query" to "贵州茅台今天股价")) as ToolResult.Success

        assertEquals("test-key", apiKeyHeader)
        val sent = mapper.readTree(body)
        assertTrue(sent.path("web_search").path("enable").asBoolean())
        assertTrue(sent.path("web_search").path("force_search").asBoolean())
        assertTrue(sent.path("tools").isMissingNode)
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
    fun `glm uses bearer and dedicated web_search endpoint`() {
        var body: String? = null
        start(
            status = 200,
            responseBody = """{"id":"1","created":1,"search_result":[
              {"title":"G","content":"glm body","link":"https://g.cn/y","media":"","icon":"","refer":"","publish_date":""}]}""",
            path = "/v1/web_search",
        ) { body = it }

        val result = configuredTool("glm").execute(mapOf("query" to "上海天气")) as ToolResult.Success

        assertEquals("Bearer test-key", authHeader)
        val sent = mapper.readTree(body)
        assertEquals("search-prime", sent.path("search_engine").asText())
        assertEquals("上海天气", sent.path("search_query").asText())
        assertEquals(3, sent.path("count").asInt())
        assertTrue((result.data["endpoint"] as String).endsWith("/web_search"))
        @Suppress("UNCHECKED_CAST")
        val citations = result.data["citations"] as List<Map<String, Any?>>
        assertEquals("https://g.cn/y", citations.single()["url"])
        assertEquals("glm body", result.data["answer"])
    }

    @Test
    fun `tavily uses bearer, dedicated search endpoint, and results array`() {
        var body: String? = null
        start(
            status = 200,
            responseBody = """{"query":"mars","answer":"Mars answer.","results":[
              {"title":"T","url":"https://t.io/1","content":"tavily body","score":0.9}]}""",
            path = "/v1/search",
        ) { body = it }

        val result = configuredTool("tavily").execute(mapOf("query" to "mars rover")) as ToolResult.Success

        assertEquals("Bearer test-key", authHeader)
        val sent = mapper.readTree(body)
        assertEquals("mars rover", sent.path("query").asText())
        assertTrue(sent.path("include_answer").asBoolean())
        assertEquals(3, sent.path("max_results").asInt())
        assertTrue((result.data["endpoint"] as String).endsWith("/search"))
        assertEquals("Mars answer.", result.data["answer"])
        @Suppress("UNCHECKED_CAST")
        val citations = result.data["citations"] as List<Map<String, Any?>>
        assertEquals("https://t.io/1", citations.single()["url"])
        assertEquals("tavily body", citations.single()["summary"])
    }

    @Test
    fun `kimi runs the builtin web_search two-round handshake`() {
        val ws = "${'$'}web_search"
        val requests = mutableListOf<String>()
        startRounds(
            // Round 1: the model requests the builtin $web_search.
            """{"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":"","tool_calls":[
              {"id":"ws:0","type":"function","function":{"name":"$ws","arguments":"{\"query\":\"mars\"}"}}]}}]}""",
            // Round 2: the final answer after the server-side search.
            """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"Mars answer."}}]}""",
            capture = { requests.add(it) },
        )

        val result = tool(
            WebSearchRuntimeConfig("kimi", "http://127.0.0.1:${server!!.address.port}/v1", "kimi-k2.6", "test-key"),
        ).execute(mapOf("query" to "mars rover")) as ToolResult.Success

        assertEquals("Bearer test-key", authHeader)
        assertEquals("Mars answer.", result.data["answer"])
        // Round 1 advertises the builtin tool...
        val round1 = mapper.readTree(requests[0])
        assertEquals("builtin_function", round1.path("tools").path(0).path("type").asText())
        assertEquals(ws, round1.path("tools").path(0).path("function").path("name").asText())
        // ...round 2 echoes the tool_call arguments back as a tool message.
        val round2Messages = mapper.readTree(requests[1]).path("messages")
        val toolMsg = (0 until round2Messages.size()).map { round2Messages.path(it) }.single { it.path("role").asText() == "tool" }
        assertEquals("ws:0", toolMsg.path("tool_call_id").asText())
        assertEquals(ws, toolMsg.path("name").asText())
        assertTrue(toolMsg.path("content").asText().contains("mars"))
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
