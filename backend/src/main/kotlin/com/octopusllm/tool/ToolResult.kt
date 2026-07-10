package com.octopusllm.tool

/**
 * Outcome of executing a [Tool] (feature 009). Provider-independent: adapters map this into whatever
 * shape each LLM expects for a tool result. [Success.data] is a structured, JSON-serializable map;
 * [Failure] distinguishes an ordinary error from a timeout so the executor and persistence layer can
 * record the terminal [ToolInvocationStatus] accurately.
 */
sealed class ToolResult {
    data class Success(val data: Map<String, Any?>) : ToolResult()

    data class Failure(val errorMessage: String, val timedOut: Boolean = false) : ToolResult()

    val status: ToolInvocationStatus
        get() = when (this) {
            is Success -> ToolInvocationStatus.SUCCESS
            is Failure -> if (timedOut) ToolInvocationStatus.TIMEOUT else ToolInvocationStatus.FAILED
        }
}

/** Lifecycle of a tool invocation; the string values are the persisted `tool_invocations.status`. */
enum class ToolInvocationStatus(val value: String) {
    PENDING("pending"),
    RUNNING("running"),
    SUCCESS("success"),
    FAILED("failed"),
    TIMEOUT("timeout"),
}
