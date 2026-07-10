package com.octopusllm.chat

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class TimeContextTest {
    // 2026-07-10T02:30:00Z == 2026-07-10 10:30 in Asia/Shanghai (UTC+8), a Friday.
    private val fixedClock = Clock.fixed(Instant.parse("2026-07-10T02:30:00Z"), ZoneId.of("UTC"))

    @Test
    fun `renders the current date, weekday and time in the configured zone`() {
        val prompt = TimeContext("Asia/Shanghai").systemPrompt(fixedClock)

        assertTrue(prompt.contains("2026-07-10"), "expected the current date: $prompt")
        assertTrue(prompt.contains("10:30"), "expected zone-local time (UTC+8): $prompt")
        assertTrue(prompt.contains("星期五"), "expected the localized weekday: $prompt")
        assertTrue(prompt.contains("Asia/Shanghai"), "expected the timezone id: $prompt")
    }

    @Test
    fun `respects a different configured zone`() {
        val prompt = TimeContext("UTC").systemPrompt(fixedClock)

        assertTrue(prompt.contains("02:30"), "expected UTC-local time: $prompt")
        assertTrue(prompt.contains("UTC"), "expected the UTC timezone id: $prompt")
    }
}
