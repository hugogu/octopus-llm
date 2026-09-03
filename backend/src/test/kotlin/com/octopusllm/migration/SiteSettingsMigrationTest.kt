package com.octopusllm.migration

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SiteSettingsMigrationTest {
    @Test
    fun `V043 adds the Chinese filing visibility toggle`() {
        val sql = requireNotNull(javaClass.classLoader.getResourceAsStream("db/migration/V043__site_china_filing_toggle.sql"))
            .bufferedReader()
            .use { it.readText() }

        assertTrue(sql.contains("china_filing_enabled BOOLEAN NOT NULL DEFAULT FALSE"))
    }
}
