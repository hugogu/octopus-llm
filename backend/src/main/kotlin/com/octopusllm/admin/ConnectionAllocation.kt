package com.octopusllm.admin

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Embeddable
data class ConnectionAllocationId(
    @Column(name = "connection_id", columnDefinition = "UUID")
    val connectionId: UUID = UUID(0, 0),

    @Column(name = "user_id", columnDefinition = "UUID")
    val userId: UUID = UUID(0, 0),
) : Serializable

@Entity
@Table(name = "connection_allocations")
class ConnectionAllocation(
    @EmbeddedId
    val id: ConnectionAllocationId,

    @Column(name = "allocated_by", nullable = false, columnDefinition = "UUID")
    val allocatedBy: UUID,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
