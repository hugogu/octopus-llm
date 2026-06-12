package com.octopusllm.connection

import com.octopusllm.api.v2.PageResponse
import com.octopusllm.api.v2.toPageResponse
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

data class AddConnectionRequestV2(
    @field:NotBlank val protocol: String,
    @field:NotBlank val baseUrl: String,
    @field:NotBlank val apiKey: String,
    val label: String? = null,
)

data class PatchConnectionRequestV2(
    val label: String? = null,
    val baseUrl: String? = null,
)

data class RotateConnectionKeyRequest(
    @field:NotBlank val apiKey: String,
)

data class ConnectionResponseV2(
    val id: UUID,
    val protocol: String,
    val label: String?,
    val baseUrl: String,
    val hasKey: Boolean,
    val modelCount: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@RestController
@RequestMapping("/api/v2/connections")
class ConnectionControllerV2(private val service: ConnectionService) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): Mono<PageResponse<ConnectionResponseV2>> =
        service.list(userId(principal), page, size)
            .map { result -> result.toPageResponse(::response) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun add(
        @AuthenticationPrincipal principal: String,
        @Valid @RequestBody request: AddConnectionRequestV2,
    ): Mono<ConnectionResponseV2> =
        service.add(
            userId(principal),
            request.protocol,
            request.label,
            request.baseUrl,
            request.apiKey,
        ).map(::response)

    @PatchMapping("/{id}")
    fun patch(
        @AuthenticationPrincipal principal: String,
        @PathVariable id: UUID,
        @RequestBody request: PatchConnectionRequestV2,
    ): Mono<ConnectionResponseV2> =
        service.patch(userId(principal), id, request.label, request.baseUrl).map(::response)

    @PutMapping("/{id}/key")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun rotateKey(
        @AuthenticationPrincipal principal: String,
        @PathVariable id: UUID,
        @Valid @RequestBody request: RotateConnectionKeyRequest,
    ): Mono<Void> = service.rotateKey(userId(principal), id, request.apiKey).then()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @AuthenticationPrincipal principal: String,
        @PathVariable id: UUID,
    ): Mono<Void> = service.delete(userId(principal), id).then()

    private fun response(connection: Connection) = ConnectionResponseV2(
        id = connection.id,
        protocol = connection.protocol,
        label = connection.label,
        baseUrl = connection.baseUrl,
        hasKey = true,
        modelCount = service.modelCount(connection.id),
        createdAt = connection.createdAt,
        updatedAt = connection.updatedAt,
    )

    private fun userId(principal: String) = UUID.fromString(principal)
}
