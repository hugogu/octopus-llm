package com.octopusllm.reaction

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface AnonymousResponseLikeRepository : JpaRepository<AnonymousResponseLike, UUID> {
    @Transactional
    @Modifying
    @Query(
        value = """
            INSERT INTO anonymous_response_likes(response_id, visitor_key_hash)
            VALUES (:responseId, :visitorKeyHash)
            ON CONFLICT (response_id, visitor_key_hash) DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIgnore(
        @Param("responseId") responseId: UUID,
        @Param("visitorKeyHash") visitorKeyHash: String,
    ): Int

    fun countByResponseId(responseId: UUID): Long
    fun existsByResponseIdAndVisitorKeyHash(responseId: UUID, visitorKeyHash: String): Boolean

    @Query(
        value = """
            SELECT response_id AS responseId, COUNT(*) AS likeCount
            FROM anonymous_response_likes
            WHERE response_id IN (:responseIds)
            GROUP BY response_id
        """,
        nativeQuery = true,
    )
    fun counts(@Param("responseIds") responseIds: Collection<UUID>): List<ResponseLikeCountProjection>
}
