package com.octopusllm.model

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenRouterModelCatalogueTest {
    private val mapper = ObjectMapper()

    private val sample = """
        {"data":[
          {"id":"openai/gpt-4o","architecture":{"input_modalities":["text","image","file"]}},
          {"id":"qwen/qwen3-vl-32b-instruct","architecture":{"input_modalities":["image","text"]}},
          {"id":"nvidia/nemotron-vl:free","architecture":{"input_modalities":["image","text","video"]}},
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
    fun `buildIndex keeps supported modalities with text first and drops file`() {
        val index = OpenRouterModelCatalogue.buildIndex(mapper, sample)

        assertEquals(listOf("text", "image"), index["gpt-4o"]) // "file" filtered out
        assertEquals(listOf("text", "image"), index["qwen3-vl-32b-instruct"]) // reordered text-first
        assertEquals(listOf("text", "image", "video"), index["nemotron-vl"])
        // Text-only models contribute an entry too (only text), which never enables an attach control.
        assertEquals(listOf("text"), index["text-only"])
        assertNull(index["unknown-model"])
        assertTrue(index["gpt-4o"]!!.first() == "text")
    }
}
