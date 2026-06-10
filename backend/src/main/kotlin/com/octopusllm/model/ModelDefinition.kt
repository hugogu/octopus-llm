package com.octopusllm.model

import com.octopusllm.llm.CapabilityMatrix
import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.Instant

enum class ModelSource {
    CATALOGUE,
    DISCOVERED,
    CUSTOM,
}

@Entity
@Table(name = "model_definitions")
class ModelDefinition(
    @Id
    @Column(length = 100)
    val id: String,

    @Column(name = "provider_id", nullable = false, length = 100)
    val providerId: String,

    @Column(name = "display_name", nullable = false, length = 255)
    val displayName: String,

    @Type(JsonType::class)
    @Column(name = "capability_matrix", columnDefinition = "jsonb", nullable = false)
    val capabilityMatrix: CapabilityMatrix,

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    val source: ModelSource = ModelSource.CATALOGUE,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
)
