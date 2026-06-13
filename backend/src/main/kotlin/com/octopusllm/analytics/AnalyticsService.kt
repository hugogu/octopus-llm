package com.octopusllm.analytics

import com.octopusllm.api.v2.PageResponse
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class AnalyticsService(private val repository: AnalyticsRepository) {
    fun summary(userId: UUID, from: Instant?, to: Instant?, configuredModelId: UUID?) =
        normalize(repository.summary(personalFilter(userId, from, to, configuredModelId)))

    fun byModel(userId: UUID, from: Instant?, to: Instant?, configuredModelId: UUID?, page: Int, size: Int) =
        page(repository.byModel(personalFilter(userId, from, to, configuredModelId), page, size), page, size)

    fun bySession(userId: UUID, from: Instant?, to: Instant?, configuredModelId: UUID?, page: Int, size: Int) =
        page(repository.bySession(personalFilter(userId, from, to, configuredModelId), page, size), page, size)

    fun responses(userId: UUID, from: Instant?, to: Instant?, configuredModelId: UUID?, page: Int, size: Int) =
        page(repository.responses(personalFilter(userId, from, to, configuredModelId), page, size), page, size)

    fun publicByModel(
        from: Instant?,
        to: Instant?,
        protocol: String?,
        modelId: String?,
        page: Int,
        size: Int,
    ) = page(
        repository.publicByModel(
            AnalyticsFilter(from = from, to = to, protocol = protocol, modelId = modelId),
            page,
            size,
        ),
        page,
        size,
    )

    private fun personalFilter(userId: UUID, from: Instant?, to: Instant?, configuredModelId: UUID?) =
        AnalyticsFilter(userId, from, to, configuredModelId)

    private fun page(result: Pair<List<Map<String, Any?>>, Long>, page: Int, size: Int): PageResponse<Map<String, Any?>> {
        requirePage(page, size)
        return PageResponse(
            items = result.first.map(::normalize),
            page = page,
            size = size,
            totalElements = result.second,
            totalPages = if (result.second == 0L) 0 else ((result.second + size - 1) / size).toInt(),
        )
    }

    private fun requirePage(page: Int, size: Int) {
        com.octopusllm.api.v2.boundedPageRequest(page, size)
    }

    private fun normalize(row: Map<String, Any?>): Map<String, Any?> =
        row.entries.associate { (key, value) ->
            snakeToCamel(key) to when (value) {
                is java.sql.Array -> (value.array as Array<*>).toList()
                is Number -> value
                else -> value
            }
        }

    private fun snakeToCamel(value: String): String =
        value.split("_").let { parts ->
            parts.first() + parts.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercase) }
        }
}
