package com.octopusllm.anonymous

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.util.UUID

@RestController
@RequestMapping("/api/v2/anonymous/conversations")
class AnonymousConversationSyncController(
    private val service: AnonymousConversationSyncService,
) {
    @PostMapping("/sync")
    fun sync(
        @AuthenticationPrincipal principal: String,
        @RequestBody request: AnonymousSyncRequest,
    ): Mono<AnonymousSyncResponse> = service.sync(UUID.fromString(principal), request)
}
