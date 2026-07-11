package com.octopusllm.tool

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Admin-managed tool configuration (feature 009). Single mutable row (id = 1) holding the provider
 * config for the built-in web_search tool, so the search backend is chosen in the admin panel instead
 * of being fixed at deploy time. The provider key is stored encrypted and never exposed by the API.
 */
@Entity
@Table(name = "tool_settings")
class ToolSettings(
    @Id
    @Column(name = "id")
    val id: Short = SINGLETON_ID,

    @Column(name = "web_search_enabled", nullable = false)
    var webSearchEnabled: Boolean = false,

    @Column(name = "web_search_provider", nullable = false, length = 32)
    var webSearchProvider: String = "mimo",

    @Column(name = "web_search_base_url", columnDefinition = "TEXT")
    var webSearchBaseUrl: String? = null,

    @Column(name = "web_search_model", columnDefinition = "TEXT")
    var webSearchModel: String? = null,

    @Column(name = "web_search_api_key", columnDefinition = "TEXT")
    var webSearchApiKey: String? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "updated_by", columnDefinition = "UUID")
    var updatedBy: UUID? = null,
) {
    companion object {
        const val SINGLETON_ID: Short = 1
    }
}
