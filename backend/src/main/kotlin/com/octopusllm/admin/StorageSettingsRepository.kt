package com.octopusllm.admin

import org.springframework.data.jpa.repository.JpaRepository

interface StorageSettingsRepository : JpaRepository<StorageSettings, Short>
