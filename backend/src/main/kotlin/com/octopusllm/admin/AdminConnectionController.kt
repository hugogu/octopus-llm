package com.octopusllm.admin

import com.octopusllm.api.v2.PageResponse
import com.octopusllm.api.v2.toPageResponse
import com.octopusllm.connection.ConfiguredModel
import com.octopusllm.connection.Connection
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

data class CreateBuiltinConnectionRequest(
    @field:NotBlank val protocol: String,
    @field:NotBlank val baseUrl: String,
    @field:NotBlank val apiKey: String,
    val label: String? = null,
)

data class PatchBuiltinConnectionRequest(
    val label: String? = null,
    val baseUrl: String? = null,
)

data class RotateBuiltinKeyRequest(@field:NotBlank val apiKey: String)

data class BuiltinConnectionResponse(
    val id: UUID,
    val protocol: String,
    val label: String?,
    val baseUrl: String,
    val hasKey: Boolean,
    val modelCount: Long,
    val allocatedUserCount: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class AddBuiltinModelRequest(
    @field:NotBlank val modelId: String,
    @field:NotBlank val displayName: String,
    val capabilityOverrides: Map<String, Any?> = emptyMap(),
    val customParams: Map<String, Any?> = emptyMap(),
    val isEnabled: Boolean = true,
)

data class PatchBuiltinModelRequest(
    val displayName: String? = null,
    val isEnabled: Boolean? = null,
    val sortOrder: Int? = null,
)

data class BuiltinModelResponse(
    val id: UUID,
    val connectionId: UUID,
    val modelId: String,
    val displayName: String,
    val isEnabled: Boolean,
    val sortOrder: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@RestController
@RequestMapping("/api/v2/admin/connections")
class AdminConnectionController(private val service: AdminConnectionService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal principal: String,
        @Valid @RequestBody request: CreateBuiltinConnectionRequest,
    ): Mono<BuiltinConnectionResponse> =
        service.createBuiltin(adminId(principal), request.protocol, request.label, request.baseUrl, request.apiKey)
            .map(::connectionResponse)

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): Mono<PageResponse<BuiltinConnectionResponse>> =
        service.listBuiltin(page, size).map { result -> result.toPageResponse(::connectionResponse) }

    @PatchMapping("/{id}")
    fun patch(
        @AuthenticationPrincipal principal: String,
        @PathVariable id: UUID,
        @RequestBody request: PatchBuiltinConnectionRequest,
    ): Mono<BuiltinConnectionResponse> =
        service.patchBuiltin(adminId(principal), id, request.label, request.baseUrl).map(::connectionResponse)

    @PutMapping("/{id}/key")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun rotateKey(
        @AuthenticationPrincipal principal: String,
        @PathVariable id: UUID,
        @Valid @RequestBody request: RotateBuiltinKeyRequest,
    ): Mono<Void> = service.rotateKey(adminId(principal), id, request.apiKey).then()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @AuthenticationPrincipal principal: String,
        @PathVariable id: UUID,
    ): Mono<Void> = service.deleteBuiltin(adminId(principal), id).then()

    // --- Models --------------------------------------------------------------

    @PostMapping("/{id}/models")
    @ResponseStatus(HttpStatus.CREATED)
    fun addModel(
        @PathVariable id: UUID,
        @Valid @RequestBody request: AddBuiltinModelRequest,
    ): Mono<BuiltinModelResponse> =
        service.addModel(
            id,
            request.modelId,
            request.displayName,
            request.capabilityOverrides,
            request.customParams,
            request.isEnabled,
        ).map(::modelResponse)

    @GetMapping("/{id}/models")
    fun listModels(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): Mono<PageResponse<BuiltinModelResponse>> =
        service.listModels(id, page, size).map { result -> result.toPageResponse(::modelResponse) }

    @GetMapping("/{id}/endpoint-models")
    fun listEndpointModels(@PathVariable id: UUID): Mono<Map<String, List<String>>> =
        service.listEndpointModels(id).map { mapOf("items" to it) }

    @PatchMapping("/{id}/models/{configuredModelId}")
    fun patchModel(
        @PathVariable id: UUID,
        @PathVariable configuredModelId: UUID,
        @RequestBody request: PatchBuiltinModelRequest,
    ): Mono<BuiltinModelResponse> =
        service.patchModel(id, configuredModelId, request.displayName, request.isEnabled, request.sortOrder)
            .map(::modelResponse)

    @DeleteMapping("/{id}/models/{configuredModelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteModel(
        @PathVariable id: UUID,
        @PathVariable configuredModelId: UUID,
    ): Mono<Void> = service.deleteModel(id, configuredModelId).then()

    // --- Allocations ---------------------------------------------------------

    @PutMapping("/{id}/allocations/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun allocate(
        @AuthenticationPrincipal principal: String,
        @PathVariable id: UUID,
        @PathVariable userId: UUID,
    ): Mono<Void> = service.allocate(adminId(principal), id, userId).then()

    @DeleteMapping("/{id}/allocations/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(
        @AuthenticationPrincipal principal: String,
        @PathVariable id: UUID,
        @PathVariable userId: UUID,
    ): Mono<Void> = service.revoke(adminId(principal), id, userId).then()

    @GetMapping("/{id}/allocations")
    fun listAllocations(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): Mono<PageResponse<AdminAllocationView>> =
        service.listAllocations(id, page, size).map { result -> result.toPageResponse { it } }

    private fun adminId(principal: String): UUID = UUID.fromString(principal)

    private fun connectionResponse(connection: Connection) = BuiltinConnectionResponse(
        id = connection.id,
        protocol = connection.protocol,
        label = connection.label,
        baseUrl = connection.baseUrl,
        hasKey = true,
        modelCount = service.modelCount(connection.id),
        allocatedUserCount = service.allocatedUserCount(connection.id),
        createdAt = connection.createdAt,
        updatedAt = connection.updatedAt,
    )

    private fun modelResponse(model: ConfiguredModel) = BuiltinModelResponse(
        id = model.id,
        connectionId = model.connection.id,
        modelId = model.modelId,
        displayName = model.displayName,
        isEnabled = model.isEnabled,
        sortOrder = model.sortOrder,
        createdAt = model.createdAt,
        updatedAt = model.updatedAt,
    )
}
