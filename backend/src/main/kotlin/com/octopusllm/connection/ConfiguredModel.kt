package com.octopusllm.connection

import com.octopusllm.auth.User
import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Type
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "configured_models")
class ConfiguredModel(
    @Id
    @Column(columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id", nullable = false)
    val connection: Connection,

    @Column(name = "model_id", nullable = false, length = 255)
    val modelId: String,

    @Column(name = "display_name", nullable = false, length = 255)
    var displayName: String,

    @Type(JsonType::class)
    @Column(name = "capability_overrides", columnDefinition = "jsonb", nullable = false)
    var capabilityOverrides: Map<String, Any?> = emptyMap(),

    @Type(JsonType::class)
    @Column(name = "custom_params", columnDefinition = "jsonb", nullable = false)
    var customParams: Map<String, Any?> = emptyMap(),

    @Column(name = "is_enabled", nullable = false)
    var isEnabled: Boolean = true,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
