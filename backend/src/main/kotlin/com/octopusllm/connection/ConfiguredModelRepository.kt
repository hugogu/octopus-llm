package com.octopusllm.connection

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ConfiguredModelRepository : JpaRepository<ConfiguredModel, UUID> {
    fun findByUserId(userId: UUID, pageable: Pageable): Page<ConfiguredModel>
    fun findByUserIdAndIsEnabled(userId: UUID, isEnabled: Boolean, pageable: Pageable): Page<ConfiguredModel>
    fun findByIdAndUserId(id: UUID, userId: UUID): ConfiguredModel?
    fun findByIdInAndUserId(ids: Collection<UUID>, userId: UUID): List<ConfiguredModel>
    fun countByConnectionId(connectionId: UUID): Long

    fun findByConnectionId(connectionId: UUID, pageable: Pageable): Page<ConfiguredModel>
    fun findByIdAndConnectionId(id: UUID, connectionId: UUID): ConfiguredModel?

    /** Owned models plus models on built-in connections allocated to the user (for chat selection). */
    @Query(
        """
        SELECT m FROM ConfiguredModel m
        WHERE m.user.id = :userId
           OR (m.connection.isBuiltin = true AND m.connection.id IN (
                SELECT a.id.connectionId FROM ConnectionAllocation a WHERE a.id.userId = :userId))
        """,
    )
    fun findOwnedOrAllocated(@Param("userId") userId: UUID, pageable: Pageable): Page<ConfiguredModel>

    @Query(
        """
        SELECT m FROM ConfiguredModel m
        WHERE (m.user.id = :userId
           OR (m.connection.isBuiltin = true AND m.connection.id IN (
                SELECT a.id.connectionId FROM ConnectionAllocation a WHERE a.id.userId = :userId)))
          AND m.isEnabled = :enabled
        """,
    )
    fun findOwnedOrAllocatedByEnabled(
        @Param("userId") userId: UUID,
        @Param("enabled") enabled: Boolean,
        pageable: Pageable,
    ): Page<ConfiguredModel>

    /**
     * Models the user may select for chat: owned by the user, or on a built-in connection allocated
     * to the user. Used by [ConfiguredModelService.requireSelectable].
     */
    @Query(
        """
        SELECT m FROM ConfiguredModel m
        WHERE m.id IN :ids AND (
          m.user.id = :userId
          OR (m.connection.isBuiltin = true AND m.connection.id IN (
               SELECT a.id.connectionId FROM ConnectionAllocation a WHERE a.id.userId = :userId))
        )
        """,
    )
    fun findSelectableByIds(@Param("ids") ids: Collection<UUID>, @Param("userId") userId: UUID): List<ConfiguredModel>
}
