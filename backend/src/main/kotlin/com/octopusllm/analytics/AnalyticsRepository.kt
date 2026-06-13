package com.octopusllm.analytics

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import java.util.UUID

data class AnalyticsFilter(
    val userId: UUID? = null,
    val from: Instant? = null,
    val to: Instant? = null,
    val configuredModelId: UUID? = null,
    val protocol: String? = null,
    val modelId: String? = null,
)

@Repository
class AnalyticsRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    fun summary(filter: AnalyticsFilter): Map<String, Any?> {
        val (where, params) = where(filter, ownerScoped = true)
        val row = jdbc.queryForMap(
            """
            SELECT COUNT(*) AS total_responses,
                   COALESCE(AVG(CASE WHEN pr.status = 'complete' THEN 1.0 ELSE 0.0 END), 0) AS success_rate,
                   COALESCE(AVG(pr.latency_ms), 0) AS avg_latency_ms,
                   COALESCE(SUM(pr.input_tokens), 0) AS input_tokens,
                   COALESCE(SUM(pr.output_tokens), 0) AS output_tokens
            FROM provider_responses pr
            JOIN chat_turns ct ON ct.id = pr.turn_id
            JOIN chat_sessions cs ON cs.id = ct.session_id
            $where
            """.trimIndent(),
            params,
        ).toMutableMap()
        row["estimatedCostsByCurrency"] = costs(where, params)
        return row
    }

    fun byModel(filter: AnalyticsFilter, page: Int, size: Int): Pair<List<Map<String, Any?>>, Long> {
        val (where, params) = where(filter, ownerScoped = true)
        params.addValue("limit", size).addValue("offset", page * size)
        val items = jdbc.queryForList(
            """
            WITH base AS (
              SELECT pr.*
              FROM provider_responses pr
              JOIN chat_turns ct ON ct.id = pr.turn_id
              JOIN chat_sessions cs ON cs.id = ct.session_id
              $where
            )
            SELECT configured_model_id, model_id, model_display_name,
                   COUNT(*) AS response_count,
                   AVG(CASE WHEN status = 'complete' THEN 1.0 ELSE 0.0 END) AS success_rate,
                   AVG(latency_ms) AS avg_latency_ms,
                   percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms) AS p95_latency_ms,
                   COALESCE(SUM(input_tokens), 0) AS input_tokens,
                   COALESCE(SUM(output_tokens), 0) AS output_tokens
            FROM base
            GROUP BY configured_model_id, model_id, model_display_name
            ORDER BY response_count DESC, configured_model_id
            LIMIT :limit OFFSET :offset
            """.trimIndent(),
            params,
        ).map { row ->
            row.toMutableMap().apply {
                this["estimatedCostsByCurrency"] = costsForModel(where, params, row["configured_model_id"] as UUID)
            }
        }
        val total = jdbc.queryForObject(
            """
            SELECT COUNT(DISTINCT pr.configured_model_id)
            FROM provider_responses pr
            JOIN chat_turns ct ON ct.id = pr.turn_id
            JOIN chat_sessions cs ON cs.id = ct.session_id
            $where
            """.trimIndent(),
            params,
            Long::class.java,
        ) ?: 0
        return items to total
    }

    fun bySession(filter: AnalyticsFilter, page: Int, size: Int): Pair<List<Map<String, Any?>>, Long> {
        val (where, params) = where(filter, ownerScoped = true)
        params.addValue("limit", size).addValue("offset", page * size)
        val items = jdbc.queryForList(
            """
            SELECT cs.id AS session_id, cs.title,
                   COUNT(*) AS response_count,
                   ARRAY_AGG(DISTINCT pr.model_display_name ORDER BY pr.model_display_name) AS models,
                   AVG(pr.latency_ms) AS avg_latency_ms,
                   COALESCE(SUM(pr.input_tokens), 0) AS input_tokens,
                   COALESCE(SUM(pr.output_tokens), 0) AS output_tokens,
                   AVG(CASE WHEN pr.status = 'complete' THEN 1.0 ELSE 0.0 END) AS success_rate
            FROM provider_responses pr
            JOIN chat_turns ct ON ct.id = pr.turn_id
            JOIN chat_sessions cs ON cs.id = ct.session_id
            $where
            GROUP BY cs.id, cs.title
            ORDER BY MAX(pr.created_at) DESC, cs.id
            LIMIT :limit OFFSET :offset
            """.trimIndent(),
            params,
        ).map { row ->
            row.toMutableMap().apply {
                this["estimatedCostsByCurrency"] = costsForSession(where, params, row["session_id"] as UUID)
            }
        }
        val total = jdbc.queryForObject(
            """
            SELECT COUNT(DISTINCT cs.id)
            FROM provider_responses pr
            JOIN chat_turns ct ON ct.id = pr.turn_id
            JOIN chat_sessions cs ON cs.id = ct.session_id
            $where
            """.trimIndent(),
            params,
            Long::class.java,
        ) ?: 0
        return items to total
    }

    fun responses(filter: AnalyticsFilter, page: Int, size: Int): Pair<List<Map<String, Any?>>, Long> {
        val (where, params) = where(filter, ownerScoped = true)
        params.addValue("limit", size).addValue("offset", page * size)
        val items = jdbc.query(
            """
            SELECT pr.*, cs.user_id, cs.id AS session_id, host(ct.client_ip) AS client_ip,
                   (SELECT COUNT(*) FROM response_likes rl WHERE rl.response_id = pr.id) AS named_like_count,
                   (SELECT COUNT(*) FROM anonymous_response_likes arl WHERE arl.response_id = pr.id) AS anonymous_like_count
            FROM provider_responses pr
            JOIN chat_turns ct ON ct.id = pr.turn_id
            JOIN chat_sessions cs ON cs.id = ct.session_id
            $where
            ORDER BY pr.created_at DESC, pr.id
            LIMIT :limit OFFSET :offset
            """.trimIndent(),
            params,
        ) { result, _ -> responseRow(result) }
        val total = jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM provider_responses pr
            JOIN chat_turns ct ON ct.id = pr.turn_id
            JOIN chat_sessions cs ON cs.id = ct.session_id
            $where
            """.trimIndent(),
            params,
            Long::class.java,
        ) ?: 0
        return items to total
    }

    /** Owner-scoped daily time series for trend line charts (latency, success rate, token usage). */
    fun timeseries(filter: AnalyticsFilter): List<Map<String, Any?>> {
        val (where, params) = where(filter, ownerScoped = true)
        return jdbc.queryForList(
            """
            SELECT to_char(date_trunc('day', pr.created_at), 'YYYY-MM-DD') AS bucket,
                   COUNT(*) AS response_count,
                   AVG(pr.latency_ms) AS avg_latency_ms,
                   AVG(CASE WHEN pr.status = 'complete' THEN 1.0 ELSE 0.0 END) AS success_rate,
                   COALESCE(SUM(pr.input_tokens), 0) AS input_tokens,
                   COALESCE(SUM(pr.output_tokens), 0) AS output_tokens
            FROM provider_responses pr
            JOIN chat_turns ct ON ct.id = pr.turn_id
            JOIN chat_sessions cs ON cs.id = ct.session_id
            $where
            GROUP BY date_trunc('day', pr.created_at)
            ORDER BY date_trunc('day', pr.created_at)
            """.trimIndent(),
            params,
        )
    }

    fun publicByModel(filter: AnalyticsFilter, page: Int, size: Int): Pair<List<Map<String, Any?>>, Long> {
        val (where, params) = where(filter, ownerScoped = false)
        params.addValue("limit", size).addValue("offset", page * size)
        val items = jdbc.queryForList(
            """
            SELECT pr.protocol, pr.model_id,
                   COUNT(*) AS response_count,
                   AVG(CASE WHEN pr.status = 'complete' THEN 1.0 ELSE 0.0 END) AS success_rate,
                   AVG(pr.latency_ms) AS avg_latency_ms,
                   percentile_cont(0.95) WITHIN GROUP (ORDER BY pr.latency_ms) AS p95_latency_ms,
                   COALESCE(SUM(pr.input_tokens), 0) AS input_tokens,
                   COALESCE(SUM(pr.output_tokens), 0) AS output_tokens,
                   SUM((SELECT COUNT(*) FROM response_likes rl WHERE rl.response_id = pr.id)) AS named_like_count,
                   SUM((SELECT COUNT(*) FROM anonymous_response_likes arl WHERE arl.response_id = pr.id)) AS anonymous_like_count
            FROM provider_responses pr
            $where
            GROUP BY pr.protocol, pr.model_id
            ORDER BY response_count DESC, pr.protocol, pr.model_id
            LIMIT :limit OFFSET :offset
            """.trimIndent(),
            params,
        )
        val total = jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM (
              SELECT 1 FROM provider_responses pr
              $where
              GROUP BY pr.protocol, pr.model_id
            ) groups
            """.trimIndent(),
            params,
            Long::class.java,
        ) ?: 0
        return items to total
    }

    private fun costs(where: String, params: MapSqlParameterSource): Map<String, BigDecimal> =
        costRows(
            """
            SELECT pr.price_currency,
                   SUM((pr.input_tokens * pr.input_price_per_mtok
                     + pr.output_tokens * pr.output_price_per_mtok) / 1000000) AS amount
            FROM provider_responses pr
            JOIN chat_turns ct ON ct.id = pr.turn_id
            JOIN chat_sessions cs ON cs.id = ct.session_id
            $where
              AND pr.price_currency IS NOT NULL
              AND pr.input_tokens IS NOT NULL AND pr.output_tokens IS NOT NULL
              AND pr.input_price_per_mtok IS NOT NULL AND pr.output_price_per_mtok IS NOT NULL
            GROUP BY pr.price_currency
            """.trimIndent(),
            params,
        )

    private fun costsForModel(
        where: String,
        params: MapSqlParameterSource,
        configuredModelId: UUID,
    ): Map<String, BigDecimal> {
        val scoped = copy(params).addValue("costModelId", configuredModelId)
        return costRows(
            """
            SELECT pr.price_currency,
                   SUM((pr.input_tokens * pr.input_price_per_mtok
                     + pr.output_tokens * pr.output_price_per_mtok) / 1000000) AS amount
            FROM provider_responses pr
            JOIN chat_turns ct ON ct.id = pr.turn_id
            JOIN chat_sessions cs ON cs.id = ct.session_id
            $where AND pr.configured_model_id = :costModelId
              AND pr.price_currency IS NOT NULL
              AND pr.input_tokens IS NOT NULL AND pr.output_tokens IS NOT NULL
              AND pr.input_price_per_mtok IS NOT NULL AND pr.output_price_per_mtok IS NOT NULL
            GROUP BY pr.price_currency
            """.trimIndent(),
            scoped,
        )
    }

    private fun costsForSession(
        where: String,
        params: MapSqlParameterSource,
        sessionId: UUID,
    ): Map<String, BigDecimal> {
        val scoped = copy(params).addValue("costSessionId", sessionId)
        return costRows(
            """
            SELECT pr.price_currency,
                   SUM((pr.input_tokens * pr.input_price_per_mtok
                     + pr.output_tokens * pr.output_price_per_mtok) / 1000000) AS amount
            FROM provider_responses pr
            JOIN chat_turns ct ON ct.id = pr.turn_id
            JOIN chat_sessions cs ON cs.id = ct.session_id
            $where AND cs.id = :costSessionId
              AND pr.price_currency IS NOT NULL
              AND pr.input_tokens IS NOT NULL AND pr.output_tokens IS NOT NULL
              AND pr.input_price_per_mtok IS NOT NULL AND pr.output_price_per_mtok IS NOT NULL
            GROUP BY pr.price_currency
            """.trimIndent(),
            scoped,
        )
    }

    private fun costRows(sql: String, params: MapSqlParameterSource): Map<String, BigDecimal> =
        jdbc.query(sql, params) { result, _ ->
            result.getString("price_currency") to result.getBigDecimal("amount")
        }.toMap()

    private fun responseRow(result: ResultSet): Map<String, Any?> {
        val cost = ModelPricing.estimate(
            result.getObject("input_tokens") as? Int,
            result.getObject("output_tokens") as? Int,
            result.getBigDecimal("input_price_per_mtok"),
            result.getBigDecimal("output_price_per_mtok"),
            result.getString("price_currency"),
        )
        return linkedMapOf(
            "responseId" to result.getObject("id", UUID::class.java),
            "userId" to result.getObject("user_id", UUID::class.java),
            "sessionId" to result.getObject("session_id", UUID::class.java),
            "createdAt" to result.getTimestamp("created_at").toInstant(),
            "configuredModelId" to result.getObject("configured_model_id", UUID::class.java),
            "modelId" to result.getString("model_id"),
            "modelDisplayName" to result.getString("model_display_name"),
            "protocol" to result.getString("protocol"),
            "connectionId" to result.getObject("connection_id", UUID::class.java),
            "connectionLabel" to result.getString("connection_label"),
            "status" to result.getString("status"),
            "latencyMs" to result.getInt("latency_ms"),
            "inputTokens" to result.getObject("input_tokens"),
            "outputTokens" to result.getObject("output_tokens"),
            "estimatedCost" to cost,
            "clientIp" to result.getString("client_ip"),
            "namedLikeCount" to result.getLong("named_like_count"),
            "anonymousLikeCount" to result.getLong("anonymous_like_count"),
        )
    }

    private fun where(filter: AnalyticsFilter, ownerScoped: Boolean): Pair<String, MapSqlParameterSource> {
        val conditions = mutableListOf<String>()
        val params = MapSqlParameterSource()
        if (ownerScoped) {
            conditions += "cs.user_id = :userId"
            params.addValue("userId", requireNotNull(filter.userId))
        }
        filter.from?.let {
            conditions += "pr.created_at >= :fromTs"
            params.addValue("fromTs", it, Types.TIMESTAMP_WITH_TIMEZONE)
        }
        filter.to?.let {
            conditions += "pr.created_at < :toTs"
            params.addValue("toTs", it, Types.TIMESTAMP_WITH_TIMEZONE)
        }
        filter.configuredModelId?.let {
            conditions += "pr.configured_model_id = :configuredModelId"
            params.addValue("configuredModelId", it)
        }
        filter.protocol?.takeIf(String::isNotBlank)?.let {
            conditions += "pr.protocol = :protocol"
            params.addValue("protocol", it)
        }
        filter.modelId?.takeIf(String::isNotBlank)?.let {
            conditions += "pr.model_id = :modelId"
            params.addValue("modelId", it)
        }
        return (if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}") to params
    }

    private fun copy(source: MapSqlParameterSource) =
        MapSqlParameterSource(source.values)

}
