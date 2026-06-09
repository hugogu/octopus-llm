package com.octopusllm.userconfig

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserModelConfigRepository : JpaRepository<UserModelConfig, UUID> {
    fun findByUserId(userId: UUID): List<UserModelConfig>
    fun findByUserIdAndModelId(userId: UUID, modelId: String): UserModelConfig?
    fun findByProviderApiKeyId(keyId: UUID): List<UserModelConfig>
}
