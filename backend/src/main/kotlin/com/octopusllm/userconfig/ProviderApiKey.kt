package com.octopusllm.userconfig

import com.octopusllm.auth.User
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "provider_api_keys")
class ProviderApiKey(
    @Id
    @Column(columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "provider_id", nullable = false, length = 100)
    val providerId: String,

    @Column(name = "encrypted_key", nullable = false)
    val encryptedKey: ByteArray,

    @Column(name = "key_iv", nullable = false)
    val keyIv: ByteArray,

    @Column(length = 255)
    val label: String? = null,

    // Overrides the provider's default API endpoint for this key
    @Column(name = "base_url", length = 500)
    var baseUrl: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
