package com.octopusllm.reaction

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.util.UUID

@RestController
@RequestMapping("/api/v2/responses")
class ReactionControllerV2(private val service: ReactionService) {
    @PutMapping("/{responseId}/like")
    fun like(
        @AuthenticationPrincipal principal: String,
        @PathVariable responseId: UUID,
    ): Mono<LikeState> = service.likeOwned(responseId, UUID.fromString(principal))

    @DeleteMapping("/{responseId}/like")
    fun unlike(
        @AuthenticationPrincipal principal: String,
        @PathVariable responseId: UUID,
    ): Mono<LikeState> = service.unlikeOwned(responseId, UUID.fromString(principal))
}
