package com.octopusllm.connection

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ConnectionRepository : JpaRepository<Connection, UUID> {
    fun findByUserId(userId: UUID, pageable: Pageable): Page<Connection>
    fun findByIdAndUserId(id: UUID, userId: UUID): Connection?
}
