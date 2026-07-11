package com.octopusllm.tool

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ToolSettingsRepository : JpaRepository<ToolSettings, Short>

interface WebSearchProviderSettingsRepository : JpaRepository<WebSearchProviderSettings, UUID> {
    fun findByProvider(provider: String): WebSearchProviderSettings?
}
