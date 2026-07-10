package com.octopusllm.llm.adapter

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.octopusllm.llm.Attachment
import com.octopusllm.llm.HistoryTurn
import com.octopusllm.llm.LlmRequest
import com.octopusllm.llm.LlmStreamEvent
import com.octopusllm.llm.ToolCallRef
import com.octopusllm.tool.ToolDefinition
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class StreamingAdaptersTest {
    private val mapper = jacksonObjectMapper()
    private var server: HttpServer? = null
    private var serverExecutor: ExecutorService? = null

    @AfterEach
    fun stopServer() {
        server?.stop(0)
        serverExecutor?.shutdownNow()
    }

    @Test
    fun `openai stream sends compatible request and maps split SSE chunks`() {
        var captured: CapturedRequest? = null
        startServer("/v1/chat/completions") { exchange ->
            captured = capture(exchange)
            sendSse(
                exchange,
                listOf(
                    """data: {"choices":[{"delta":{"content":"你好"}}]}""" + "\n\n",
                    """data: {"choices":[{"delta":{"reasoning_content":"think"}}]}""" + "\n\n",
                    """data: {"choices":[],"usage":{"prompt_tokens":7,"completion_tokens":3}}""" + "\n\n",
                    "data: [DONE]\n\n",
                ),
                splitFirstChunk = true,
            )
        }

        val events = OpenAiCompatAdapter(mapper).stream(
            modelId = "provider-model",
            request = LlmRequest(
                prompt = "describe",
                attachments = listOf(Attachment("image", "YWJj", "image/png")),
                customParams = mapOf("temperature" to 0.2, "ignored" to null),
            ),
            decryptedApiKey = "test-secret",
            baseUrlOverride = baseUrl("/v1"),
        ).collectList().block()!!

        assertEquals("Bearer test-secret", captured!!.headers["Authorization"])
        val body = mapper.readTree(captured!!.body)
        assertEquals("provider-model", body.path("model").asText())
        assertTrue(body.path("stream").asBoolean())
        assertEquals(0.2, body.path("temperature").asDouble())
        assertFalse(body.has("ignored"))
        assertEquals("image_url", body.path("messages").path(0).path("content").path(1).path("type").asText())
        assertEquals(
            listOf(
                LlmStreamEvent.Token("provider-model", "你好"),
                LlmStreamEvent.Reasoning("provider-model", "think"),
            ),
            events.dropLast(1),
        )
        val complete = events.last() as LlmStreamEvent.ModelComplete
        assertEquals(7, complete.inputTokens)
        assertEquals(3, complete.outputTokens)
    }

    @Test
    fun `openai stream prepends the system prompt as a leading system message`() {
        var captured: CapturedRequest? = null
        startServer("/v1/chat/completions") { exchange ->
            captured = capture(exchange)
            sendSse(exchange, listOf("""data: {"choices":[{"delta":{"content":"ok"}}]}""" + "\n\n", "data: [DONE]\n\n"))
        }

        OpenAiCompatAdapter(mapper).stream(
            modelId = "provider-model",
            request = LlmRequest(prompt = "hi", systemPrompt = "当前日期与时间：2026-07-10"),
            decryptedApiKey = "test-secret",
            baseUrlOverride = baseUrl("/v1"),
        ).collectList().block()!!

        val messages = mapper.readTree(captured!!.body).path("messages")
        assertEquals("system", messages.path(0).path("role").asText())
        assertEquals("当前日期与时间：2026-07-10", messages.path(0).path("content").asText())
        assertEquals("user", messages.path(1).path("role").asText())
    }

    @Test
    fun `minimax stream prepends the system prompt as a leading system message`() {
        var captured: CapturedRequest? = null
        startServer("/text/chatcompletion_v2") { exchange ->
            captured = capture(exchange)
            sendSse(
                exchange,
                listOf(
                    """data: {"choices":[{"delta":{"content":"ok"},"finish_reason":"stop"}]}""" + "\n\n",
                    "data: [DONE]\n\n",
                ),
            )
        }

        MiniMaxAdapter().stream(
            modelId = "minimax-m",
            request = LlmRequest(prompt = "hi", systemPrompt = "当前日期与时间：2026-07-10"),
            decryptedApiKey = "secret",
            baseUrlOverride = baseUrl(""),
        ).collectList().block()!!

        val messages = mapper.readTree(captured!!.body).path("messages")
        assertEquals("system", messages.path(0).path("role").asText())
        assertEquals("当前日期与时间：2026-07-10", messages.path(0).path("content").asText())
    }

    @Test
    fun `openai stream advertises tools and maps streamed tool_calls across chunks`() {
        var captured: CapturedRequest? = null
        startServer("/v1/chat/completions") { exchange ->
            captured = capture(exchange)
            sendSse(
                exchange,
                listOf(
                    """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"current_time","arguments":"{\"timezone"}}]}}]}""" + "\n\n",
                    """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\":\"UTC\"}"}}]}}]}""" + "\n\n",
                    """data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":5,"completion_tokens":2}}""" + "\n\n",
                    "data: [DONE]\n\n",
                ),
            )
        }

        val events = OpenAiCompatAdapter(mapper).stream(
            modelId = "provider-model",
            request = LlmRequest(
                prompt = "what time is it?",
                tools = listOf(
                    ToolDefinition(
                        "current_time",
                        "Return the current time",
                        mapOf("type" to "object", "properties" to emptyMap<String, Any>()),
                    ),
                ),
            ),
            decryptedApiKey = "test-secret",
            baseUrlOverride = baseUrl("/v1"),
        ).collectList().block()!!

        val body = mapper.readTree(captured!!.body)
        assertEquals("function", body.path("tools").path(0).path("type").asText())
        assertEquals("current_time", body.path("tools").path(0).path("function").path("name").asText())

        val toolCall = events.filterIsInstance<LlmStreamEvent.ToolCall>().single()
        assertEquals("call_1", toolCall.callId)
        assertEquals("current_time", toolCall.toolName)
        assertEquals(mapOf("timezone" to "UTC"), toolCall.arguments)
        // The tool call is emitted before the terminal completion event.
        assertTrue(events.indexOf(toolCall) < events.indexOfFirst { it is LlmStreamEvent.ModelComplete })
    }

    @Test
    fun `openai serializes assistant tool_calls and tool-result history`() {
        var captured: CapturedRequest? = null
        startServer("/v1/chat/completions") { exchange ->
            captured = capture(exchange)
            sendSse(exchange, listOf("""data: {"choices":[{"delta":{"content":"10:30"}}]}""" + "\n\n", "data: [DONE]\n\n"))
        }

        OpenAiCompatAdapter(mapper).stream(
            modelId = "provider-model",
            request = LlmRequest(
                prompt = "and in words?",
                history = listOf(
                    HistoryTurn(role = "user", text = "what time is it?"),
                    HistoryTurn(
                        role = "assistant",
                        text = "",
                        toolCalls = listOf(ToolCallRef("call_1", "current_time", mapOf("timezone" to "UTC"))),
                    ),
                    HistoryTurn(role = "tool", text = """{"time":"10:30"}""", toolCallId = "call_1"),
                ),
            ),
            decryptedApiKey = "test-secret",
            baseUrlOverride = baseUrl("/v1"),
        ).collectList().block()!!

        val messages = mapper.readTree(captured!!.body).path("messages")
        val assistant = (0 until messages.size()).map { messages.path(it) }.single { it.path("role").asText() == "assistant" }
        assertEquals("call_1", assistant.path("tool_calls").path(0).path("id").asText())
        assertEquals("current_time", assistant.path("tool_calls").path(0).path("function").path("name").asText())
        val toolMsg = (0 until messages.size()).map { messages.path(it) }.single { it.path("role").asText() == "tool" }
        assertEquals("call_1", toolMsg.path("tool_call_id").asText())
        assertEquals("""{"time":"10:30"}""", toolMsg.path("content").asText())
    }

    @Test
    fun `anthropic stream sends the system prompt as a top-level field`() {
        var captured: CapturedRequest? = null
        startServer("/v1/messages") { exchange ->
            captured = capture(exchange)
            sendSse(
                exchange,
                listOf(
                    """data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"ok"}}""" + "\n\n",
                    """data: {"type":"message_stop"}""" + "\n\n",
                ),
            )
        }

        AnthropicAdapter(mapper).stream(
            modelId = "claude-test",
            request = LlmRequest(prompt = "hi", systemPrompt = "当前日期与时间：2026-07-10"),
            decryptedApiKey = "anthropic-secret",
            baseUrlOverride = baseUrl(""),
        ).collectList().block()!!

        val body = mapper.readTree(captured!!.body)
        assertEquals("当前日期与时间：2026-07-10", body.path("system").asText())
        // System is a top-level field, not a message role, for Anthropic.
        assertEquals("user", body.path("messages").path(0).path("role").asText())
    }

    @Test
    fun `anthropic stream sends messages request and maps token usage`() {
        var captured: CapturedRequest? = null
        startServer("/v1/messages") { exchange ->
            captured = capture(exchange)
            sendSse(
                exchange,
                listOf(
                    """data: {"type":"message_start","message":{"usage":{"input_tokens":11}}}""" + "\n\n",
                    """data: {"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"plan"}}""" + "\n\n",
                    """data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"answer"}}""" + "\n\n",
                    """data: {"type":"message_delta","usage":{"output_tokens":5}}""" + "\n\n",
                    """data: {"type":"message_stop"}""" + "\n\n",
                ),
            )
        }

        val events = AnthropicAdapter(mapper).stream(
            modelId = "claude-test",
            request = LlmRequest("hello"),
            decryptedApiKey = "anthropic-secret",
            baseUrlOverride = baseUrl(""),
        ).collectList().block()!!

        assertEquals("anthropic-secret", captured!!.headers["X-api-key"])
        assertEquals("2023-06-01", captured!!.headers["Anthropic-version"])
        val body = mapper.readTree(captured!!.body)
        assertEquals("claude-test", body.path("model").asText())
        assertEquals(4096, body.path("max_tokens").asInt())
        assertTrue(body.path("stream").asBoolean())
        assertEquals(
            listOf(
                LlmStreamEvent.Reasoning("claude-test", "plan"),
                LlmStreamEvent.Token("claude-test", "answer"),
            ),
            events.dropLast(1),
        )
        val complete = events.last() as LlmStreamEvent.ModelComplete
        assertEquals(11, complete.inputTokens)
        assertEquals(5, complete.outputTokens)
    }

    @Test
    fun `anthropic stream captures normalized cache read and write tokens`() {
        startServer("/v1/messages") { exchange ->
            capture(exchange)
            sendSse(
                exchange,
                listOf(
                    """data: {"type":"message_start","message":{"usage":{"input_tokens":11,"cache_read_input_tokens":1024,"cache_creation_input_tokens":256}}}""" + "\n\n",
                    """data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"answer"}}""" + "\n\n",
                    """data: {"type":"message_delta","usage":{"output_tokens":5}}""" + "\n\n",
                    """data: {"type":"message_stop"}""" + "\n\n",
                ),
            )
        }

        val events = AnthropicAdapter(mapper).stream(
            modelId = "claude-test",
            request = LlmRequest("hello"),
            decryptedApiKey = "anthropic-secret",
            baseUrlOverride = baseUrl(""),
        ).collectList().block()!!

        val complete = events.last() as LlmStreamEvent.ModelComplete
        assertEquals(1024, complete.cacheReadTokens)
        assertEquals(256, complete.cacheWriteTokens)
    }

    @Test
    fun `openai stream maps cached prompt tokens to cache read with no write dimension`() {
        startServer("/v1/chat/completions") { exchange ->
            capture(exchange)
            sendSse(
                exchange,
                listOf(
                    """data: {"choices":[{"delta":{"content":"hi"}}]}""" + "\n\n",
                    """data: {"choices":[],"usage":{"prompt_tokens":7,"completion_tokens":3,"prompt_tokens_details":{"cached_tokens":4}}}""" + "\n\n",
                    "data: [DONE]\n\n",
                ),
            )
        }

        val events = OpenAiCompatAdapter(mapper).stream(
            modelId = "provider-model",
            request = LlmRequest("describe"),
            decryptedApiKey = "test-secret",
            baseUrlOverride = baseUrl("/v1"),
        ).collectList().block()!!

        val complete = events.last() as LlmStreamEvent.ModelComplete
        assertEquals(4, complete.cacheReadTokens)
        assertEquals(null, complete.cacheWriteTokens)
    }

    @Test
    fun `three provider streams subscribe concurrently`() {
        val allArrived = CountDownLatch(3)
        val paths = Collections.synchronizedList(mutableListOf<String>())
        startServer("/v1/chat/completions") { exchange ->
            paths.add(exchange.requestURI.path)
            exchange.requestBody.use { it.readAllBytes() }
            allArrived.countDown()
            check(allArrived.await(2, TimeUnit.SECONDS)) { "Provider requests were not concurrent" }
            sendSse(
                exchange,
                listOf(
                    """data: {"choices":[{"delta":{"content":"ok"}}]}""" + "\n\n",
                    "data: [DONE]\n\n",
                ),
            )
        }
        val adapter = OpenAiCompatAdapter(mapper)

        val events = Flux.merge(
            (1..3).map { index ->
                adapter.stream(
                    modelId = "model-$index",
                    request = LlmRequest("hello"),
                    decryptedApiKey = "secret-$index",
                    baseUrlOverride = baseUrl("/v1"),
                )
            },
        ).collectList().block()!!

        assertEquals(3, paths.size)
        assertEquals(3, events.count { it is LlmStreamEvent.Token })
        assertEquals(3, events.count { it is LlmStreamEvent.ModelComplete })
    }

    @Test
    fun `provider error omits response body`() {
        startServer("/v1/chat/completions") { exchange ->
            exchange.requestBody.use { it.readAllBytes() }
            val body = "do-not-expose-provider-details".toByteArray()
            exchange.sendResponseHeaders(429, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }

        val event = OpenAiCompatAdapter(mapper).stream(
            modelId = "model",
            request = LlmRequest("hello"),
            decryptedApiKey = "secret",
            baseUrlOverride = baseUrl("/v1"),
        ).blockLast() as LlmStreamEvent.ModelError

        assertEquals("Provider returned HTTP 429", event.error)
    }

    @Test
    fun `minimax stream splits inline think tags from content`() {
        startServer("/text/chatcompletion_v2") { exchange ->
            exchange.requestBody.use { it.readAllBytes() }
            sendSse(
                exchange,
                listOf(
                    """data: {"choices":[{"delta":{"content":"<think>step 1</think>hello "}}]}""" + "\n\n",
                    """data: {"choices":[{"delta":{"content":"world"},"finish_reason":"stop"}],"usage":{"prompt_tokens":3,"completion_tokens":2}}""" + "\n\n",
                    "data: [DONE]\n\n",
                ),
            )
        }

        val events = MiniMaxAdapter().stream(
            modelId = "minimax-m",
            request = LlmRequest("hi"),
            decryptedApiKey = "secret",
            baseUrlOverride = baseUrl(""),
        ).collectList().block()!!

        assertEquals(
            listOf(
                LlmStreamEvent.Reasoning("minimax-m", "step 1"),
                LlmStreamEvent.Token("minimax-m", "hello "),
                LlmStreamEvent.Token("minimax-m", "world"),
            ),
            events.dropLast(1),
        )
        val complete = events.last() as LlmStreamEvent.ModelComplete
        assertEquals(3, complete.inputTokens)
        assertEquals(2, complete.outputTokens)
    }

    @Test
    fun `minimax stream handles think tag split across chunks`() {
        startServer("/text/chatcompletion_v2") { exchange ->
            exchange.requestBody.use { it.readAllBytes() }
            sendSse(
                exchange,
                listOf(
                    """data: {"choices":[{"delta":{"content":"<th"}}]}""" + "\n\n",
                    """data: {"choices":[{"delta":{"content":"ink>plan A</thi"}}]}""" + "\n\n",
                    """data: {"choices":[{"delta":{"content":"nk>answer"}}]}""" + "\n\n",
                    """data: {"choices":[{"delta":{},"finish_reason":"stop"}]}""" + "\n\n",
                    "data: [DONE]\n\n",
                ),
            )
        }

        val events = MiniMaxAdapter().stream(
            modelId = "minimax-m",
            request = LlmRequest("hi"),
            decryptedApiKey = "secret",
            baseUrlOverride = baseUrl(""),
        ).collectList().block()!!

        assertEquals(
            listOf(
                LlmStreamEvent.Reasoning("minimax-m", "plan A"),
                LlmStreamEvent.Token("minimax-m", "answer"),
            ),
            events.dropLast(1),
        )
    }

    @Test
    fun `minimax stream flushes an unclosed think block as reasoning on stop`() {
        startServer("/text/chatcompletion_v2") { exchange ->
            exchange.requestBody.use { it.readAllBytes() }
            sendSse(
                exchange,
                listOf(
                    """data: {"choices":[{"delta":{"content":"<think>never-finished"}}]}""" + "\n\n",
                    """data: {"choices":[{"delta":{"content":" continuation"},"finish_reason":"stop"}]}""" + "\n\n",
                    "data: [DONE]\n\n",
                ),
            )
        }

        val events = MiniMaxAdapter().stream(
            modelId = "minimax-m",
            request = LlmRequest("hi"),
            decryptedApiKey = "secret",
            baseUrlOverride = baseUrl(""),
        ).collectList().block()!!

        assertEquals(
            listOf(
                LlmStreamEvent.Reasoning("minimax-m", "never-finished"),
                LlmStreamEvent.Reasoning("minimax-m", " continuation"),
            ),
            events.dropLast(1),
        )
    }

    @Test
    fun `minimax stream still emits separate reasoning_content field`() {
        startServer("/text/chatcompletion_v2") { exchange ->
            exchange.requestBody.use { it.readAllBytes() }
            sendSse(
                exchange,
                listOf(
                    """data: {"choices":[{"delta":{"reasoning_content":"think step","content":"hi"}}]}""" + "\n\n",
                    """data: {"choices":[{"delta":{"content":"!"},"finish_reason":"stop"}]}""" + "\n\n",
                    "data: [DONE]\n\n",
                ),
            )
        }

        val events = MiniMaxAdapter().stream(
            modelId = "minimax-m",
            request = LlmRequest("hi"),
            decryptedApiKey = "secret",
            baseUrlOverride = baseUrl(""),
        ).collectList().block()!!

        assertEquals(
            listOf(
                LlmStreamEvent.Reasoning("minimax-m", "think step"),
                LlmStreamEvent.Token("minimax-m", "hi"),
                LlmStreamEvent.Token("minimax-m", "!"),
            ),
            events.dropLast(1),
        )
    }

    @Test
    fun `minimax stream emits plain tokens when no think tags are present`() {
        startServer("/text/chatcompletion_v2") { exchange ->
            exchange.requestBody.use { it.readAllBytes() }
            sendSse(
                exchange,
                listOf(
                    """data: {"choices":[{"delta":{"content":"hello "}}]}""" + "\n\n",
                    """data: {"choices":[{"delta":{"content":"world"},"finish_reason":"stop"}]}""" + "\n\n",
                    "data: [DONE]\n\n",
                ),
            )
        }

        val events = MiniMaxAdapter().stream(
            modelId = "minimax-m",
            request = LlmRequest("hi"),
            decryptedApiKey = "secret",
            baseUrlOverride = baseUrl(""),
        ).collectList().block()!!

        assertEquals(
            listOf(
                LlmStreamEvent.Token("minimax-m", "hello "),
                LlmStreamEvent.Token("minimax-m", "world"),
            ),
            events.dropLast(1),
        )
    }

    private fun startServer(path: String, handler: (HttpExchange) -> Unit) {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            serverExecutor = Executors.newCachedThreadPool()
            executor = serverExecutor
            createContext(path) { exchange ->
                try {
                    handler(exchange)
                } catch (error: Throwable) {
                    exchange.close()
                    throw error
                }
            }
            start()
        }
    }

    private fun baseUrl(path: String): String =
        "http://127.0.0.1:${server!!.address.port}$path"

    private fun capture(exchange: HttpExchange): CapturedRequest =
        CapturedRequest(
            headers = exchange.requestHeaders.entries.associate { (name, values) -> name to values.first() },
            body = exchange.requestBody.use { String(it.readAllBytes(), StandardCharsets.UTF_8) },
        )

    private fun sendSse(
        exchange: HttpExchange,
        chunks: List<String>,
        splitFirstChunk: Boolean = false,
    ) {
        exchange.responseHeaders.add("Content-Type", "text/event-stream")
        exchange.sendResponseHeaders(200, 0)
        exchange.responseBody.use { output ->
            chunks.forEachIndexed { index, chunk ->
                val bytes = chunk.toByteArray(StandardCharsets.UTF_8)
                if (index == 0 && splitFirstChunk) {
                    val utf8Start = bytes.indexOfFirst { it < 0 }
                    val splitAt = utf8Start + 1
                    output.write(bytes, 0, splitAt)
                    output.flush()
                    output.write(bytes, splitAt, bytes.size - splitAt)
                } else {
                    output.write(bytes)
                }
                output.flush()
            }
        }
    }

    private data class CapturedRequest(
        val headers: Map<String, String>,
        val body: String,
    )
}
