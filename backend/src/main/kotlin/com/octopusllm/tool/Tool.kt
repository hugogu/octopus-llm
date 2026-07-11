package com.octopusllm.tool

/**
 * Declarative description of a tool (feature 009): the model-facing name, purpose, and a JSON-Schema
 * object describing its parameters. Adapters translate this into each provider's tool/function schema.
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    /** JSON-Schema object (`{"type":"object","properties":{...},"required":[...]}`). */
    val parameters: Map<String, Any?>,
)

/**
 * An executable tool. Implementations are Spring beans discovered by [ToolRegistry]. [execute] is
 * synchronous and may block on external I/O; the [ToolExecutor] runs it off the event loop and applies
 * the timeout/retry policy, so implementations should not add their own timeouts.
 */
interface Tool {
    val definition: ToolDefinition

    /**
     * Whether the tool is usable right now (feature 009). Tools needing runtime config (e.g. web_search
     * needs an admin-configured provider) report false when unconfigured, so the registry does not
     * advertise them to models. Defaults to true for self-contained tools.
     */
    fun isAvailable(): Boolean = true

    fun execute(arguments: Map<String, Any?>): ToolResult
}
