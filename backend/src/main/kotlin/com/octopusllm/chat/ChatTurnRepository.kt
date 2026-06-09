package com.octopusllm.chat

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ChatTurnRepository : JpaRepository<ChatTurn, UUID> {
    fun findBySessionIdOrderBySequenceNum(sessionId: UUID): List<ChatTurn>
    fun findByClientRequestId(clientRequestId: String): ChatTurn?
    fun countBySessionId(sessionId: UUID): Long
}
