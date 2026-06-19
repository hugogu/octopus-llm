package com.octopusllm.migration

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface MigrationStagedMediaRepository : JpaRepository<MigrationStagedMedia, MigrationStagedMediaId> {

    fun findByIdOperationId(operationId: UUID): List<MigrationStagedMedia>

    // Derived delete needs its own transaction when called from the non-transactional import flow.
    @Transactional
    fun deleteByIdOperationId(operationId: UUID)

    /**
     * Staged objects safe to sweep: those belonging to a failed operation, or to an operation that is
     * still 'in_progress' but has not advanced since [staleBefore] (its process likely crashed).
     */
    @Query(
        """
        SELECT staged FROM MigrationStagedMedia staged
        WHERE staged.id.operationId IN (
            SELECT operation.id FROM MigrationOperation operation
            WHERE operation.status = 'failed'
               OR (operation.status = 'in_progress' AND operation.updatedAt < :staleBefore)
        )
        """,
    )
    fun findSweepable(@Param("staleBefore") staleBefore: Instant): List<MigrationStagedMedia>
}
