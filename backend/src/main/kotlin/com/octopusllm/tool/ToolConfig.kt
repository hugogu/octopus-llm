package com.octopusllm.tool

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Registers configurable built-in tools (feature 009). The web_search tool is only wired when a provider
 * key is configured (`app.tools.web-search.api-key`, typically via `APP_TOOLS_WEB_SEARCH_API_KEY`), so an
 * unconfigured deployment simply doesn't advertise it. The key is a deployment secret and is never stored
 * in the database or logged.
 */
@Configuration
class ToolConfig {
    @Bean
    @ConditionalOnProperty(prefix = "app.tools.web-search", name = ["api-key"])
    fun webSearchTool(
        @Value("\${app.tools.web-search.api-key}") apiKey: String,
        @Value("\${app.tools.web-search.base-url:https://token-plan-cn.xiaomimimo.com/v1}") baseUrl: String,
        @Value("\${app.tools.web-search.model:mimo-v2.5-pro}") model: String,
        @Value("\${app.tools.web-search.limit:3}") limit: Int,
        objectMapper: ObjectMapper,
    ): Tool = WebSearchTool(apiKey, baseUrl, model, limit, objectMapper)
}
