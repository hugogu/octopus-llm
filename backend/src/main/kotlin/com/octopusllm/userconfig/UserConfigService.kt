package com.octopusllm.userconfig

import com.octopusllm.auth.UserRepository
import com.octopusllm.model.ModelDefinitionRepository
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
) {

    fun listApiKeys(userId: UUID): Mono<List<ProviderApiKey>> =
        Mono.fromCallable { apiKeyRepository.findByUserId(userId) }
            .subscribeOn(Schedulers.boundedElastic())

    fun addApiKey(userId: UUID, providerId: String, rawKey: String, label: String?): Mono<ProviderApiKey> =
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
            )
            apiKeyRepository.save(key)
        }.subscribeOn(Schedulers.boundedElastic())

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

    fun addModelConfig(userId: UUID, modelId: String, apiKeyId: UUID, isEnabled: Boolean = true): Mono<UserModelConfig> =
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
                existing.updatedAt = Instant.now()
                modelConfigRepository.save(existing)
            } else {
                val config = UserModelConfig(user = user, model = model, providerApiKey = apiKey, isEnabled = isEnabled)
                modelConfigRepository.save(config)
            }
        }.subscribeOn(Schedulers.boundedElastic())

    fun patchModelConfig(userId: UUID, configId: UUID, isEnabled: Boolean): Mono<UserModelConfig> =
        Mono.fromCallable {
            val config = modelConfigRepository.findById(configId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Config not found")
            }
            if (config.user.id != userId) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
            if (config.providerApiKey == null) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "API key deleted; re-add a key first")
            config.isEnabled = isEnabled
            config.updatedAt = Instant.now()
            modelConfigRepository.save(config)
        }.subscribeOn(Schedulers.boundedElastic())

    fun deleteModelConfig(userId: UUID, configId: UUID): Mono<Unit> =
        Mono.fromCallable {
            val config = modelConfigRepository.findById(configId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Config not found")
            }
            if (config.user.id != userId) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
            modelConfigRepository.delete(config)
        }.subscribeOn(Schedulers.boundedElastic()).thenReturn(Unit)
}
