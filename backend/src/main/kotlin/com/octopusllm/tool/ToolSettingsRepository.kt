package com.octopusllm.tool

import org.springframework.data.jpa.repository.JpaRepository

interface ToolSettingsRepository : JpaRepository<ToolSettings, Short>
