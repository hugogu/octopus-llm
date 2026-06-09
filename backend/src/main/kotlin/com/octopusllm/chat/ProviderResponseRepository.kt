package com.octopusllm.chat

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ProviderResponseRepository : JpaRepository<ProviderResponse, UUID> {
    fun findByTurnId(turnId: UUID): List<ProviderResponse>
}
