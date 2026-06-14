package com.octopusllm.media

import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface MediaRepository : JpaRepository<Media, UUID> {
    fun findByIdAndOwnerUserId(id: UUID, ownerUserId: UUID): Media?

    fun findAllByIdInAndOwnerUserId(ids: Collection<UUID>, ownerUserId: UUID): List<Media>

    fun findByTurnId(turnId: UUID): List<Media>

    /** Orphaned uploads (never bound to a turn) older than the cutoff — the cleanup sweep target. */
    fun findByTurnIdIsNullAndCreatedAtBefore(cutoff: Instant): List<Media>
}
