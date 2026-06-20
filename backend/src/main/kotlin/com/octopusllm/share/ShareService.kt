package com.octopusllm.share

import com.octopusllm.api.v2.PageResponse
import com.octopusllm.api.v2.boundedPageRequest
import com.octopusllm.chat.ChatSessionRepository
import com.octopusllm.chat.ChatTurnRepository
import com.octopusllm.chat.ProviderResponseRepository
import com.octopusllm.chat.latestProviderResponses
import com.octopusllm.media.MediaRepository
import com.octopusllm.media.MediaStorageFactory
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
    val scope: String,
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
    // Usage figures for the details popover — no identity/IP/connection (FR-018, within the share
    // view's privacy boundary).
    val inputTokens: Int?,
    val outputTokens: Int?,
    val cacheReadTokens: Int?,
    val cacheWriteTokens: Int?,
    val latencyMs: Int,
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
    // Ordered media references (feature 007); no owner identity. Inaccessible once the share is revoked.
    val attachments: List<Map<String, Any?>>,
    val responses: List<SharedResponseDto>,
)

data class SharedSessionDto(
    val title: String?,
    val turns: List<SharedTurnDto>,
    val scope: String,
    val canImport: Boolean = true,
)

data class AnonymousLikeState(
    val responseId: UUID,
    val anonymousLikeCount: Long,
    val likedByThisVisitor: Boolean,
)

data class SharedQuestImportSource(
    val title: String?,
    val turns: List<SharedQuestImportTurn>,
    val media: List<SharedQuestImportMedia>,
)

data class SharedQuestImportTurn(
    val sourceTurnId: UUID,
    val sequenceNum: Int,
    val promptText: String,
    val attachments: List<Map<String, Any?>>,
    val selectedModelIds: List<String>,
    val selectedConfiguredModelIds: List<UUID>,
    val createdAt: Instant,
    val responses: List<com.octopusllm.chat.ProviderResponse>,
)

