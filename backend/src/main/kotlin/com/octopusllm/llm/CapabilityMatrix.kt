package com.octopusllm.llm

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonProperty

data class CapabilityMatrix(
    @JsonProperty("input_modalities") val inputModalities: List<String> = listOf("text"),
    @JsonProperty("output_modalities") val outputModalities: List<String> = listOf("text"),
    @JsonProperty("context_length_tokens") val contextLengthTokens: Int? = null,
    @JsonProperty("supports_streaming") val supportsStreaming: Boolean = false,
    @JsonProperty("supports_function_calling") val supportsFunctionCalling: Boolean = false,
    @JsonProperty("supports_system_prompt") val supportsSystemPrompt: Boolean = true,
    @JsonProperty("supports_video_input") val supportsVideoInput: Boolean = false,
) {
    val extras: MutableMap<String, Any> = mutableMapOf()

    @JsonAnySetter
    fun setExtra(key: String, value: Any) {
        extras[key] = value
    }
}
