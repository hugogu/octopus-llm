package com.octopusllm.admin

import com.octopusllm.api.v2.PageResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.util.UUID

@RestController
@RequestMapping("/api/v2/admin")
class AdminModelAccessController(
    private val service: AdminModelAccessService,
) {
    @GetMapping("/models")
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) connectionId: UUID?,
        @RequestParam(required = false) protocol: String?,
        @RequestParam(required = false) enabled: Boolean?,
        @RequestParam(required = false) anonymousAllowed: Boolean?,
        @RequestParam(defaultValue = "displayName") sort: String,
        @RequestParam(defaultValue = "asc") direction: String,
    ): Mono<PageResponse<AdminModelAccessView>> = service.list(
        page, size, q, connectionId, protocol, enabled, anonymousAllowed, sort, direction,
    )

    @PostMapping("/model-bulk-operations/preview")
    @ResponseStatus(HttpStatus.CREATED)
    fun preview(
        @AuthenticationPrincipal principal: String,
        @RequestBody request: AdminModelBulkPreviewRequest,
    ): Mono<AdminModelBulkPreviewResponse> = service.preview(UUID.fromString(principal), request)

    @PostMapping("/model-bulk-operations/{operationId}/execute")
    fun execute(
        @AuthenticationPrincipal principal: String,
        @PathVariable operationId: UUID,
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?,
    ): Mono<AdminModelBulkOperationResponse> = service.execute(
        UUID.fromString(principal), operationId, idempotencyKey.orEmpty(),
    )

    @GetMapping("/model-bulk-operations/{operationId}")
    fun get(
        @AuthenticationPrincipal principal: String,
        @PathVariable operationId: UUID,
    ): Mono<AdminModelBulkOperationResponse> = service.get(UUID.fromString(principal), operationId)
}
