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

    @Type(JsonType::class)
    @Column(columnDefinition = "jsonb")
    val attachments: List<Map<String, String>>? = null,

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "selected_model_ids", nullable = false, columnDefinition = "TEXT[]")
    val selectedModelIds: Array<String>,

    @Column(name = "client_request_id", length = 100)
    val clientRequestId: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
