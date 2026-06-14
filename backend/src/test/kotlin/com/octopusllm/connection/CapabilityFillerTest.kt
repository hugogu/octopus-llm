package com.octopusllm.connection

import com.octopusllm.model.CapabilityDetector
import com.octopusllm.model.DetectedModelInfo
import com.octopusllm.testsupport.Feature003Fixtures
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CapabilityFillerTest {
    private val detector = mockk<CapabilityDetector>(relaxed = true)
    private val filler = CapabilityFiller(detector)

    private val gptDetail = DetectedModelInfo(
        modalities = listOf("text", "image"),
        inputPricePerMtok = BigDecimal("2.5000"),
        outputPricePerMtok = BigDecimal("10.0000"),
        contextLengthTokens = 128000,
        supportsFunctionCalling = true,
    )

    @Test
    fun `fills modalities, pricing, context, and tools for an unset model`() {
        val model = Feature003Fixtures.configuredModel(modelId = "gpt-4o")
        every { detector.detailFor("gpt-4o") } returns gptDetail

        val updated = filler.fill(listOf(model))

        assertEquals(1, updated.size)
        assertEquals(listOf("text", "image"), model.capabilityOverrides["input_modalities"])
        assertEquals(128000, model.capabilityOverrides["context_length_tokens"])
        assertEquals(true, model.capabilityOverrides["supports_function_calling"])
        assertEquals(BigDecimal("2.5000"), model.inputPricePerMtok)
        assertEquals("USD", model.priceCurrency)
    }

    @Test
    fun `does not overwrite manually set modalities or pricing`() {
        val model = Feature003Fixtures.configuredModel(modelId = "gpt-4o").apply {
            capabilityOverrides = mapOf("input_modalities" to listOf("text"))
            inputPricePerMtok = BigDecimal("9.0000")
            priceCurrency = "EUR"
        }
        every { detector.detailFor("gpt-4o") } returns gptDetail

        val updated = filler.fill(listOf(model))

        // Context + tools still fill (they were unset), but modalities + pricing are preserved.
        assertEquals(listOf("text"), model.capabilityOverrides["input_modalities"])
        assertEquals(BigDecimal("9.0000"), model.inputPricePerMtok)
        assertEquals("EUR", model.priceCurrency)
        assertTrue(updated.size == 1) // context/tools changed
    }
}
