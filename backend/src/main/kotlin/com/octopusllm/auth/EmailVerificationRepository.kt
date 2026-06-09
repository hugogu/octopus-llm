package com.octopusllm.auth

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface EmailVerificationRepository : JpaRepository<EmailVerification, UUID> {
    fun findByToken(token: String): EmailVerification?

    @Modifying
    @Query("DELETE FROM EmailVerification ev WHERE ev.user.id = :userId AND ev.usedAt IS NULL")
    fun deleteByUserIdAndUsedAtIsNull(userId: UUID)
}
