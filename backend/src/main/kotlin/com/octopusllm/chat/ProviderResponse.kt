package com.octopusllm.chat

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// This entity is write-once: rows are INSERTed on stream completion,
// never updated. status is always 'complete' or 'error'.
@Entity
@Table(name = "provider_responses")
class ProviderResponse(
    @Id
    @Column(columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turn_id", nullable = false)
    val turn: ChatTurn,

    @Column(name = "model_id", nullable = false, length = 255)
    val modelId: String,

    @Column(name = "configured_model_id", nullable = false)
    val configuredModelId: UUID,

    @Column(name = "model_display_name", nullable = false, length = 255)
    val modelDisplayName: String,

    @Column(nullable = false, length = 50)
    val protocol: String,

    @Column(name = "connection_label", length = 255)
    val connectionLabel: String? = null,

    @Column(name = "connection_id")
    val connectionId: UUID? = null,

    @Column(nullable = false, length = 50)
    val status: String, // "complete" or "error"

    @Column(name = "response_text", columnDefinition = "TEXT")
    val responseText: String? = null,

    @Column(name = "reasoning_text", columnDefinition = "TEXT")
    val reasoningText: String? = null,

    @Column(name = "error_message", columnDefinition = "TEXT")
    val errorMessage: String? = null,

    @Column(name = "input_tokens")
    val inputTokens: Int? = null,

    @Column(name = "output_tokens")
    val outputTokens: Int? = null,

    @Column(name = "latency_ms", nullable = false)
    val latencyMs: Int,

    @Column(name = "input_price_per_mtok", precision = 12, scale = 4)
    val inputPricePerMtok: BigDecimal? = null,

    @Column(name = "output_price_per_mtok", precision = 12, scale = 4)
    val outputPricePerMtok: BigDecimal? = null,

    @Column(name = "price_currency", length = 3)
    val priceCurrency: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
