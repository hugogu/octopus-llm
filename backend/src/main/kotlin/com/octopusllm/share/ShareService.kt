package com.octopusllm.share

import com.octopusllm.api.v2.PageResponse
import com.octopusllm.api.v2.boundedPageRequest
import com.octopusllm.chat.ChatSessionRepository
import com.octopusllm.chat.ChatTurnRepository
import com.octopusllm.chat.ProviderResponseRepository
import com.octopusllm.reaction.AnonymousResponseLikeRepository
import com.octopusllm.reaction.LikeState
import com.octopusllm.reaction.ReactionService
import com.octopusllm.reaction.ResponseLikeRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant
import java.util.UUID

data class ShareLinkDto(
    val token: String,
    val shareUrl: String,
    val createdAt: Instant,
    val revokedAt: Instant?,
)

data class SharedResponseDto(
    val responseId: UUID,
    val modelDisplayName: String,
    val status: String,
    val responseText: String?,
    val reasoningText: String?,
    val errorMessage: String?,
    // Named loves from registered users — an aggregate count only, never any liker identity (FR-015).
    val namedLikeCount: Long,
    // True when the (logged-in) viewer has loved this response. Always false for anonymous visitors.
    val likedByMe: Boolean,
    val anonymousLikeCount: Long,
    val likedByThisVisitor: Boolean,
)

data class SharedTurnDto(
    val sequenceNum: Int,
    val promptText: String,
    val responses: List<SharedResponseDto>,
)

data class SharedSessionDto(
    val title: String?,
    val turns: List<SharedTurnDto>,
)

data class AnonymousLikeState(
    val responseId: UUID,
    val anonymousLikeCount: Long,
    val likedByThisVisitor: Boolean,
)

@Service
class ShareService(
    private val shareRepository: SessionShareRepository,
    private val sessionRepository: ChatSessionRepository,
    private val turnRepository: ChatTurnRepository,
    private val responseRepository: ProviderResponseRepository,
    private val anonymousLikeRepository: AnonymousResponseLikeRepository,
    private val responseLikeRepository: ResponseLikeRepository,
    private val reactionService: ReactionService,
    private val tokenService: ShareTokenService,
) {
    fun create(sessionId: UUID, userId: UUID): Mono<Pair<ShareLinkDto, Boolean>> = blocking {
        shareRepository.findActiveOwned(sessionId, userId)?.let { return@blocking dto(it) to false }
        val session = sessionRepository.findById(sessionId).orElseThrow { notFound() }
        if (session.user.id != userId) throw notFound()
        try {
            dto(shareRepository.saveAndFlush(SessionShare(session = session, token = tokenService.create()))) to true
        } catch (_: DataIntegrityViolationException) {
            dto(shareRepository.findActiveOwned(sessionId, userId) ?: throw notFound()) to false
        }
    }

    fun list(sessionId: UUID, userId: UUID, page: Int, size: Int): Mono<PageResponse<ShareLinkDto>> = blocking {
        if (!sessionRepository.existsByIdAndUserId(sessionId, userId)) throw notFound()
        val result = shareRepository.findOwned(
            sessionId,
            userId,
            boundedPageRequest(page, size, Sort.Order.desc("createdAt"), Sort.Order.asc("id")),
        )
        PageResponse(result.content.map(::dto), result.number, result.size, result.totalElements, result.totalPages)
    }

    fun revoke(sessionId: UUID, token: String, userId: UUID): Mono<Unit> = blocking {
        val share = shareRepository.findOwnedByToken(sessionId, userId, token) ?: throw notFound()
        if (share.revokedAt == null) {
            share.revokedAt = Instant.now()
            shareRepository.save(share)
        }
        Unit
    }

    fun read(token: String, visitorDigest: String, viewerId: UUID?): Mono<SharedSessionDto> = blocking {
        val share = requireActive(token)
        val turns = turnRepository.findBySessionIdOrderBySequenceNum(share.session.id)
        SharedSessionDto(
            title = share.session.title,
            turns = turns.map { turn ->
                SharedTurnDto(
                    sequenceNum = turn.sequenceNum,
                    promptText = turn.promptText,
                    responses = responseRepository.findByTurnId(turn.id).map { response ->
                        SharedResponseDto(
                            responseId = response.id,
                            modelDisplayName = response.modelDisplayName,
                            status = response.status,
                            responseText = response.responseText,
                            reasoningText = response.reasoningText,
                            errorMessage = response.errorMessage,
                            namedLikeCount = responseLikeRepository.countByResponseId(response.id),
                            likedByMe = viewerId != null &&
                                responseLikeRepository.existsByResponseIdAndUserId(response.id, viewerId),
                            anonymousLikeCount = anonymousLikeRepository.countByResponseId(response.id),
                            likedByThisVisitor = anonymousLikeRepository
                                .existsByResponseIdAndVisitorKeyHash(response.id, visitorDigest),
                        )
                    },
                )
            },
        )
    }

    fun anonymousLike(token: String, responseId: UUID, visitorDigest: String): Mono<AnonymousLikeState> = blocking {
        requireResponse(token, responseId)
        anonymousLikeRepository.insertIgnore(responseId, visitorDigest)
        AnonymousLikeState(
            responseId,
            anonymousLikeRepository.countByResponseId(responseId),
            anonymousLikeRepository.existsByResponseIdAndVisitorKeyHash(responseId, visitorDigest),
        )
    }

    fun namedLike(token: String, responseId: UUID, userId: UUID, liked: Boolean): Mono<LikeState> = blocking {
        requireResponse(token, responseId)
        if (liked) reactionService.likeShared(responseId, userId)
        else reactionService.unlikeShared(responseId, userId)
    }

    private fun requireResponse(token: String, responseId: UUID) {
        val share = requireActive(token)
        val response = responseRepository.findById(responseId).orElseThrow { notFound() }
        if (response.turn.session.id != share.session.id) throw notFound()
    }

    private fun requireActive(token: String) = shareRepository.findActiveByToken(token) ?: throw notFound()
    private fun dto(share: SessionShare) = ShareLinkDto(
        share.token,
        "/share/${share.token}",
        share.createdAt,
        share.revokedAt,
    )
    private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND, "Shared session not found")
    private fun <T> blocking(block: () -> T): Mono<T> =
        Mono.fromCallable(block).subscribeOn(Schedulers.boundedElastic())
}
