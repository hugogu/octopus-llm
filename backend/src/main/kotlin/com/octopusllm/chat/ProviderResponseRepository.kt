package com.octopusllm.chat

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ProviderResponseRepository : JpaRepository<ProviderResponse, UUID> {
    fun findByTurnId(turnId: UUID): List<ProviderResponse>
    fun findFirstByTurnIdAndConfiguredModelIdOrderByAttemptNumberDesc(
        turnId: UUID,
        configuredModelId: UUID,
    ): ProviderResponse?
    fun findByRetryRequestId(retryRequestId: String): ProviderResponse?

    @Query(
        """
        SELECT CASE WHEN COUNT(response) > 0 THEN true ELSE false END
        FROM ProviderResponse response
        JOIN response.turn turn
        JOIN turn.session session
        WHERE response.id = :responseId AND session.user.id = :userId
        """,
    )
    fun existsOwnedById(@Param("responseId") responseId: UUID, @Param("userId") userId: UUID): Boolean
}
