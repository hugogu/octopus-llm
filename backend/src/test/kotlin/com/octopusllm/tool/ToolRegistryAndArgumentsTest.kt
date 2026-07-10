package com.octopusllm.tool

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ToolRegistryAndArgumentsTest {

    private fun tool(name: String) = object : Tool {
        override val definition = ToolDefinition(name, "d", emptyMap())
        override fun execute(arguments: Map<String, Any?>) = ToolResult.Success(emptyMap())
    }

    @Test
    fun `registry resolves tools by name and lists definitions`() {
        val registry = ToolRegistry(listOf(tool("current_time"), tool("weather")))

        assertEquals("weather", registry.find("weather")?.definition?.name)
        assertNull(registry.find("missing"))
        assertEquals(setOf("current_time", "weather"), registry.definitions().map { it.name }.toSet())
    }

    @Test
    fun `registry rejects duplicate tool names`() {
        assertThrows(IllegalArgumentException::class.java) {
            ToolRegistry(listOf(tool("dupe"), tool("dupe")))
        }
    }

    @Test
    fun `argument hash is stable regardless of key order`() {
        val a = ToolArguments.hash(mapOf("city" to "上海", "unit" to "c"))
        val b = ToolArguments.hash(mapOf("unit" to "c", "city" to "上海"))
        val different = ToolArguments.hash(mapOf("city" to "北京", "unit" to "c"))

        assertEquals(a, b)
        assertEquals(64, a.length) // SHA-256 hex
        assert(a != different)
    }
}
