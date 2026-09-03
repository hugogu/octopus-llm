package com.octopusllm.admin

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AdminModelBulkOperationItemRepository : JpaRepository<AdminModelBulkOperationItem, AdminModelBulkOperationItemId> {
    fun findByOperationIdOrderByDisplayNameSnapshotAsc(operationId: UUID): List<AdminModelBulkOperationItem>
}
