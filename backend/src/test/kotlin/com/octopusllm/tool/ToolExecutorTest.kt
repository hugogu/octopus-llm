package com.octopusllm.tool

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class ToolExecutorTest {

    /** Tool whose behavior and invocation count are controlled per test. */
    private class FakeTool(
        name: String = "fake",
        val calls: AtomicInteger = AtomicInteger(0),
        val behavior: (Map<String, Any?>) -> ToolResult,
    ) : Tool {
        override val definition = ToolDefinition(name, "fake", emptyMap())
        override fun execute(arguments: Map<String, Any?>): ToolResult {
            calls.incrementAndGet()
            return behavior(arguments)
        }
    }

    private val fastExecutor = ToolExecutor(
        timeout = Duration.ofMillis(200),
        retryBackoff = Duration.ofMillis(10),
        maxRetries = 1,
    )

    @Test
    fun `execute emits running, result and terminal success status`() {
        val tool = FakeTool { ToolResult.Success(mapOf("ok" to true)) }
        val scope = fastExecutor.newTurnScope()

        val events = fastExecutor.execute(scope, "call-1", tool, mapOf("a" to 1)).collectList().block()!!

        assertEquals(3, events.size)
        assertEquals(
            UnifiedInteractionEvent.ToolStatus("call-1", "fake", ToolInvocationStatus.RUNNING),
            events[0],
        )
        val result = events[1] as UnifiedInteractionEvent.ToolResultEvent
        assertEquals(ToolResult.Success(mapOf("ok" to true)), result.result)
        assertEquals(
            UnifiedInteractionEvent.ToolStatus("call-1", "fake", ToolInvocationStatus.SUCCESS),
            events[2],
        )
    }

    @Test
    fun `identical invocations in one scope execute the tool once`() {
        val calls = AtomicInteger(0)
        val tool = FakeTool(calls = calls) { ToolResult.Success(mapOf("v" to "x")) }
        val scope = fastExecutor.newTurnScope()

        val first = fastExecutor.executeOnce(scope, tool, mapOf("symbol" to "600519")).block()
        val second = fastExecutor.executeOnce(scope, tool, mapOf("symbol" to "600519")).block()

        assertEquals(first, second)
        assertEquals(1, calls.get())
    }

    @Test
    fun `different arguments execute the tool separately`() {
        val calls = AtomicInteger(0)
        val tool = FakeTool(calls = calls) { args -> ToolResult.Success(mapOf("echo" to args["symbol"])) }
        val scope = fastExecutor.newTurnScope()

        fastExecutor.executeOnce(scope, tool, mapOf("symbol" to "600519")).block()
        fastExecutor.executeOnce(scope, tool, mapOf("symbol" to "000001")).block()

        assertEquals(2, calls.get())
    }

    @Test
    fun `a thrown transient error is retried once then succeeds`() {
        val calls = AtomicInteger(0)
        val tool = FakeTool(calls = calls) {
            if (calls.get() == 1) throw RuntimeException("transient 503") else ToolResult.Success(mapOf("ok" to true))
        }
        val scope = fastExecutor.newTurnScope()

        val result = fastExecutor.executeOnce(scope, tool, emptyMap()).block()

        assertTrue(result is ToolResult.Success)
        assertEquals(2, calls.get())
    }

    @Test
    fun `an exhausted retry is folded into a failure result`() {
        val tool = FakeTool { throw RuntimeException("still down") }
        val scope = fastExecutor.newTurnScope()

        val result = fastExecutor.executeOnce(scope, tool, emptyMap()).block()

        val failure = result as ToolResult.Failure
        assertFalse(failure.timedOut)
        assertTrue(failure.errorMessage.contains("still down"))
        assertEquals(ToolInvocationStatus.FAILED, failure.status)
    }

    @Test
    fun `a slow tool times out and reports a timeout failure`() {
        val tool = FakeTool {
            Thread.sleep(1_000)
            ToolResult.Success(emptyMap())
        }
        val scope = fastExecutor.newTurnScope()

        val result = fastExecutor.executeOnce(scope, tool, emptyMap()).block()

        val failure = result as ToolResult.Failure
        assertTrue(failure.timedOut)
        assertEquals(ToolInvocationStatus.TIMEOUT, failure.status)
    }
}
