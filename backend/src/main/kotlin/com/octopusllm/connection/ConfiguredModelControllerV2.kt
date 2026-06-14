package com.octopusllm.connection

import com.octopusllm.api.v2.PageResponse
import com.octopusllm.api.v2.toPageResponse
import com.octopusllm.llm.CapabilityMatrix
import com.octopusllm.model.ProtocolDefinitions
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.time.Instant
import java.math.BigDecimal
import java.util.UUID

data class AddConfiguredModelRequestV2(
    val connectionId: UUID,
    @field:NotBlank val modelId: String,
    @field:NotBlank val displayName: String,
    val capabilityOverrides: Map<String, Any?> = emptyMap(),
    val customParams: Map<String, Any?> = emptyMap(),
    val isEnabled: Boolean = true,
    val inputPricePerMtok: BigDecimal? = null,
    val outputPricePerMtok: BigDecimal? = null,
    val priceCurrency: String? = null,
)

data class PatchConfiguredModelRequestV2(
    val displayName: String? = null,
    val isEnabled: Boolean? = null,
    val capabilityOverrides: Map<String, Any?>? = null,
    val customParams: Map<String, Any?>? = null,
    val sortOrder: Int? = null,
    val inputPricePerMtok: BigDecimal? = null,
    val outputPricePerMtok: BigDecimal? = null,
    val priceCurrency: String? = null,
)

data class ConfiguredModelResponseV2(
    val id: UUID,
    val connectionId: UUID,
    val connectionLabel: String?,
    val protocol: String,
    val baseUrl: String,
    val modelId: String,
    val displayName: String,
    val builtin: Boolean,
    val capabilityOverrides: Map<String, Any?>,
    val capabilityMatrix: CapabilityMatrix,
    val customParams: Map<String, Any?>,
    val isEnabled: Boolean,
    val sortOrder: Int,
    val inputPricePerMtok: BigDecimal?,
    val outputPricePerMtok: BigDecimal?,
    val priceCurrency: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@RestController
@RequestMapping("/api/v2/configured-models")
class ConfiguredModelControllerV2(private val service: ConfiguredModelService) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: String,
        @RequestParam(required = false) enabled: Boolean?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): Mono<PageResponse<ConfiguredModelResponseV2>> =
        service.list(userId(principal), enabled, page, size)
            .map { result -> result.toPageResponse(::response) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun add(
        @AuthenticationPrincipal principal: String,
        @Valid @RequestBody request: AddConfiguredModelRequestV2,
    ): Mono<ConfiguredModelResponseV2> =
        service.add(
            userId(principal),
            request.connectionId,
            request.modelId,
            request.displayName,
            request.capabilityOverrides,
            request.customParams,
            request.isEnabled,
            request.inputPricePerMtok,
            request.outputPricePerMtok,
            request.priceCurrency,
        ).map(::response)

    @PatchMapping("/{id}")
    fun patch(
        @AuthenticationPrincipal principal: String,
        @PathVariable id: UUID,
        @RequestBody request: PatchConfiguredModelRequestV2,
    ): Mono<ConfiguredModelResponseV2> =
        service.patch(
            userId(principal),
            id,
            request.displayName,
            request.isEnabled,
            request.capabilityOverrides,
            request.customParams,
            request.sortOrder,
            request.inputPricePerMtok,
            request.outputPricePerMtok,
            request.priceCurrency,
        ).map(::response)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @AuthenticationPrincipal principal: String,
        @PathVariable id: UUID,
    ): Mono<Void> = service.delete(userId(principal), id).then()

    private fun response(model: ConfiguredModel): ConfiguredModelResponseV2 {
        val protocol = ProtocolDefinitions.require(model.connection.protocol)
        return ConfiguredModelResponseV2(
            id = model.id,
            connectionId = model.connection.id,
            connectionLabel = model.connection.label,
            protocol = protocol.id,
            baseUrl = model.connection.baseUrl,
            modelId = model.modelId,
            displayName = model.displayName,
            builtin = model.connection.isBuiltin,
            capabilityOverrides = model.capabilityOverrides,
            capabilityMatrix = ProtocolDefinitions.mergeCapabilities(protocol.baseline, model.capabilityOverrides),
            customParams = model.customParams,
            isEnabled = model.isEnabled,
            sortOrder = model.sortOrder,
            inputPricePerMtok = model.inputPricePerMtok,
            outputPricePerMtok = model.outputPricePerMtok,
            priceCurrency = model.priceCurrency,
            createdAt = model.createdAt,
            updatedAt = model.updatedAt,
        )
    }

    private fun userId(principal: String) = UUID.fromString(principal)
}
