package com.octopusllm.userconfig

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import com.octopusllm.llm.CapabilityMatrix
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

data class AddApiKeyRequest(
    @field:NotBlank val providerId: String,
    @field:NotBlank val apiKey: String,
    val label: String? = null,
)

data class ApiKeyResponse(val id: UUID, val providerId: String, val label: String?, val createdAt: Instant)
private fun ProviderApiKey.toResponse() = ApiKeyResponse(id, providerId, label, createdAt)

data class UpsertModelConfigRequest(
    @field:NotBlank val modelId: String,
    @field:NotNull val providerApiKeyId: UUID,
    val isEnabled: Boolean = true,
    val customParams: Map<String, Any?> = emptyMap(),
)

data class PatchModelConfigRequest(
    val providerApiKeyId: UUID? = null,
    val isEnabled: Boolean? = null,
    val customParams: Map<String, Any?>? = null,
)

data class SyncProviderModelsRequest(
    @field:NotBlank val providerId: String,
    val providerApiKeyId: UUID? = null,
)

data class CreateCustomModelRequest(
    @field:NotBlank val providerId: String,
    @field:NotBlank val modelId: String,
    val displayName: String? = null,
    @field:NotNull val providerApiKeyId: UUID,
    val isEnabled: Boolean = true,
    val customParams: Map<String, Any?> = emptyMap(),
    val capabilityMatrix: CapabilityMatrix = CapabilityMatrix(),
)

data class ModelConfigResponse(
    val id: UUID,
    val modelId: String,
    val providerApiKeyId: UUID?,
    val isEnabled: Boolean,
    val customParams: Map<String, Any?>,
    val createdAt: Instant,
    val updatedAt: Instant,
)
private fun UserModelConfig.toResponse() = ModelConfigResponse(id, model.id, providerApiKey?.id, isEnabled, customParams, createdAt, updatedAt)

@RestController
@RequestMapping("/api/v1/user")
class UserConfigController(private val service: UserConfigService) {

    private fun userId(principal: String) = UUID.fromString(principal)

    // API Keys

    @GetMapping("/api-keys")
    fun listApiKeys(@AuthenticationPrincipal principal: String): Mono<Map<String, List<ApiKeyResponse>>> =
        service.listApiKeys(userId(principal)).map { keys ->
            mapOf("apiKeys" to keys.map { it.toResponse() })
        }

    @PostMapping("/api-keys")
    @ResponseStatus(HttpStatus.CREATED)
    fun addApiKey(
        @AuthenticationPrincipal principal: String,
        @Valid @RequestBody request: AddApiKeyRequest,
    ): Mono<ApiKeyResponse> =
        service.addApiKey(userId(principal), request.providerId, request.apiKey, request.label)
            .map { it.toResponse() }

    @DeleteMapping("/api-keys/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteApiKey(
        @AuthenticationPrincipal principal: String,
        @PathVariable keyId: UUID,
    ): Mono<Void> =
        service.deleteApiKey(userId(principal), keyId).then()

    // Model Configs

    @GetMapping("/model-configs")
    fun listModelConfigs(@AuthenticationPrincipal principal: String): Mono<Map<String, List<ModelConfigResponse>>> =
        service.listModelConfigs(userId(principal)).map { configs ->
            mapOf("modelConfigs" to configs.map { it.toResponse() })
        }

    @PostMapping("/model-configs")
    @ResponseStatus(HttpStatus.CREATED)
    fun addModelConfig(
        @AuthenticationPrincipal principal: String,
        @Valid @RequestBody request: UpsertModelConfigRequest,
    ): Mono<ModelConfigResponse> =
        service.addModelConfig(
            userId(principal),
            request.modelId,
            request.providerApiKeyId,
            request.isEnabled,
            request.customParams,
        )
            .map { it.toResponse() }

    @PatchMapping("/model-configs/{configId}")
    fun patchModelConfig(
        @AuthenticationPrincipal principal: String,
        @PathVariable configId: UUID,
        @RequestBody request: PatchModelConfigRequest,
    ): Mono<ModelConfigResponse> =
        service.patchModelConfig(
            userId(principal),
            configId,
            request.providerApiKeyId,
            request.isEnabled,
            request.customParams,
        )
            .map { it.toResponse() }

    @DeleteMapping("/model-configs/{configId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteModelConfig(
        @AuthenticationPrincipal principal: String,
        @PathVariable configId: UUID,
    ): Mono<Void> =
        service.deleteModelConfig(userId(principal), configId).then()

    @PostMapping("/provider-models/sync")
    fun syncProviderModels(
        @AuthenticationPrincipal principal: String,
        @Valid @RequestBody request: SyncProviderModelsRequest,
    ): Mono<Map<String, List<com.octopusllm.model.ModelResponse>>> =
        service.syncProviderModels(userId(principal), request.providerId, request.providerApiKeyId).map { models ->
            mapOf("models" to models.map {
                com.octopusllm.model.ModelResponse(
                    id = it.id,
                    providerId = it.providerId,
                    displayName = it.displayName,
                    capabilityMatrix = it.capabilityMatrix,
                    isActive = it.isActive,
                    source = it.source,
                )
            })
        }

    @PostMapping("/custom-models")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCustomModel(
        @AuthenticationPrincipal principal: String,
        @Valid @RequestBody request: CreateCustomModelRequest,
    ): Mono<ModelConfigResponse> =
        service.createCustomModel(
            userId(principal),
            request.providerId,
            request.modelId,
            request.displayName,
            request.providerApiKeyId,
            request.isEnabled,
            request.customParams,
            request.capabilityMatrix,
        ).map { it.toResponse() }
}
