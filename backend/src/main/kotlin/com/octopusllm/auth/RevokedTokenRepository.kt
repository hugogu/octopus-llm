package com.octopusllm.auth

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import java.time.Instant

interface RevokedTokenRepository : JpaRepository<RevokedToken, String> {
    fun existsByJti(jti: String): Boolean

    @Modifying
    fun deleteByExpiresAtBefore(cutoff: Instant)
}
