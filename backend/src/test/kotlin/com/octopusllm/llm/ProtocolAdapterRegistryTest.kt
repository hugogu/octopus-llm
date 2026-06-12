package com.octopusllm.llm

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ProtocolAdapterRegistryTest {
    @Test
    fun `resolves any Spring supplied protocol adapter without registry changes`() {
        val adapter = mockk<LlmAdapter>()
        every { adapter.protocolId } returns "custom-protocol"
        assertSame(adapter, ProtocolAdapterRegistry(listOf(adapter)).getAdapter("custom-protocol"))
    }

    @Test
    fun `rejects duplicate protocol registrations`() {
        val first = mockk<LlmAdapter>()
        val second = mockk<LlmAdapter>()
        every { first.protocolId } returns "duplicate"
        every { second.protocolId } returns "duplicate"
        assertThrows(IllegalArgumentException::class.java) {
            ProtocolAdapterRegistry(listOf(first, second))
        }
    }

    @Test
    fun `rejects unknown protocol lookup`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProtocolAdapterRegistry(emptyList()).getAdapter("missing")
        }
    }
}
