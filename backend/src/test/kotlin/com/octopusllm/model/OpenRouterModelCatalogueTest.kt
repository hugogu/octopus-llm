package com.octopusllm.model

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OpenRouterModelCatalogueTest {
    private val mapper = ObjectMapper()

    private val sample = """
        {"data":[
          {"id":"openai/gpt-4o","context_length":128000,
           "architecture":{"input_modalities":["text","image","file"]},
           "pricing":{"prompt":"0.0000025","completion":"0.00001"},
           "supported_parameters":["tools","temperature"]},
          {"id":"qwen/qwen3-vl-32b-instruct",
           "architecture":{"input_modalities":["image","text"]},
           "pricing":{"prompt":"0","completion":"0"}},
          {"id":"some/text-only","architecture":{"input_modalities":["text"]}}
        ]}
    """.trimIndent()

    @Test
    fun `normalize strips provider namespace and suffix`() {
        assertEquals("gpt-4o", OpenRouterModelCatalogue.normalize("openai/gpt-4o"))
        assertEquals("nemotron-vl", OpenRouterModelCatalogue.normalize("nvidia/nemotron-vl:free"))
        assertEquals("gpt-4o", OpenRouterModelCatalogue.normalize("GPT-4o"))
    }

    @Test
    fun `buildIndex captures modalities, pricing per 1M, context length, and tools`() {
        val index = OpenRouterModelCatalogue.buildIndex(mapper, sample)

        val gpt = index["gpt-4o"]!!
        assertEquals(listOf("text", "image"), gpt.modalities) // "file" filtered, text first
        assertEquals(BigDecimal("2.5000"), gpt.inputPricePerMtok) // 0.0000025 * 1e6
        assertEquals(BigDecimal("10.0000"), gpt.outputPricePerMtok)
        assertEquals(128000, gpt.contextLengthTokens)
        assertTrue(gpt.supportsFunctionCalling)

        val qwen = index["qwen3-vl-32b-instruct"]!!
        assertEquals(listOf("text", "image"), qwen.modalities)
        assertEquals(BigDecimal("0.0000"), qwen.inputPricePerMtok)
        assertFalse(qwen.supportsFunctionCalling)
        assertNull(qwen.contextLengthTokens)

        assertEquals(listOf("text"), index["text-only"]!!.modalities)
        assertNull(index["unknown"])
    }
}
