package com.octopusllm.userconfig

import com.octopusllm.auth.UserRepository
import com.octopusllm.llm.CapabilityMatrix
import com.octopusllm.model.ModelDefinitionRepository
import com.octopusllm.model.ModelSource
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant
import java.util.UUID

@Service
class UserConfigService(
    private val userRepository: UserRepository,
    private val apiKeyRepository: ProviderApiKeyRepository,
    private val modelConfigRepository: UserModelConfigRepository,
    private val modelDefinitionRepository: ModelDefinitionRepository,
    private val encryptionService: ApiKeyEncryptionService,
    private val providerModelSyncService: ProviderModelSyncService,
    private val preferenceRepository: UserPreferenceRepository,
) {

    fun listApiKeys(userId: UUID): Mono<List<ProviderApiKey>> =
        Mono.fromCallable { apiKeyRepository.findByUserId(userId) }
            .subscribeOn(Schedulers.boundedElastic())

    fun addApiKey(
        userId: UUID,
        providerId: String,
        rawKey: String,
        label: String?,
        baseUrl: String? = null,
    ): Mono<ProviderApiKey> =
        Mono.fromCallable {
            val user = userRepository.findById(userId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
            }
            val encrypted = encryptionService.encrypt(rawKey)
            val key = ProviderApiKey(
                user = user,
                providerId = providerId,
                encryptedKey = encrypted.ciphertext,
                keyIv = encrypted.iv,
                label = label,
                baseUrl = normalizeBaseUrl(baseUrl),
            )
            apiKeyRepository.save(key)
        }.subscribeOn(Schedulers.boundedElastic())

    fun patchApiKey(userId: UUID, keyId: UUID, baseUrl: String?): Mono<ProviderApiKey> =
        Mono.fromCallable {
            val key = apiKeyRepository.findById(keyId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found")
            }
            if (key.user.id != userId) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
            if (baseUrl != null) {
                key.baseUrl = normalizeBaseUrl(baseUrl)
                key.updatedAt = Instant.now()
                apiKeyRepository.save(key)
            } else {
                key
            }
        }.subscribeOn(Schedulers.boundedElastic())

    private fun normalizeBaseUrl(baseUrl: String?): String? {
        val trimmed = baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return null
        if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Base URL must start with http:// or https://")
        }
        return trimmed
    }

    fun syncProviderModels(userId: UUID, providerId: String, providerApiKeyId: UUID? = null) =
        providerModelSyncService.syncProviderModels(userId, providerId, providerApiKeyId)

    @Transactional
    fun deleteApiKey(userId: UUID, keyId: UUID): Mono<Unit> =
        Mono.fromCallable {
            val key = apiKeyRepository.findById(keyId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found")
            }
            if (key.user.id != userId) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")

            // Disable all model configs linked to this key (ON DELETE SET NULL handled by DB; app-level disable)
            val affectedConfigs = modelConfigRepository.findByProviderApiKeyId(keyId)
            affectedConfigs.forEach { config ->
                config.isEnabled = false
                config.updatedAt = Instant.now()
            }
            modelConfigRepository.saveAll(affectedConfigs)
            apiKeyRepository.delete(key)
        }.subscribeOn(Schedulers.boundedElastic()).thenReturn(Unit)

    fun listModelConfigs(userId: UUID): Mono<List<UserModelConfig>> =
        Mono.fromCallable { modelConfigRepository.findByUserId(userId) }
            .subscribeOn(Schedulers.boundedElastic())

    fun addModelConfig(
        userId: UUID,
        modelId: String,
        apiKeyId: UUID,
        isEnabled: Boolean = true,
        customParams: Map<String, Any?> = emptyMap(),
    ): Mono<UserModelConfig> =
        Mono.fromCallable {
            val user = userRepository.findById(userId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
            }
            val model = modelDefinitionRepository.findByIdAndIsActiveTrue(modelId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found: $modelId")
            val apiKey = apiKeyRepository.findById(apiKeyId).orElseThrow {
                ResponseStatusException(HttpStatus.BAD_REQUEST, "API key not found")
            }
            if (apiKey.user.id != userId) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
            if (apiKey.providerId != model.providerId) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "API key provider does not match model provider")
            }
            val existing = modelConfigRepository.findByUserIdAndModelId(userId, modelId)
            if (existing != null) {
                existing.providerApiKey = apiKey
                existing.isEnabled = isEnabled
                existing.customParams = customParams
                existing.updatedAt = Instant.now()
                modelConfigRepository.save(existing)
            } else {
                val config = UserModelConfig(
                    user = user,
                    model = model,
                    providerApiKey = apiKey,
                    isEnabled = isEnabled,
                    customParams = customParams,
                )
                modelConfigRepository.save(config)
            }
        }.subscribeOn(Schedulers.boundedElastic())

    fun patchModelConfig(
        userId: UUID,
        configId: UUID,
        providerApiKeyId: UUID?,
        isEnabled: Boolean?,
        customParams: Map<String, Any?>?,
    ): Mono<UserModelConfig> =
        Mono.fromCallable {
            val config = modelConfigRepository.findById(configId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Config not found")
            }
            if (config.user.id != userId) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
            val nextKey = if (providerApiKeyId != null) {
                apiKeyRepository.findById(providerApiKeyId).orElseThrow {
                    ResponseStatusException(HttpStatus.BAD_REQUEST, "API key not found")
                }.also { key ->
                    if (key.user.id != userId) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
                    if (key.providerId != config.model.providerId) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "API key provider does not match model provider")
                    }
                }
            } else {
                config.providerApiKey
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "API key deleted; re-add a key first")
            }

            config.providerApiKey = nextKey
            if (isEnabled != null) config.isEnabled = isEnabled
            if (customParams != null) config.customParams = customParams
            config.updatedAt = Instant.now()
            modelConfigRepository.save(config)
        }.subscribeOn(Schedulers.boundedElastic())

    fun createCustomModel(
        userId: UUID,
        providerId: String,
        modelId: String,
        displayName: String?,
        providerApiKeyId: UUID,
        isEnabled: Boolean,
        customParams: Map<String, Any?>,
        capabilityMatrix: CapabilityMatrix,
    ): Mono<UserModelConfig> =
        Mono.fromCallable {
            val existing = modelDefinitionRepository.findById(modelId).orElse(null)
            if (existing != null && existing.providerId != providerId) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Model ID already exists for another provider")
            }

            val model = existing ?: modelDefinitionRepository.save(
                com.octopusllm.model.ModelDefinition(
                    id = modelId,
                    providerId = providerId,
                    displayName = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: modelId,
                    capabilityMatrix = capabilityMatrix,
                    isActive = true,
                    source = ModelSource.CUSTOM,
                )
            )

            if (existing != null && existing.source != ModelSource.CUSTOM) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Model already exists in the shared catalogue")
            }

            model
        }.subscribeOn(Schedulers.boundedElastic()).flatMap {
            addModelConfig(userId, modelId, providerApiKeyId, isEnabled, customParams)
        }

    fun deleteModelConfig(userId: UUID, configId: UUID): Mono<Unit> =
        Mono.fromCallable {
            val config = modelConfigRepository.findById(configId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Config not found")
            }
            if (config.user.id != userId) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
            modelConfigRepository.delete(config)
        }.subscribeOn(Schedulers.boundedElastic()).thenReturn(Unit)

    // User Preferences

    fun getPreferences(userId: UUID): Mono<UserPreference> =
        Mono.fromCallable {
            preferenceRepository.findByUserId(userId)
                ?: UserPreference(
                    user = userRepository.findById(userId).orElseThrow {
                        ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
                    }
                )
        }.subscribeOn(Schedulers.boundedElastic())

    fun updatePreferences(
        userId: UUID,
        lastSelectedModelId: String?,
        themePreference: String?,
        sidebarCollapsed: Boolean?,
    ): Mono<UserPreference> =
        Mono.fromCallable {
            val user = userRepository.findById(userId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
            }
            val preference = preferenceRepository.findByUserId(userId)
                ?: UserPreference(user = user)
            
            if (lastSelectedModelId != null) preference.lastSelectedModelId = lastSelectedModelId
            if (themePreference != null) {
                require(themePreference in setOf("light", "dark", "system")) {
                    "Theme preference must be one of: light, dark, system"
                }
                preference.themePreference = themePreference
            }
            if (sidebarCollapsed != null) preference.sidebarCollapsed = sidebarCollapsed
            preference.updatedAt = Instant.now()
            
            preferenceRepository.save(preference)
        }.subscribeOn(Schedulers.boundedElastic())
}
