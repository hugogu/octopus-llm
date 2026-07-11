package com.octopusllm.admin

import com.octopusllm.tool.ToolSettings
import com.octopusllm.tool.ToolSettingsService
import com.octopusllm.tool.ToolSettingsUpdate
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
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

/** A selectable web_search provider preset, prefilling the base URL + model. */
data class WebSearchProviderView(val id: String, val label: String, val defaultBaseUrl: String, val defaultModel: String)

data class WebSearchConfigView(
    val enabled: Boolean,
    val provider: String,
    val baseUrl: String?,
    val model: String?,
    val apiKeySet: Boolean,
)

data class ToolSettingsView(
    val tools: List<ToolInfoView>,
    val webSearch: WebSearchConfigView,
    val webSearchProviders: List<WebSearchProviderView>,
    val updatedAt: Instant,
    val updatedBy: UUID?,
)

/**
 * Admin tool configuration (feature 009). Lists the supported built-in tools and lets the admin choose
 * and configure the web_search provider. The provider API key is never returned — only `apiKeySet`.
 * Lives under the admin-only `/api/v2/admin` path space (gated in SecurityConfig).
 */
@RestController
@RequestMapping("/api/v2/admin/tool-settings")
class ToolSettingsController(
    private val service: ToolSettingsService,
) {
    companion object {
        private val PROVIDERS = listOf(
            WebSearchProviderView("mimo", "MiMo · 小米 (token-plan CN)", "https://token-plan-cn.xiaomimimo.com/v1", "mimo-v2.5-pro"),
            WebSearchProviderView("mimo-standard", "MiMo · 小米 (standard)", "https://api.xiaomimimo.com/v1", "mimo-v2.5-pro"),
            WebSearchProviderView("openrouter", "OpenRouter · web 插件", "https://openrouter.ai/api/v1", "deepseek/deepseek-chat"),
            WebSearchProviderView("glm", "GLM · 智谱 web_search", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash"),
        )
    }

    @GetMapping
    fun get(): Mono<ToolSettingsView> =
        Mono.fromCallable { view(service.get()) }.subscribeOn(Schedulers.boundedElastic())

    @PutMapping
    fun update(
        @AuthenticationPrincipal principal: String,
        @RequestBody request: ToolSettingsUpdate,
    ): Mono<ToolSettingsView> =
        Mono.fromCallable { view(service.update(UUID.fromString(principal), request)) }
            .subscribeOn(Schedulers.boundedElastic())

    private fun view(s: ToolSettings): ToolSettingsView {
        val webSearchAvailable = s.webSearchEnabled &&
            !s.webSearchBaseUrl.isNullOrBlank() && !s.webSearchModel.isNullOrBlank() && !s.webSearchApiKey.isNullOrBlank()
        return ToolSettingsView(
            tools = listOf(
                ToolInfoView("current_time", "当前时间", "返回当前日期与时间，无需外部依赖。", configurable = false, available = true),
                ToolInfoView("web_search", "联网搜索", "调用搜索 Provider 联网检索最新信息（涵盖新闻/股价/天气）。", configurable = true, available = webSearchAvailable),
            ),
            webSearch = WebSearchConfigView(
                enabled = s.webSearchEnabled,
                provider = s.webSearchProvider,
                baseUrl = s.webSearchBaseUrl,
                model = s.webSearchModel,
                apiKeySet = !s.webSearchApiKey.isNullOrBlank(),
            ),
            webSearchProviders = PROVIDERS,
            updatedAt = s.updatedAt,
            updatedBy = s.updatedBy,
        )
    }
}
