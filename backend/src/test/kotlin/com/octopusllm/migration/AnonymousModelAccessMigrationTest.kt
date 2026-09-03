package com.octopusllm.migration

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnonymousModelAccessMigrationTest {
    @Test
    fun `V041 contains anonymous policy, bounded lease, import identity, and bulk snapshots`() {
        val sql = requireNotNull(javaClass.classLoader.getResourceAsStream("db/migration/V041__anonymous_model_access.sql"))
            .bufferedReader()
            .use { it.readText() }

        listOf(
            "is_anonymous_allowed",
            "anonymous_request_leases",
            "anonymous_conversation_imports",
            "admin_model_bulk_operations",
            "admin_model_bulk_operation_items",
            "MODEL_ANONYMOUS_ALLOW",
            "MODEL_BULK_OPERATION",
        ).forEach { marker -> assertTrue(sql.contains(marker), "migration is missing $marker") }
        assertTrue(sql.contains("configured_models (connection_id, is_enabled, is_anonymous_allowed"))
        assertTrue(sql.contains("UNIQUE (user_id, source_conversation_id)"))
        assertTrue(sql.contains("outcome IN ('PENDING', 'CHANGED', 'ALREADY_SATISFIED', 'ALREADY_DELETED', 'FAILED')"))
    }

    @Test
    fun `V042 contains the guest default model policy`() {
        val sql = requireNotNull(javaClass.classLoader.getResourceAsStream("db/migration/V042__anonymous_default_models.sql"))
            .bufferedReader()
            .use { it.readText() }

        assertTrue(sql.contains("is_anonymous_default BOOLEAN NOT NULL DEFAULT FALSE"))
        assertTrue(sql.contains("idx_configured_models_anonymous_defaults"))
    }
}
