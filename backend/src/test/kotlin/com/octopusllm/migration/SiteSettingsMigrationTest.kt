package com.octopusllm.migration

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SiteSettingsMigrationTest {
    @Test
    fun `V044 adds the Google Analytics Measurement ID`() {
        val sql = requireNotNull(javaClass.classLoader.getResourceAsStream("db/migration/V044__google_analytics_measurement_id.sql"))
            .bufferedReader()
            .use { it.readText() }

        assertTrue(sql.contains("google_analytics_measurement_id TEXT"))
    }

    @Test
    fun `V043 adds the Chinese filing visibility toggle`() {
        val sql = requireNotNull(javaClass.classLoader.getResourceAsStream("db/migration/V043__site_china_filing_toggle.sql"))
            .bufferedReader()
            .use { it.readText() }

        assertTrue(sql.contains("china_filing_enabled BOOLEAN NOT NULL DEFAULT FALSE"))
    }
}
