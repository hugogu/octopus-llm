package com.octopusllm.tool

/**
 * Static metadata for the supported web_search providers (feature 009): the selectable presets and,
 * per provider, whether it needs a model (chat-completions-style providers do; dedicated search APIs
 * do not). Single source of truth shared by the settings service and the admin controller.
 */
object WebSearchProviders {
    data class Preset(
        val id: String,
        val label: String,
        val defaultBaseUrl: String,
        val defaultModel: String,
        val needsModel: Boolean,
    )

    val ALL: List<Preset> = listOf(
        Preset("mimo", "MiMo · 小米 (token-plan CN)", "https://token-plan-cn.xiaomimimo.com/v1", "mimo-v2.5-pro", true),
        Preset("mimo-standard", "MiMo · 小米 (standard)", "https://api.xiaomimimo.com/v1", "mimo-v2.5-pro", true),
        Preset("openrouter", "OpenRouter · web 插件", "https://openrouter.ai/api/v1", "deepseek/deepseek-chat", true),
        Preset("glm", "GLM · 智谱 web_search", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", false),
        Preset("tavily", "Tavily Search", "https://api.tavily.com", "", false),
        Preset("kimi", "Kimi · Moonshot (\$web_search)", "https://api.moonshot.ai/v1", "kimi-k2.6", true),
    )

    private val byId: Map<String, Preset> = ALL.associateBy { it.id }

    /** Chat-completions providers need a model; dedicated search APIs do not. Unknown ids default true. */
    fun needsModel(provider: String): Boolean = byId[provider]?.needsModel ?: true
}
