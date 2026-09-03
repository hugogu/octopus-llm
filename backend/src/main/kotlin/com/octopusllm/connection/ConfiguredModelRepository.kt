package com.octopusllm.connection

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ConfiguredModelRepository : JpaRepository<ConfiguredModel, UUID> {
    fun findByUserId(userId: UUID, pageable: Pageable): Page<ConfiguredModel>
    fun findByUserId(userId: UUID): List<ConfiguredModel>
    fun findByUserIdAndIsEnabled(userId: UUID, isEnabled: Boolean, pageable: Pageable): Page<ConfiguredModel>
    fun findByIdAndUserId(id: UUID, userId: UUID): ConfiguredModel?
    fun findByIdInAndUserId(ids: Collection<UUID>, userId: UUID): List<ConfiguredModel>
    fun countByConnectionId(connectionId: UUID): Long
    fun countByIsAnonymousDefaultTrue(): Long

    fun findByConnectionId(connectionId: UUID, pageable: Pageable): Page<ConfiguredModel>
    fun findByIdAndConnectionId(id: UUID, connectionId: UUID): ConfiguredModel?

    @Query(
        """
        SELECT m FROM ConfiguredModel m
        JOIN m.connection c
        WHERE c.isBuiltin = true
          AND m.isEnabled = true
          AND m.isAnonymousAllowed = true
        """,
    )
    fun findAnonymousAllowed(pageable: Pageable): Page<ConfiguredModel>

    @Query(
        """
        SELECT m FROM ConfiguredModel m
        JOIN m.connection c
        WHERE m.id IN :ids
          AND c.isBuiltin = true
          AND m.isEnabled = true
          AND m.isAnonymousAllowed = true
        """,
    )
    fun findAnonymousEligibleByIds(@Param("ids") ids: Collection<UUID>): List<ConfiguredModel>

    @Query(
        """
        SELECT m FROM ConfiguredModel m
        JOIN m.connection c
        WHERE c.isBuiltin = true
          AND (:q = '' OR LOWER(m.modelId) LIKE CONCAT('%', :q, '%')
               OR LOWER(m.displayName) LIKE CONCAT('%', :q, '%')
               OR LOWER(COALESCE(c.label, '')) LIKE CONCAT('%', :q, '%'))
          AND (:connectionId IS NULL OR c.id = :connectionId)
          AND (:protocol IS NULL OR c.protocol = :protocol)
          AND (:enabled IS NULL OR m.isEnabled = :enabled)
          AND (:anonymousAllowed IS NULL OR m.isAnonymousAllowed = :anonymousAllowed)
        """,
    )
    fun findBuiltinForAdmin(
        @Param("q") q: String?,
        @Param("connectionId") connectionId: UUID?,
        @Param("protocol") protocol: String?,
        @Param("enabled") enabled: Boolean?,
        @Param("anonymousAllowed") anonymousAllowed: Boolean?,
        pageable: Pageable,
    ): Page<ConfiguredModel>

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
