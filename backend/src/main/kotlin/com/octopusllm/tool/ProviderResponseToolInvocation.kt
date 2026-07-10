package com.octopusllm.tool

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Join row linking an immutable provider_response to a tool_invocation it consumed (feature 009).
 * Multiple rows per invocation capture the shared-execution fan-out when models deduplicate a call.
 */
@Entity
@Table(name = "provider_response_tool_invocations")
class ProviderResponseToolInvocation(
    @Id
    @Column(columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @Column(name = "provider_response_id", nullable = false)
    val providerResponseId: UUID,

    @Column(name = "tool_invocation_id", nullable = false)
    val toolInvocationId: UUID,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
