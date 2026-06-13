package com.octopusllm.admin

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface ConnectionAllocationRepository : JpaRepository<ConnectionAllocation, ConnectionAllocationId> {
    fun findByIdConnectionId(connectionId: UUID, pageable: Pageable): Page<ConnectionAllocation>
    fun countByIdConnectionId(connectionId: UUID): Long
    fun existsByIdConnectionIdAndIdUserId(connectionId: UUID, userId: UUID): Boolean

    @Transactional
    @Modifying
    @Query("DELETE FROM ConnectionAllocation a WHERE a.id.connectionId = :connectionId AND a.id.userId = :userId")
    fun deleteAllocation(@Param("connectionId") connectionId: UUID, @Param("userId") userId: UUID)
}
