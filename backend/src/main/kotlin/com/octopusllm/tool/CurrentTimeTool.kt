package com.octopusllm.tool

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Built-in `current_time` tool (feature 009): an authoritative, self-contained time lookup requiring no
 * external API. Complements the always-on time context — a model can call it explicitly, optionally for
 * a specific IANA timezone. Defaults to the product zone (`app.time.zone`, Asia/Shanghai).
 */
@Component
class CurrentTimeTool(
    @Value("\${app.time.zone:Asia/Shanghai}") private val defaultZone: String,
    private val clock: Clock = Clock.systemUTC(),
) : Tool {
    override val definition = ToolDefinition(
        name = "current_time",
        description = "Return the current date and time. Optionally pass an IANA timezone id.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "timezone" to mapOf(
                    "type" to "string",
                    "description" to "IANA timezone id, e.g. Asia/Shanghai or UTC. Defaults to the server zone.",
                ),
            ),
            "required" to emptyList<String>(),
        ),
    )

    override fun execute(arguments: Map<String, Any?>): ToolResult {
        val requested = (arguments["timezone"] as? String)?.takeIf { it.isNotBlank() }
        val zone = runCatching { ZoneId.of(requested ?: defaultZone) }.getOrElse {
            return ToolResult.Failure("Unknown timezone: $requested")
        }
        val now = clock.instant().atZone(zone)
        return ToolResult.Success(
            mapOf(
                "iso8601" to now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                "date" to now.format(DateTimeFormatter.ISO_LOCAL_DATE),
                "time" to now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                "weekday" to now.format(DateTimeFormatter.ofPattern("EEEE", Locale.SIMPLIFIED_CHINESE)),
                "timezone" to zone.id,
            ),
        )
    }
}
