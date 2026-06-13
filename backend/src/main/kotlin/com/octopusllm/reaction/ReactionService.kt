package com.octopusllm.reaction

import com.octopusllm.chat.ProviderResponseRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.UUID

data class LikeState(
    val responseId: UUID,
    val likeCount: Long,
    val likedByMe: Boolean,
)

@Service
class ReactionService(
    private val responseRepository: ProviderResponseRepository,
    private val likeRepository: ResponseLikeRepository,
) {
    fun likeOwned(responseId: UUID, userId: UUID): Mono<LikeState> = blocking {
        requireOwned(responseId, userId)
        likeRepository.insertIgnore(responseId, userId)
        state(responseId, userId)
    }

    fun unlikeOwned(responseId: UUID, userId: UUID): Mono<LikeState> = blocking {
        requireOwned(responseId, userId)
        likeRepository.deleteForUser(responseId, userId)
        state(responseId, userId)
    }

    fun states(responseIds: Collection<UUID>, userId: UUID): Mono<Map<UUID, LikeState>> = blocking {
        if (responseIds.isEmpty()) return@blocking emptyMap()
        val counts = likeRepository.counts(responseIds).associate { it.getResponseId() to it.getLikeCount() }
        val liked = likeRepository.likedResponseIds(responseIds, userId).toSet()
        responseIds.associateWith { id -> LikeState(id, counts[id] ?: 0, id in liked) }
    }

    fun likeShared(responseId: UUID, userId: UUID): LikeState {
        likeRepository.insertIgnore(responseId, userId)
        return state(responseId, userId)
    }

    fun unlikeShared(responseId: UUID, userId: UUID): LikeState {
        likeRepository.deleteForUser(responseId, userId)
        return state(responseId, userId)
    }

    private fun state(responseId: UUID, userId: UUID) =
        LikeState(
            responseId,
            likeRepository.countByResponseId(responseId),
            likeRepository.existsByResponseIdAndUserId(responseId, userId),
        )

    private fun requireOwned(responseId: UUID, userId: UUID) {
        if (!responseRepository.existsOwnedById(responseId, userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Response not found")
        }
    }

    private fun <T> blocking(block: () -> T): Mono<T> =
        Mono.fromCallable(block).subscribeOn(Schedulers.boundedElastic())
}
