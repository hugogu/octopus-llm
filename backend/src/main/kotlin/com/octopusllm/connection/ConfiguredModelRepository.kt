package com.octopusllm.connection

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ConfiguredModelRepository : JpaRepository<ConfiguredModel, UUID> {
    fun findByUserId(userId: UUID, pageable: Pageable): Page<ConfiguredModel>
    fun findByUserIdAndIsEnabled(userId: UUID, isEnabled: Boolean, pageable: Pageable): Page<ConfiguredModel>
    fun findByIdAndUserId(id: UUID, userId: UUID): ConfiguredModel?
    fun findByIdInAndUserId(ids: Collection<UUID>, userId: UUID): List<ConfiguredModel>
    fun countByConnectionId(connectionId: UUID): Long
}
