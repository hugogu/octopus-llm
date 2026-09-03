package com.octopusllm.anonymous

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AnonymousConversationImportRepository : JpaRepository<AnonymousConversationImport, UUID> {
    fun findByUserIdAndSourceConversationId(userId: UUID, sourceConversationId: UUID): AnonymousConversationImport?
}
