package com.octopusllm.share

import com.octopusllm.reaction.LikeState
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.util.UUID

@RestController
@RequestMapping("/api/v2/shared/{token}")
class SharedSessionController(
    private val service: ShareService,
    private val visitorService: AnonymousVisitorService,
) {
    @GetMapping
    fun read(
        @AuthenticationPrincipal principal: String?,
        @PathVariable token: String,
        @CookieValue(name = AnonymousVisitorService.COOKIE_NAME, required = false) visitorCookie: String?,
    ): Mono<ResponseEntity<SharedSessionDto>> {
        val visitor = visitorService.resolve(token, visitorCookie)
        return service.read(token, visitor.digest, principal?.let(UUID::fromString)).map { body ->
            ResponseEntity.ok().apply {
                visitor.cookie?.let { header("Set-Cookie", it.toString()) }
            }.body(body)
        }
    }

    @PostMapping("/responses/{responseId}/like")
    fun anonymousLike(
        @PathVariable token: String,
        @PathVariable responseId: UUID,
        @CookieValue(name = AnonymousVisitorService.COOKIE_NAME, required = false) visitorCookie: String?,
    ): Mono<ResponseEntity<AnonymousLikeState>> {
        val visitor = visitorService.resolve(token, visitorCookie)
        return service.anonymousLike(token, responseId, visitor.digest).map { body ->
            ResponseEntity.ok().apply {
                visitor.cookie?.let { header("Set-Cookie", it.toString()) }
            }.body(body)
        }
    }

    @PutMapping("/responses/{responseId}/like")
    fun namedLike(
        @AuthenticationPrincipal principal: String?,
        @PathVariable token: String,
        @PathVariable responseId: UUID,
    ): Mono<LikeState> =
        service.namedLike(token, responseId, requireUser(principal), true)

    @DeleteMapping("/responses/{responseId}/like")
    fun namedUnlike(
        @AuthenticationPrincipal principal: String?,
        @PathVariable token: String,
        @PathVariable responseId: UUID,
    ): Mono<LikeState> =
        service.namedLike(token, responseId, requireUser(principal), false)

    private fun requireUser(principal: String?): UUID =
        principal?.let(UUID::fromString)
            ?: throw org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "Authentication required",
            )
}
