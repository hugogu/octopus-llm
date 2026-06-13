package com.octopusllm.share

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SessionShareRepository : JpaRepository<SessionShare, UUID> {
    @Query(
        """
        SELECT share FROM SessionShare share
        JOIN FETCH share.session session
        WHERE session.id = :sessionId AND session.user.id = :userId AND share.revokedAt IS NULL
        """,
    )
    fun findActiveOwned(@Param("sessionId") sessionId: UUID, @Param("userId") userId: UUID): SessionShare?

    @Query(
        """
        SELECT share FROM SessionShare share
        JOIN FETCH share.session session
        WHERE share.token = :token AND share.revokedAt IS NULL
        """,
    )
    fun findActiveByToken(@Param("token") token: String): SessionShare?

    @Query(
        """
        SELECT share FROM SessionShare share
        JOIN share.session session
        WHERE session.id = :sessionId AND session.user.id = :userId
        """,
    )
    fun findOwned(
        @Param("sessionId") sessionId: UUID,
        @Param("userId") userId: UUID,
        pageable: Pageable,
    ): Page<SessionShare>

    @Query(
        """
        SELECT share FROM SessionShare share
        JOIN FETCH share.session session
        WHERE session.id = :sessionId AND session.user.id = :userId AND share.token = :token
        """,
    )
    fun findOwnedByToken(
        @Param("sessionId") sessionId: UUID,
        @Param("userId") userId: UUID,
        @Param("token") token: String,
    ): SessionShare?
}
