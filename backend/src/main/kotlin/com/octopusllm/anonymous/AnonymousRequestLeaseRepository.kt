package com.octopusllm.anonymous

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface AnonymousRequestLeaseRepository : JpaRepository<AnonymousRequestLease, AnonymousRequestLeaseId> {
    @Transactional
    @Modifying
    @Query(
        value = """
            INSERT INTO anonymous_request_leases(
                client_key_hash, slot_no, lease_id, expires_at, created_at
            ) VALUES (:clientKeyHash, :slotNo, :leaseId, :expiresAt, :now)
            ON CONFLICT (client_key_hash, slot_no)
            DO UPDATE SET lease_id = EXCLUDED.lease_id,
                          expires_at = EXCLUDED.expires_at,
                          created_at = EXCLUDED.created_at
            WHERE anonymous_request_leases.expires_at <= :now
        """,
        nativeQuery = true,
    )
    fun claim(
        @Param("clientKeyHash") clientKeyHash: String,
        @Param("slotNo") slotNo: Short,
        @Param("leaseId") leaseId: UUID,
        @Param("expiresAt") expiresAt: Instant,
        @Param("now") now: Instant,
    ): Int

    @Transactional
    @Modifying
    @Query(
        value = """
            DELETE FROM anonymous_request_leases
            WHERE client_key_hash = :clientKeyHash
              AND slot_no = :slotNo
              AND lease_id = :leaseId
        """,
        nativeQuery = true,
    )
    fun release(
        @Param("clientKeyHash") clientKeyHash: String,
        @Param("slotNo") slotNo: Short,
        @Param("leaseId") leaseId: UUID,
    ): Int
}
