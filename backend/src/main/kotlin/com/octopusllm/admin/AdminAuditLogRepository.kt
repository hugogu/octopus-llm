package com.octopusllm.admin

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AdminAuditLogRepository : JpaRepository<AdminAuditLog, UUID>
