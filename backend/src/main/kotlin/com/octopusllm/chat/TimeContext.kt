package com.octopusllm.chat

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds the always-on time context (feature 009, US1). The current date, weekday, time, and timezone
 * are injected into every model request as a system prompt so relative references like "今天"/"现在"/
 * "下周" resolve against a shared temporal baseline instead of a model's training cutoff.
 *
 * The zone defaults to Asia/Shanghai (the product's primary audience) and is overridable via
 * `app.time.zone`, decoupling the injected "today" from the server's (often UTC) system clock.
 */
@Component
class TimeContext(
    @Value("\${app.time.zone:Asia/Shanghai}") zoneIdValue: String,
) {
    private val zone: ZoneId = ZoneId.of(zoneIdValue)

    /** Renders the system-prompt line for [clock]; a fixed clock can be supplied for deterministic tests. */
    fun systemPrompt(clock: Clock = Clock.system(zone)): String {
        val now = clock.instant().atZone(zone)
        val formatted = FORMATTER.format(now)
        return "当前日期与时间：$formatted（时区 ${zone.id}）。" +
            "当用户提到“今天”“现在”“昨天”“明天”“下周”等相对时间时，请以此为基准进行推断。"
    }

    private companion object {
        private val FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE HH:mm", Locale.SIMPLIFIED_CHINESE)
    }
}
