package com.octopusllm.userconfig

import com.octopusllm.llm.CapabilityMatrix
import com.octopusllm.model.ModelDefinition
import com.octopusllm.model.ModelDefinitionRepository
import com.octopusllm.model.ModelSource
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant
import java.util.UUID

private data class DiscoveredModel(
    val id: String,
    val displayName: String,
    val capabilityMatrix: CapabilityMatrix,
)

@Service
class ProviderModelSyncService(
    private val apiKeyRepository: ProviderApiKeyRepository,
    private val modelDefinitionRepository: ModelDefinitionRepository,
    private val encryptionService: ApiKeyEncryptionService,
) {
    private val openAiCompatibleBaseUrls = mapOf(
        "openai" to "https://api.openai.com/v1",
        "moonshot" to "https://api.moonshot.cn/v1",
        "deepseek" to "https://api.deepseek.com/v1",
        "zhipu" to "https://open.bigmodel.cn/api/paas/v4",
    )

    fun syncProviderModels(userId: UUID, providerId: String, providerApiKeyId: UUID? = null): Mono<List<ModelDefinition>> =
        Mono.fromCallable {
            val apiKey = resolveApiKey(userId, providerId, providerApiKeyId)
            val decryptedKey = encryptionService.decrypt(apiKey.encryptedKey, apiKey.keyIv)
            val discovered = discoverModels(providerId, decryptedKey)
            upsertModels(providerId, discovered)
        }.subscribeOn(Schedulers.boundedElastic())

    private fun resolveApiKey(userId: UUID, providerId: String, providerApiKeyId: UUID?): ProviderApiKey {
        val key = if (providerApiKeyId != null) {
            apiKeyRepository.findById(providerApiKeyId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found")
            }.also {
                if (it.user.id != userId) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
                if (it.providerId != providerId) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "API key provider does not match request provider")
                }
            }
        } else {
            apiKeyRepository.findByUserId(userId).firstOrNull { it.providerId == providerId }
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No API key found for provider: $providerId")
        }
        return key
    }

    private fun discoverModels(providerId: String, decryptedApiKey: String): List<DiscoveredModel> {
        val baseUrl = openAiCompatibleBaseUrls[providerId]
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider does not support dynamic model discovery: $providerId")

        val client: OpenAIClient = OpenAIOkHttpClient.builder()
            .apiKey(decryptedApiKey)
            .baseUrl(baseUrl)
            .build()

        try {
            return client.models()
                .list()
                .autoPager()
                .toList()
                .asSequence()
                .map { model -> model.id() }
                .filter { shouldKeepDiscoveredModel(providerId, it) }
                .distinct()
                .sorted()
                .map { modelId ->
                    DiscoveredModel(
                        id = modelId,
                        displayName = displayNameFor(providerId, modelId),
                        capabilityMatrix = capabilityMatrixFor(providerId, modelId),
                    )
                }
                .toList()
        } finally {
            client.close()
        }
    }

    private fun shouldKeepDiscoveredModel(providerId: String, modelId: String): Boolean =
        when (providerId) {
            "deepseek" -> modelId !in setOf("deepseek-chat", "deepseek-reasoner")
            "moonshot" -> modelId !in setOf(
                "kimi-k2-0905-preview",
                "kimi-k2-0711-preview",
                "kimi-k2-turbo-preview",
                "kimi-k2-thinking",
                "kimi-k2-thinking-turbo",
                "kimi-latest",
                "kimi-thinking-preview",
            )
            else -> true
        }

    private fun capabilityMatrixFor(providerId: String, modelId: String): CapabilityMatrix =
        when {
            providerId == "moonshot" && modelId in setOf("kimi-k2.5", "kimi-k2.6") -> CapabilityMatrix(
                inputModalities = listOf("text", "image", "video"),
                outputModalities = listOf("text"),
                contextLengthTokens = 256000,
                supportsStreaming = true,
                supportsFunctionCalling = true,
                supportsSystemPrompt = true,
                supportsVideoInput = true,
            )
            providerId == "moonshot" && modelId.contains("vision") -> CapabilityMatrix(
                inputModalities = listOf("text", "image"),
                outputModalities = listOf("text"),
                contextLengthTokens = parseMoonshotContext(modelId),
                supportsStreaming = true,
                supportsFunctionCalling = false,
                supportsSystemPrompt = true,
                supportsVideoInput = false,
            )
            providerId == "moonshot" && modelId.startsWith("moonshot-v1-") -> CapabilityMatrix(
                inputModalities = listOf("text"),
                outputModalities = listOf("text"),
                contextLengthTokens = parseMoonshotContext(modelId),
                supportsStreaming = true,
                supportsFunctionCalling = false,
                supportsSystemPrompt = true,
                supportsVideoInput = false,
            )
            providerId == "deepseek" -> CapabilityMatrix(
                inputModalities = listOf("text"),
                outputModalities = listOf("text"),
                contextLengthTokens = 65536,
                supportsStreaming = true,
                supportsFunctionCalling = true,
                supportsSystemPrompt = true,
                supportsVideoInput = false,
            )
            else -> CapabilityMatrix(
                inputModalities = listOf("text"),
                outputModalities = listOf("text"),
                supportsStreaming = true,
                supportsFunctionCalling = true,
                supportsSystemPrompt = true,
                supportsVideoInput = false,
            )
        }

    private fun parseMoonshotContext(modelId: String): Int? =
        when {
            modelId.contains("128k") -> 128000
            modelId.contains("32k") -> 32000
            modelId.contains("8k") -> 8000
            else -> null
        }

    private fun displayNameFor(providerId: String, modelId: String): String =
        when (providerId) {
            "moonshot" -> modelId.replace('-', ' ').split(' ')
                .joinToString(" ") { token ->
                    when {
                        token.equals("kimi", ignoreCase = true) -> "Kimi"
                        token.equals("moonshot", ignoreCase = true) -> "Moonshot"
                        token.startsWith("k2.") -> "K2.${token.substringAfter("k2.")}"
                        token.equals("v1", ignoreCase = true) -> "V1"
                        else -> token.uppercase().takeIf { token.length <= 3 } ?: token.replaceFirstChar { it.uppercase() }
                    }
                }
            "deepseek" -> modelId.replace('-', ' ').split(' ')
                .joinToString(" ") { token -> token.replaceFirstChar { it.uppercase() } }
            else -> modelId
        }

    private fun upsertModels(providerId: String, discovered: List<DiscoveredModel>): List<ModelDefinition> {
        val existingById = modelDefinitionRepository.findByProviderId(providerId).associateBy { it.id }
        val discoveredIds = discovered.map { it.id }.toSet()
        val now = Instant.now()

        val toSave = mutableListOf<ModelDefinition>()
        discovered.forEach { model ->
            val existing = existingById[model.id]
            if (existing?.source == ModelSource.CUSTOM) return@forEach
            toSave += ModelDefinition(
                id = model.id,
                providerId = providerId,
                displayName = existing?.displayName ?: model.displayName,
                capabilityMatrix = existing?.capabilityMatrix ?: model.capabilityMatrix,
                isActive = true,
                source = ModelSource.DISCOVERED,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
        }

        existingById.values
            .filter { it.source != ModelSource.CUSTOM && it.id !in discoveredIds && it.isActive }
            .forEach { stale ->
                toSave += ModelDefinition(
                    id = stale.id,
                    providerId = stale.providerId,
                    displayName = stale.displayName,
                    capabilityMatrix = stale.capabilityMatrix,
                    isActive = false,
                    source = stale.source,
                    createdAt = stale.createdAt,
                    updatedAt = now,
                )
            }

        return modelDefinitionRepository.saveAll(toSave)
            .filter { it.isActive }
            .sortedBy { it.displayName.lowercase() }
    }
}
