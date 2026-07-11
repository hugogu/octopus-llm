package com.octopusllm.admin

import com.octopusllm.tool.ToolSettings
import com.octopusllm.tool.ToolSettingsActivationUpdate
import com.octopusllm.tool.ToolSettingsService
import com.octopusllm.tool.WebSearchProviderUpdate
import com.octopusllm.tool.WebSearchProviders
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant
import java.util.UUID

/** One built-in tool in the admin listing. */
data class ToolInfoView(
    val name: String,
    val label: String,
    val description: String,
    val configurable: Boolean,
    val available: Boolean,
)

/** A web_search provider: its preset metadata merged with the admin's saved config (key masked). */
data class WebSearchProviderView(
    val id: String,
    val label: String,
    val needsModel: Boolean,
    val baseUrl: String,
    val model: String,
    val apiKeySet: Boolean,
)

data class ToolSettingsView(
    val tools: List<ToolInfoView>,
    val webSearchEnabled: Boolean,
    val webSearchActiveProvider: String,
    val webSearchProviders: List<WebSearchProviderView>,
    val updatedAt: Instant,
    val updatedBy: UUID?,
)

/**
 * Admin tool configuration (feature 009). Lists the built-in tools and lets the admin configure each
 * web_search provider independently (so they coexist) and choose which one is active. Provider API keys
 * are never returned — only `apiKeySet`. Admin-only (the /api/v2/admin path space is gated in SecurityConfig).
 */
@RestController
@RequestMapping("/api/v2/admin/tool-settings")
class ToolSettingsController(
    private val service: ToolSettingsService,
) {
    @GetMapping
    fun get(): Mono<ToolSettingsView> =
        Mono.fromCallable { view() }.subscribeOn(Schedulers.boundedElastic())

    /** Update one provider's config (base URL / model / key). Key blank keeps the stored key. */
    @PutMapping("/providers/{provider}")
    fun updateProvider(
        @AuthenticationPrincipal principal: String,
        @PathVariable provider: String,
        @RequestBody request: WebSearchProviderUpdate,
    ): Mono<ToolSettingsView> =
        Mono.fromCallable {
            service.updateProvider(UUID.fromString(principal), provider, request)
            view()
        }.subscribeOn(Schedulers.boundedElastic())

    /** Enable/disable web_search and choose the active provider. */
    @PutMapping
    fun updateActivation(
        @AuthenticationPrincipal principal: String,
        @RequestBody request: ToolSettingsActivationUpdate,
    ): Mono<ToolSettingsView> =
        Mono.fromCallable {
            service.updateActivation(UUID.fromString(principal), request)
            view()
        }.subscribeOn(Schedulers.boundedElastic())

    private fun view(): ToolSettingsView {
        val settings: ToolSettings = service.get()
        val saved = service.providerConfigs()
        val active = settings.webSearchActiveProvider
        val activeConfigured = saved[active]?.let {
            !it.baseUrl.isNullOrBlank() && !it.apiKey.isNullOrBlank() &&
                (!WebSearchProviders.needsModel(active) || !it.model.isNullOrBlank())
        } ?: false
        return ToolSettingsView(
            tools = listOf(
                ToolInfoView("current_time", "当前时间", "返回当前日期与时间，无需外部依赖。", configurable = false, available = true),
                ToolInfoView(
                    "web_search", "联网搜索", "调用搜索 Provider 联网检索最新信息（涵盖新闻/股价/天气）。",
                    configurable = true, available = settings.webSearchEnabled && activeConfigured,
                ),
            ),
            webSearchEnabled = settings.webSearchEnabled,
            webSearchActiveProvider = active,
            webSearchProviders = WebSearchProviders.ALL.map { preset ->
                val row = saved[preset.id]
                WebSearchProviderView(
                    id = preset.id,
                    label = preset.label,
                    needsModel = preset.needsModel,
                    baseUrl = row?.baseUrl?.takeIf { it.isNotBlank() } ?: preset.defaultBaseUrl,
                    model = row?.model?.takeIf { it.isNotBlank() } ?: preset.defaultModel,
                    apiKeySet = !row?.apiKey.isNullOrBlank(),
                )
            },
            updatedAt = settings.updatedAt,
            updatedBy = settings.updatedBy,
        )
    }
}
