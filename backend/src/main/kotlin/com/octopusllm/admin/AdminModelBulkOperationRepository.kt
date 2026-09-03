package com.octopusllm.admin

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AdminModelBulkOperationRepository : JpaRepository<AdminModelBulkOperation, UUID> {
    fun findByIdAndAdminUserId(id: UUID, adminUserId: UUID): AdminModelBulkOperation?
}
