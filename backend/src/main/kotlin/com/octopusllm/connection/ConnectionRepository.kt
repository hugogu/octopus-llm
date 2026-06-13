package com.octopusllm.connection

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ConnectionRepository : JpaRepository<Connection, UUID> {
    fun findByUserId(userId: UUID, pageable: Pageable): Page<Connection>
    fun findByIdAndUserId(id: UUID, userId: UUID): Connection?

    fun findByIsBuiltinTrue(pageable: Pageable): Page<Connection>
    fun findByIdAndIsBuiltinTrue(id: UUID): Connection?

    /** Built-in connections allocated to the given user (read-only access for chat). */
    @Query(
        """
        SELECT c FROM Connection c
        WHERE c.isBuiltin = true
          AND c.id IN (
            SELECT a.id.connectionId FROM ConnectionAllocation a WHERE a.id.userId = :userId
          )
        ORDER BY c.createdAt ASC, c.id ASC
        """,
    )
    fun findBuiltinAllocatedToUser(@Param("userId") userId: UUID): List<Connection>
}
