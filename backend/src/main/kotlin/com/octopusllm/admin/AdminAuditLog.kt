package com.octopusllm.admin

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Type
import java.time.Instant
import java.util.UUID

enum class AdminAuditAction {
    ACTIVATE,
    DISABLE,
    ENABLE,
    RESET_PASSWORD,
    BUILTIN_CONNECTION_CREATE,
    BUILTIN_CONNECTION_UPDATE,
    BUILTIN_CONNECTION_DELETE,
    ALLOCATE,
    REVOKE,
}

enum class AdminAuditTargetType {
    USER,
    CONNECTION,
}

@Entity
@Table(name = "admin_audit_log")
class AdminAuditLog(
    @Id
    @Column(columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @Column(name = "admin_user_id", nullable = false, columnDefinition = "UUID")
    val adminUserId: UUID,

    @Column(nullable = false, length = 50)
    val action: String,

    @Column(name = "target_type", nullable = false, length = 20)
    val targetType: String,

    @Column(name = "target_id", nullable = false, columnDefinition = "UUID")
    val targetId: UUID,

    @Type(JsonType::class)
    @Column(columnDefinition = "jsonb", nullable = false)
    val metadata: Map<String, Any?> = emptyMap(),

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
