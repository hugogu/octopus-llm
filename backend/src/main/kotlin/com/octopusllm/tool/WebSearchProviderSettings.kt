package com.octopusllm.tool

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One web_search provider's saved config (feature 009). All configured providers coexist — the active
 * one is selected in [ToolSettings]. The API key is stored encrypted and never returned by the API.
 */
@Entity
@Table(name = "web_search_provider_settings")
class WebSearchProviderSettings(
    @Id
    @Column(columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, length = 32, unique = true)
    val provider: String,

    @Column(name = "base_url", columnDefinition = "TEXT")
    var baseUrl: String? = null,

    @Column(columnDefinition = "TEXT")
    var model: String? = null,

    @Column(name = "api_key", columnDefinition = "TEXT")
    var apiKey: String? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "updated_by", columnDefinition = "UUID")
    var updatedBy: UUID? = null,
)
