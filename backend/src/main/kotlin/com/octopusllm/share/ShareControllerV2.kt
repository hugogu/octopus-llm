package com.octopusllm.share

import com.octopusllm.api.v2.PageResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.util.UUID

@RestController
@RequestMapping("/api/v2/chat/sessions/{sessionId}/shares")
class ShareControllerV2(private val service: ShareService) {
    data class ScopeRequest(val scope: String? = null)

    @PostMapping
    fun create(
        @AuthenticationPrincipal principal: String,
        @PathVariable sessionId: UUID,
        @RequestBody(required = false) request: ScopeRequest?,
    ): Mono<ResponseEntity<ShareLinkDto>> =
        service.create(sessionId, UUID.fromString(principal), parseScope(request?.scope)).map { (share, created) ->
            ResponseEntity.status(if (created) HttpStatus.CREATED else HttpStatus.OK).body(share)
        }

    @PatchMapping("/{token}")
    fun changeScope(
        @AuthenticationPrincipal principal: String,
        @PathVariable sessionId: UUID,
        @PathVariable token: String,
        @RequestBody request: ScopeRequest,
    ): Mono<ShareLinkDto> =
        service.changeScope(sessionId, token, UUID.fromString(principal), parseScope(request.scope))

    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: String,
        @PathVariable sessionId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): Mono<PageResponse<ShareLinkDto>> =
        service.list(sessionId, UUID.fromString(principal), page, size)

    @DeleteMapping("/{token}")
    fun revoke(
        @AuthenticationPrincipal principal: String,
        @PathVariable sessionId: UUID,
        @PathVariable token: String,
    ): Mono<ResponseEntity<Void>> =
        service.revoke(sessionId, token, UUID.fromString(principal))
            .thenReturn(ResponseEntity.noContent().build())

    private fun parseScope(value: String?): ShareScope =
        try {
            ShareScope.fromWire(value ?: ShareScope.AUTHENTICATED.wire)
        } catch (_: IllegalArgumentException) {
            throw org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_share_scope")
        }
}
