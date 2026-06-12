package com.octopusllm.connection

import com.octopusllm.auth.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "connections")
class Connection(
    @Id
    @Column(columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false, length = 50)
    val protocol: String,

    @Column(length = 255)
    var label: String? = null,

    @Column(name = "base_url", nullable = false, length = 500)
    var baseUrl: String,

    @Column(name = "encrypted_key", nullable = false)
    var encryptedKey: ByteArray,

    @Column(name = "key_iv", nullable = false)
    var keyIv: ByteArray,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
