package com.octopusllm.chat

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DialogRedactionRepository : JpaRepository<DialogRedaction, UUID> {
    fun findByTurnIdIn(turnIds: Collection<UUID>): List<DialogRedaction>
    fun existsByScopeAndTurnId(scope: String, turnId: UUID): Boolean
    fun existsByScopeAndResponseId(scope: String, responseId: UUID): Boolean
}
