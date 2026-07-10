package com.octopusllm.tool

import org.springframework.stereotype.Component

/**
 * Name → [Tool] lookup over all [Tool] beans (feature 009). Built-in tools register by being Spring
 * components; [definitions] is what the adapter layer advertises to capable models each turn.
 */
@Component
class ToolRegistry(tools: List<Tool>) {
    private val byName: Map<String, Tool> = tools.associateBy { it.definition.name }

    init {
        require(byName.size == tools.size) {
            "Duplicate tool names registered: ${tools.groupingBy { it.definition.name }.eachCount().filterValues { it > 1 }.keys}"
        }
    }

    fun find(name: String): Tool? = byName[name]

    fun definitions(): List<ToolDefinition> = byName.values.map { it.definition }
}
