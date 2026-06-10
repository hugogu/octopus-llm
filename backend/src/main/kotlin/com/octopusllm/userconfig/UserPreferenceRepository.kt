package com.octopusllm.userconfig

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserPreferenceRepository : JpaRepository<UserPreference, UUID> {
    fun findByUserId(userId: UUID): UserPreference?
}
