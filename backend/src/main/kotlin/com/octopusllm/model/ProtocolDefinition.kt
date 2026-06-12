package com.octopusllm.model

import com.octopusllm.llm.CapabilityMatrix
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

data class ProtocolDefinition(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String?,
    val baseline: CapabilityMatrix,
)

object ProtocolDefinitions {
    val OPENAI_COMPATIBLE = ProtocolDefinition(
        id = "openai-compatible",
        displayName = "OpenAI-compatible",
        defaultBaseUrl = "https://api.openai.com/v1",
        baseline = CapabilityMatrix(
            inputModalities = listOf("text"),
            outputModalities = listOf("text"),
            supportsStreaming = true,
            supportsFunctionCalling = false,
            supportsSystemPrompt = true,
        ),
    )

    val ANTHROPIC = ProtocolDefinition(
        id = "anthropic",
        displayName = "Anthropic",
        defaultBaseUrl = "https://api.anthropic.com",
        baseline = CapabilityMatrix(
            inputModalities = listOf("text"),
            outputModalities = listOf("text"),
            supportsStreaming = true,
            supportsFunctionCalling = false,
            supportsSystemPrompt = true,
        ),
    )

    val MINIMAX = ProtocolDefinition(
        id = "minimax",
        displayName = "MiniMax",
        defaultBaseUrl = "https://api.minimax.chat/v1",
        baseline = CapabilityMatrix(
            inputModalities = listOf("text"),
            outputModalities = listOf("text"),
            supportsStreaming = true,
            supportsFunctionCalling = false,
            supportsSystemPrompt = true,
        ),
    )

    val all = listOf(OPENAI_COMPATIBLE, ANTHROPIC, MINIMAX)
    private val byId = all.associateBy { it.id }

    fun require(id: String): ProtocolDefinition =
        byId[id] ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown protocol: $id")

    fun mergeCapabilities(
        baseline: CapabilityMatrix,
        overrides: Map<String, Any?>,
    ): CapabilityMatrix {
        val known = setOf(
            "input_modalities",
            "output_modalities",
            "context_length_tokens",
            "supports_streaming",
            "supports_function_calling",
            "supports_system_prompt",
            "supports_video_input",
        )
        val unknown = overrides.keys - known
        if (unknown.isNotEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown capability keys: ${unknown.sorted()}")
        }

        fun stringList(key: String, fallback: List<String>): List<String> {
            val value = overrides[key] ?: return fallback
            val list = value as? List<*>
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$key must be an array of strings")
            if (list.any { it !is String }) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$key must be an array of strings")
            }
            return list.filterIsInstance<String>()
        }

        fun boolean(key: String, fallback: Boolean): Boolean {
            val value = overrides[key] ?: return fallback
            return value as? Boolean
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$key must be a boolean")
        }

        val contextLength = when (val value = overrides["context_length_tokens"]) {
            null -> baseline.contextLengthTokens
            is Number -> value.toInt().takeIf { it > 0 }
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "context_length_tokens must be positive")
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "context_length_tokens must be a number")
        }

        return baseline.copy(
            inputModalities = stringList("input_modalities", baseline.inputModalities),
            outputModalities = stringList("output_modalities", baseline.outputModalities),
            contextLengthTokens = contextLength,
            supportsStreaming = boolean("supports_streaming", baseline.supportsStreaming),
            supportsFunctionCalling = boolean("supports_function_calling", baseline.supportsFunctionCalling),
            supportsSystemPrompt = boolean("supports_system_prompt", baseline.supportsSystemPrompt),
            supportsVideoInput = boolean("supports_video_input", baseline.supportsVideoInput),
        )
    }
}
