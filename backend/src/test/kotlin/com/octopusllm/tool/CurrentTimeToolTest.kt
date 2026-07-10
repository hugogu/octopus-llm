package com.octopusllm.tool

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class CurrentTimeToolTest {
    // 2026-07-10T02:30:00Z == 2026-07-10 10:30 in Asia/Shanghai (UTC+8).
    private val clock = Clock.fixed(Instant.parse("2026-07-10T02:30:00Z"), ZoneId.of("UTC"))

    @Test
    fun `returns the current time in the default zone`() {
        val result = CurrentTimeTool("Asia/Shanghai", clock).execute(emptyMap()) as ToolResult.Success

        assertEquals("2026-07-10", result.data["date"])
        assertEquals("10:30:00", result.data["time"])
        assertEquals("Asia/Shanghai", result.data["timezone"])
    }

    @Test
    fun `honors a requested timezone`() {
        val result = CurrentTimeTool("Asia/Shanghai", clock).execute(mapOf("timezone" to "UTC")) as ToolResult.Success

        assertEquals("02:30:00", result.data["time"])
        assertEquals("UTC", result.data["timezone"])
    }

    @Test
    fun `fails clearly on an unknown timezone`() {
        val result = CurrentTimeTool("Asia/Shanghai", clock).execute(mapOf("timezone" to "Mars/Olympus"))

        val failure = result as ToolResult.Failure
        assertTrue(failure.errorMessage.contains("Mars/Olympus"))
    }
}
