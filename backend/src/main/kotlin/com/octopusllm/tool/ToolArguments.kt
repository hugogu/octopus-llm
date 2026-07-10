package com.octopusllm.tool

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import java.security.MessageDigest

/**
 * Canonicalizes tool arguments so that semantically identical invocations hash identically regardless
 * of key ordering (feature 009). The hash keys per-turn deduplication and is persisted as
 * `tool_invocations.arguments_hash`.
 */
object ToolArguments {
    // Sorting map entries makes the serialization order-insensitive; a private mapper keeps this
    // independent of any app-wide Jackson configuration.
    private val canonicalMapper: ObjectMapper = ObjectMapper()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)

    fun canonicalJson(arguments: Map<String, Any?>): String =
        canonicalMapper.writeValueAsString(arguments)

    fun hash(arguments: Map<String, Any?>): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(canonicalJson(arguments).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
