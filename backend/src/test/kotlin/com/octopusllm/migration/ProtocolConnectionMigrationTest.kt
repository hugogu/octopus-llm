package com.octopusllm.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

@Testcontainers
class ProtocolConnectionMigrationTest {
    companion object {
        @Container
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }

    private lateinit var flyway: Flyway

    @BeforeEach
    fun resetToV016() {
        flyway = Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .cleanDisabled(false)
            .target("16")
            .load()
        flyway.clean()
        flyway.migrate()
    }

    @Test
    fun `V017 migrates keys models history and preferences before removing source tables`() {
        val userId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val turnId = UUID.randomUUID()
        val keyIds = providerMappings.keys.associateWith { UUID.randomUUID() }
        val configuredModelId = UUID.randomUUID()
        val skippedModelId = UUID.randomUUID()
        val ciphertext = byteArrayOf(1, 2, 3, 4)
        val iv = byteArrayOf(5, 6, 7)

        connection().use { database ->
            database.prepareStatement(
                "INSERT INTO users(id,email,password_hash,email_verified) VALUES (?,?,?,true)",
            ).use {
                it.setObject(1, userId)
                it.setString(2, "migration@example.com")
                it.setString(3, "hash")
                it.executeUpdate()
            }
            providerMappings.forEach { (provider, expected) ->
                database.prepareStatement(
                    """
                    INSERT INTO provider_api_keys(
                        id,user_id,provider_id,encrypted_key,key_iv,label,base_url
                    ) VALUES (?,?,?,?,?,?,?)
                    """.trimIndent(),
                ).use {
                    it.setObject(1, keyIds.getValue(provider))
                    it.setObject(2, userId)
                    it.setString(3, provider)
                    it.setBytes(4, ciphertext)
                    it.setBytes(5, iv)
                    it.setString(6, provider)
                    it.setString(7, if (provider == "openai") "https://example.com/v1/" else null)
                    it.executeUpdate()
                }
                assertTrue(expected.first.isNotBlank())
            }
            database.createStatement().use {
                it.executeUpdate(
                    """
                    INSERT INTO model_definitions(
                        id,provider_id,display_name,capability_matrix,is_active,source
                    ) VALUES (
                        'migration-model','openai','Migration Model',
                        '{"input_modalities":["text"],"output_modalities":["text"],"supports_streaming":true,"supports_function_calling":false,"supports_system_prompt":true,"supports_video_input":false,"extras":{"custom_key":true}}',
                        true,'CUSTOM'
                    )
                    """.trimIndent(),
                )
                it.executeUpdate(
                    """
                    INSERT INTO user_model_configs(
                        id,user_id,model_id,provider_api_key_id,is_enabled,custom_params
                    ) VALUES (
                        '$configuredModelId','$userId','migration-model','${keyIds.getValue("openai")}',false,
                        '{"temperature":0.25}'
                    )
                    """.trimIndent(),
                )
                it.executeUpdate(
                    """
                    INSERT INTO user_model_configs(
                        id,user_id,model_id,provider_api_key_id,is_enabled,custom_params
                    ) VALUES (
                        '$skippedModelId','$userId','gpt-4o-mini-2024-07-18',NULL,true,'{}'
                    )
                    """.trimIndent(),
                )
                it.executeUpdate("INSERT INTO chat_sessions(id,user_id,title) VALUES ('$sessionId','$userId','Migrated')")
                it.executeUpdate(
                    """
                    INSERT INTO chat_turns(id,session_id,sequence_num,prompt_text,selected_model_ids)
                    VALUES ('$turnId','$sessionId',1,'hello',ARRAY['migration-model'])
                    """.trimIndent(),
                )
                it.executeUpdate(
                    """
                    INSERT INTO provider_responses(
                        turn_id,model_id,status,response_text,latency_ms
                    ) VALUES ('$turnId','migration-model','complete','world',42)
                    """.trimIndent(),
                )
                it.executeUpdate(
                    """
                    INSERT INTO user_preferences(user_id,last_selected_model_id)
                    VALUES ('$userId','migration-model')
                    """.trimIndent(),
                )
            }
        }

        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .target("18")
            .load()
            .migrate()

        connection().use { database ->
            providerMappings.forEach { (provider, expected) ->
                database.prepareStatement(
                    "SELECT protocol,base_url,encrypted_key,key_iv FROM connections WHERE id=?",
                ).use {
                    it.setObject(1, keyIds.getValue(provider))
                    it.executeQuery().use { result ->
                        assertTrue(result.next())
                        assertEquals(expected.first, result.getString("protocol"))
                        assertEquals(
                            if (provider == "openai") "https://example.com/v1" else expected.second,
                            result.getString("base_url"),
                        )
                        assertArrayEquals(ciphertext, result.getBytes("encrypted_key"))
                        assertArrayEquals(iv, result.getBytes("key_iv"))
                    }
                }
            }
            database.prepareStatement(
                "SELECT model_id,is_enabled,custom_params::text,capability_overrides::text FROM configured_models WHERE id=?",
            ).use {
                it.setObject(1, configuredModelId)
                it.executeQuery().use { result ->
                    assertTrue(result.next())
                    assertEquals("migration-model", result.getString("model_id"))
                    assertFalse(result.getBoolean("is_enabled"))
                    assertTrue(result.getString("custom_params").contains("0.25"))
                    val overrides = result.getString("capability_overrides")
                    assertFalse(overrides.contains("extras"), "Migrated capability overrides must not contain legacy 'extras' key")
                    assertTrue(overrides.contains("input_modalities"))
                }
            }
            database.createStatement().use { statement ->
                statement.executeQuery(
                    """
                    SELECT configured_model_id,model_display_name,protocol,response_text
                    FROM provider_responses
                    """.trimIndent(),
                ).use { result ->
                    assertTrue(result.next())
                    assertEquals(configuredModelId, result.getObject("configured_model_id", UUID::class.java))
                    assertEquals("Migration Model", result.getString("model_display_name"))
                    assertEquals("openai-compatible", result.getString("protocol"))
                    assertEquals("world", result.getString("response_text"))
                }
                statement.executeQuery(
                    "SELECT selected_configured_model_ids FROM chat_turns WHERE id='$turnId'",
                ).use { result ->
                    assertTrue(result.next())
                    val ids = result.getArray(1).array as Array<*>
                    assertEquals(listOf(configuredModelId), ids.toList())
                }
                statement.executeQuery(
                    "SELECT last_selected_configured_model_id FROM user_preferences WHERE user_id='$userId'",
                ).use { result ->
                    assertTrue(result.next())
                    assertEquals(configuredModelId, result.getObject(1, UUID::class.java))
                }
                statement.executeQuery(
                    """
                    SELECT migrated_connections,migrated_models,skipped_models_without_key,unmapped_providers
                    FROM configuration_migration_audit
                    """.trimIndent(),
                ).use { result ->
                    assertTrue(result.next())
                    assertEquals(providerMappings.size, result.getInt("migrated_connections"))
                    assertEquals(1, result.getInt("migrated_models"))
                    assertEquals(1, result.getInt("skipped_models_without_key"))
                    assertEquals(0, result.getInt("unmapped_providers"))
                }
            }
            assertFalse(tableExists(database, "provider_api_keys"))
            assertFalse(tableExists(database, "user_model_configs"))
            assertFalse(tableExists(database, "model_definitions"))
        }
    }

    private fun connection(): Connection =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)

    private fun tableExists(connection: Connection, name: String): Boolean =
        connection.metaData.getTables(null, "public", name, arrayOf("TABLE")).use { it.next() }

    private val providerMappings = linkedMapOf(
        "openai" to ("openai-compatible" to "https://api.openai.com/v1"),
        "moonshot" to ("openai-compatible" to "https://api.moonshot.cn/v1"),
        "deepseek" to ("openai-compatible" to "https://api.deepseek.com/v1"),
        "zhipu" to ("openai-compatible" to "https://open.bigmodel.cn/api/paas/v4"),
        "anthropic" to ("anthropic" to "https://api.anthropic.com"),
        "minimax" to ("minimax" to "https://api.minimax.chat/v1"),
    )
}
