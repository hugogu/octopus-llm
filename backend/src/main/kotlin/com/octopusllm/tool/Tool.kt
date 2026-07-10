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

    fun execute(arguments: Map<String, Any?>): ToolResult
}