data class SharedQuestImportMedia(
    val sourceMediaId: UUID,
    val mediaType: String,
    val mimeType: String,
    val sizeBytes: Long,
    val originalFilename: String?,
    val content: ByteArray,
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
    private val dialogRedactionService: com.octopusllm.chat.DialogRedactionService,
    private val mediaRepository: MediaRepository,
    private val mediaStorageFactory: MediaStorageFactory,
) {
    fun create(sessionId: UUID, userId: UUID, scope: ShareScope): Mono<Pair<ShareLinkDto, Boolean>> = blocking {
        shareRepository.findActiveOwned(sessionId, userId)?.let {
            if (it.scope != scope) {
                it.scope = scope
                shareRepository.save(it)
            }
            return@blocking dto(it) to false
        }
        val session = sessionRepository.findById(sessionId).orElseThrow { notFound() }
        if (session.user.id != userId) throw notFound()
        try {
            dto(
                shareRepository.saveAndFlush(
                    SessionShare(session = session, token = tokenService.create(), scope = scope),
                ),
            ) to true
        } catch (_: DataIntegrityViolationException) {
            val existing = shareRepository.findActiveOwned(sessionId, userId) ?: throw notFound()
            existing.scope = scope
            dto(shareRepository.save(existing)) to false
        }
    }

    fun changeScope(sessionId: UUID, token: String, userId: UUID, scope: ShareScope): Mono<ShareLinkDto> = blocking {
        val share = shareRepository.findOwnedByToken(sessionId, userId, token)
            ?.takeIf { it.revokedAt == null } ?: throw notFound()
        if (share.scope != scope) {
            share.scope = scope
            shareRepository.save(share)
        }
        dto(share)
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
        val share = requireAccessible(token, viewerId)
        val turns = turnRepository.findBySessionIdOrderBySequenceNum(share.session.id)
        // Feature 008: redacted Dialogs are hidden from shared views too (same filter as owned reads).
        val redactions = dialogRedactionService.forTurns(turns.map { it.id })
        SharedSessionDto(
            title = share.session.title,
            turns = turns.filterNot { redactions.isTurnRedacted(it.id) }.map { turn ->
                SharedTurnDto(
                    sequenceNum = turn.sequenceNum,
                    promptText = turn.promptText,
                    attachments = turn.attachments.orEmpty()
                        .sortedBy { (it["order"] as? Number)?.toInt() ?: 0 },
                    responses = latestProviderResponses(
                        turn,
                        responseRepository.findByTurnId(turn.id),
                    ).filterNot { redactions.isResponseRedacted(it.id) }.map { response ->
                        SharedResponseDto(
                            responseId = response.id,
                            modelDisplayName = response.modelDisplayName,
                            status = response.status,
                            responseText = response.responseText,
                            reasoningText = response.reasoningText,
                            errorMessage = response.errorMessage,
                            inputTokens = response.inputTokens,
                            outputTokens = response.outputTokens,
                            cacheReadTokens = response.cacheReadTokens,
                            cacheWriteTokens = response.cacheWriteTokens,
                            latencyMs = response.latencyMs,
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
            scope = share.scope.wire,
        )
    }

    fun anonymousLike(
        token: String,
        responseId: UUID,
        visitorDigest: String,
        viewerId: UUID?,
    ): Mono<AnonymousLikeState> = blocking {
        requireResponse(token, responseId, viewerId)
        anonymousLikeRepository.insertIgnore(responseId, visitorDigest)
        AnonymousLikeState(
            responseId,
            anonymousLikeRepository.countByResponseId(responseId),
            anonymousLikeRepository.existsByResponseIdAndVisitorKeyHash(responseId, visitorDigest),
        )
    }

    fun namedLike(token: String, responseId: UUID, userId: UUID, liked: Boolean): Mono<LikeState> = blocking {
        requireResponse(token, responseId, userId)
        if (liked) reactionService.likeShared(responseId, userId)
        else reactionService.unlikeShared(responseId, userId)
    }

    /**
     * Resolves the exact visible immutable snapshot that an authenticated viewer may import. Active
     * share lookup happens before any Quest content is read; scope-specific anonymous rejection is
     * centralized here with the other token operations in the share-scope phase.
     */
    fun importSource(token: String, viewerId: UUID): SharedQuestImportSource {
        val share = requireAccessible(token, viewerId)
        val turns = turnRepository.findBySessionIdOrderBySequenceNum(share.session.id)
        val redactions = dialogRedactionService.forTurns(turns.map { it.id })
        val visibleTurns = turns.filterNot { redactions.isTurnRedacted(it.id) }.map { turn ->
            SharedQuestImportTurn(
                sourceTurnId = turn.id,
                sequenceNum = turn.sequenceNum,
                promptText = turn.promptText,
                attachments = turn.attachments.orEmpty(),
                selectedModelIds = turn.selectedModelIds.toList(),
                selectedConfiguredModelIds = turn.selectedConfiguredModelIds.toList(),
                createdAt = turn.createdAt,
                responses = latestProviderResponses(turn, responseRepository.findByTurnId(turn.id))
                    .filterNot { redactions.isResponseRedacted(it.id) },
            )
        }
        val mediaIds = visibleTurns.flatMap { turn ->
            turn.attachments.mapNotNull { attachment ->
                (attachment["media_id"] as? String)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            }
        }.toSet()
        val media = mediaRepository.findAllById(mediaIds).map { item ->
            val bytes = mediaStorageFactory.resolveByBackend(item.storageBackend)?.read(item.storageKey)
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Shared Quest media is unavailable")
            SharedQuestImportMedia(
                sourceMediaId = item.id,
                mediaType = item.mediaType,
                mimeType = item.mimeType,
                sizeBytes = item.sizeBytes,
                originalFilename = item.originalFilename,
                content = bytes,
            )
        }
        if (media.size != mediaIds.size) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Shared Quest media is unavailable")
        }
        return SharedQuestImportSource(share.session.title, visibleTurns, media)
    }

    fun requireAccessible(token: String, viewerId: UUID?): SessionShare {
        val share = requireActive(token)
        if (share.scope == ShareScope.AUTHENTICATED && viewerId == null) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "auth_required")
        }
        return share
    }

    private fun requireResponse(token: String, responseId: UUID, viewerId: UUID?) {
        val share = requireAccessible(token, viewerId)
        val response = responseRepository.findById(responseId).orElseThrow { notFound() }
        if (response.turn.session.id != share.session.id) throw notFound()
        val redactions = dialogRedactionService.forTurns(listOf(response.turn.id))
        if (redactions.isTurnRedacted(response.turn.id) || redactions.isResponseRedacted(response.id)) throw notFound()
    }

    private fun requireActive(token: String) = shareRepository.findActiveByToken(token) ?: throw notFound()
    private fun dto(share: SessionShare) = ShareLinkDto(
        share.token,
        "/share/${share.token}",
        share.scope.wire,
        share.createdAt,
        share.revokedAt,
    )
    private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND, "Shared session not found")
    private fun <T> blocking(block: () -> T): Mono<T> =
        Mono.fromCallable(block).subscribeOn(Schedulers.boundedElastic())
}
