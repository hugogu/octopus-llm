package com.octopusllm.admin

import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Writes append-only audit rows for administrative actions. Metadata MUST never contain key material
 * or plaintext passwords (callers pass only non-secret context such as labels or counts).
 */
@Service
class AdminAuditService(
    private val repository: AdminAuditLogRepository,
) {
    fun record(
        adminUserId: UUID,
        action: AdminAuditAction,
        targetType: AdminAuditTargetType,
        targetId: UUID,
        metadata: Map<String, Any?> = emptyMap(),
    ) {
        repository.save(
            AdminAuditLog(
                adminUserId = adminUserId,
                action = action.name,
                targetType = targetType.name,
                targetId = targetId,
                metadata = metadata,
            ),
        )
    }
}
