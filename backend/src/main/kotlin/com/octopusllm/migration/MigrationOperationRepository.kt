package com.octopusllm.migration

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MigrationOperationRepository : JpaRepository<MigrationOperation, UUID> {
    fun findByActorUserIdAndOperationTypeAndIdempotencyKeyHash(
        actorUserId: UUID,
        operationType: String,
        idempotencyKeyHash: ByteArray,
    ): MigrationOperation?
}
