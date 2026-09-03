package com.octopusllm.admin

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

data class AdminModelBulkOperationItemId(
    val operationId: UUID = UUID.randomUUID(),
    val configuredModelId: UUID = UUID.randomUUID(),
) : Serializable

@Entity
@Table(name = "admin_model_bulk_operation_items")
@IdClass(AdminModelBulkOperationItemId::class)
class AdminModelBulkOperationItem(
    @Id
    @Column(name = "operation_id", nullable = false)
    val operationId: UUID,

    @Id
    @Column(name = "configured_model_id", nullable = false)
    val configuredModelId: UUID,

    @Column(name = "model_id_snapshot", nullable = false, length = 255)
    val modelIdSnapshot: String,

    @Column(name = "display_name_snapshot", nullable = false, length = 255)
    val displayNameSnapshot: String,

    @Column(name = "connection_label_snapshot", length = 255)
    val connectionLabelSnapshot: String? = null,

    @Column(name = "previous_is_enabled")
    val previousIsEnabled: Boolean? = null,

    @Column(name = "previous_is_anonymous_allowed")
    val previousIsAnonymousAllowed: Boolean? = null,

    @Column(name = "outcome", nullable = false, length = 24)
    var outcome: String = "PENDING",

    @Column(name = "error_code", length = 100)
    var errorCode: String? = null,

    @Column(name = "error_message", length = 1000)
    var errorMessage: String? = null,

    @Column(name = "processed_at")
    var processedAt: Instant? = null,
)
