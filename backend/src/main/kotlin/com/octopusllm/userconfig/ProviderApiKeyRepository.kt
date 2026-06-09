package com.octopusllm.userconfig

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ProviderApiKeyRepository : JpaRepository<ProviderApiKey, UUID> {
    fun findByUserIdAndProviderId(userId: UUID, providerId: String): ProviderApiKey?
    fun findByUserId(userId: UUID): List<ProviderApiKey>
}
