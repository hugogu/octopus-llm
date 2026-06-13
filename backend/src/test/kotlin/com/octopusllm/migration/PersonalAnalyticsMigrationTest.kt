package com.octopusllm.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager

@Testcontainers
class PersonalAnalyticsMigrationTest {
    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @BeforeAll
        fun migrate() {
            Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .cleanDisabled(false)
                .load()
                .also { it.clean() }
                .migrate()
        }
    }

    @Test
    fun `V021 through V026 create required columns tables and partial active-share uniqueness`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            val columns = connection.createStatement().use { statement ->
                statement.executeQuery(
                    """
                    SELECT table_name, column_name
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name IN ('users','configured_models','chat_turns','provider_responses')
                    """.trimIndent(),
                ).use { result ->
                    buildSet {
                        while (result.next()) add(result.getString(1) to result.getString(2))
                    }
                }
            }
            assertTrue("users" to "display_name" in columns)
            assertTrue("configured_models" to "input_price_per_mtok" in columns)
            assertTrue("chat_turns" to "client_ip" in columns)
            assertTrue("provider_responses" to "connection_id" in columns)

            listOf(
                "auth_action_throttles",
                "response_likes",
                "anonymous_response_likes",
                "session_shares",
            ).forEach { table ->
                assertTrue(
                    connection.metaData.getTables(null, "public", table, arrayOf("TABLE")).use { it.next() },
                    "missing table $table",
                )
            }

            connection.createStatement().use { statement ->
                statement.executeQuery(
                    """
                    SELECT indexdef FROM pg_indexes
                    WHERE schemaname = 'public' AND indexname = 'uq_session_shares_active'
                    """.trimIndent(),
                ).use { result ->
                    assertTrue(result.next())
                    assertTrue(result.getString(1).contains("WHERE (revoked_at IS NULL)"))
                }
            }
        }
    }

    @Test
    fun `pricing and currency constraints reject invalid values`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                val userId = java.util.UUID.randomUUID()
                val connectionId = java.util.UUID.randomUUID()
                statement.executeUpdate(
                    "INSERT INTO users(id,email,password_hash) VALUES ('$userId','migration-005@example.com','hash')",
                )
                statement.executeUpdate(
                    """
                    INSERT INTO connections(id,user_id,protocol,base_url,encrypted_key,key_iv)
                    VALUES ('$connectionId','$userId','openai-compatible','https://example.com',decode('00','hex'),decode('00','hex'))
                    """.trimIndent(),
                )
                val failed = runCatching {
                    statement.executeUpdate(
                        """
                        INSERT INTO configured_models(
                          user_id,connection_id,model_id,display_name,input_price_per_mtok,price_currency
                        ) VALUES ('$userId','$connectionId','model','Model',-1,'usd')
                        """.trimIndent(),
                    )
                }.isFailure
                assertTrue(failed)
            }
        }
    }
}
