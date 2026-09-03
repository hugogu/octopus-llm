package com.octopusllm.anonymous

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

data class AnonymousRequestLeaseId(
    val clientKeyHash: String = "",
    val slotNo: Short = 0,
) : Serializable

@Entity
@Table(name = "anonymous_request_leases")
@IdClass(AnonymousRequestLeaseId::class)
class AnonymousRequestLease(
    @Id
    @Column(name = "client_key_hash", nullable = false, length = 64)
    val clientKeyHash: String,

    @Id
    @Column(name = "slot_no", nullable = false)
    val slotNo: Short,

    @Column(name = "lease_id", nullable = false)
    val leaseId: UUID,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
