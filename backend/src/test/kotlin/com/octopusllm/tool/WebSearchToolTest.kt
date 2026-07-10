package com.octopusllm.tool

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class WebSearchToolTest {
    private val mapper = jacksonObjectMapper()
    private var server: HttpServer? = null

    @AfterEach
    fun stop() = server?.stop(0) ?: Unit

    private fun start(status: Int, responseBody: String, capture: (String) -> Unit = {}) {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1/chat/completions") { exchange: HttpExchange ->
                capture(exchange.requestBody.use { String(it.readAllBytes(), StandardCharsets.UTF_8) })
                val bytes = responseBody.toByteArray(StandardCharsets.UTF_8)
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
    }

    private fun tool() = WebSearchTool(
        apiKey = "test-key",
        baseUrl = "http://127.0.0.1:${server!!.address.port}/v1",
        model = "mimo-v2.5",
        limit = 3,
        objectMapper = mapper,
    )

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

        val result = tool().execute(mapOf("query" to "贵州茅台今天股价")) as ToolResult.Success

        val sent = mapper.readTree(body)
        assertEquals("web_search", sent.path("tools").path(0).path("type").asText())
        assertTrue(sent.path("tools").path(0).path("force_search").asBoolean())
        assertEquals("茅台今日约 1680 元。", result.data["answer"])
        @Suppress("UNCHECKED_CAST")
        val citations = result.data["citations"] as List<Map<String, Any?>>
        assertEquals("https://x.com/a", citations.single()["url"])
    }

    @Test
    fun `missing query fails without calling the provider`() {
        val result = WebSearchTool("k", "http://127.0.0.1:1/v1", "m", 3, mapper).execute(emptyMap())
        assertTrue(result is ToolResult.Failure)
    }

    @Test
    fun `a provider error is surfaced as a failure`() {
        start(503, """{"error":"unavailable"}""")

        val result = tool().execute(mapOf("query" to "上海天气"))

        val failure = result as ToolResult.Failure
        assertTrue(failure.errorMessage.contains("503"))
    }
}
