package com.octopusllm.chat

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.Type
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "chat_turns")
class ChatTurn(
    @Id
    @Column(columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    val session: ChatSession,

    @Column(name = "sequence_num", nullable = false)
    val sequenceNum: Int,

    @Column(name = "prompt_text", nullable = false, columnDefinition = "TEXT")
    val promptText: String,

    // Feature 007: ordered media references {media_id, media_type, mime_type, size_bytes, url, order}.
    // Legacy rows may hold the old inline-base64 shape {type, data, mimeType}; both read as Any?.
    @Type(JsonType::class)
    @Column(columnDefinition = "jsonb")
    val attachments: List<Map<String, Any?>>? = null,

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "selected_model_ids", nullable = false, columnDefinition = "TEXT[]")
    val selectedModelIds: Array<String>,

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "selected_configured_model_ids", nullable = false, columnDefinition = "UUID[]")
    val selectedConfiguredModelIds: Array<UUID> = emptyArray(),

    @Column(name = "client_request_id", length = 100)
    val clientRequestId: String? = null,

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "client_ip", columnDefinition = "inet")
    val clientIp: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
