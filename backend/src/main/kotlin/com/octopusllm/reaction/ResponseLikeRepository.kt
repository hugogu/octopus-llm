package com.octopusllm.reaction

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface ResponseLikeCountProjection {
    fun getResponseId(): UUID
    fun getLikeCount(): Long
}

interface ResponseLikeRepository : JpaRepository<ResponseLike, UUID> {
    @Transactional
    @Modifying
    @Query(
        value = """
            INSERT INTO response_likes(response_id, user_id)
            VALUES (:responseId, :userId)
            ON CONFLICT (response_id, user_id) DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIgnore(@Param("responseId") responseId: UUID, @Param("userId") userId: UUID): Int

    @Transactional
    @Modifying
    @Query("DELETE FROM ResponseLike like WHERE like.response.id = :responseId AND like.user.id = :userId")
    fun deleteForUser(@Param("responseId") responseId: UUID, @Param("userId") userId: UUID): Int

    fun countByResponseId(responseId: UUID): Long
    fun existsByResponseIdAndUserId(responseId: UUID, userId: UUID): Boolean

    @Query(
        value = """
            SELECT response_id AS responseId, COUNT(*) AS likeCount
            FROM response_likes
            WHERE response_id IN (:responseIds)
            GROUP BY response_id
        """,
        nativeQuery = true,
    )
    fun counts(@Param("responseIds") responseIds: Collection<UUID>): List<ResponseLikeCountProjection>

    @Query(
        value = """
            SELECT response_id FROM response_likes
            WHERE user_id = :userId AND response_id IN (:responseIds)
        """,
        nativeQuery = true,
    )
    fun likedResponseIds(
        @Param("responseIds") responseIds: Collection<UUID>,
        @Param("userId") userId: UUID,
    ): List<UUID>
}
