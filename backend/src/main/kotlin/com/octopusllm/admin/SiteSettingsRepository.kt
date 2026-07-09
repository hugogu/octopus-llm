package com.octopusllm.admin

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID  // no-op import retained for template symmetry

interface SiteSettingsRepository : JpaRepository<SiteSettings, Short>
