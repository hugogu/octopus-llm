package com.octopusllm.admin

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Read access to the platform storage configuration (feature 007). The single row is seeded by
 * Flyway (V030); [get] falls back to creating a default row defensively. The admin update path
 * (backend selection, S3 connectivity validation) is added in user story US5.
 */
@Service
class StorageSettingsService(
    private val repository: StorageSettingsRepository,
) {
    @Transactional
    fun get(): StorageSettings =
        repository.findById(StorageSettings.SINGLETON_ID)
            .orElseGet { repository.save(StorageSettings()) }
}
