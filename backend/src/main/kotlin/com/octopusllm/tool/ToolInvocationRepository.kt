package com.octopusllm.tool

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ToolInvocationRepository : JpaRepository<ToolInvocation, UUID> {
    fun findByQuestIdAndTurnId(questId: UUID, turnId: UUID): List<ToolInvocation>

    /** Deduplication lookup: the single execution for this tool + argument set within a turn. */
    fun findByQuestIdAndTurnIdAndToolNameAndArgumentsHash(
        questId: UUID,
        turnId: UUID,
        toolName: String,
        argumentsHash: String,
    ): ToolInvocation?
}

interface ProviderResponseToolInvocationRepository : JpaRepository<ProviderResponseToolInvocation, UUID> {
    fun findByProviderResponseId(providerResponseId: UUID): List<ProviderResponseToolInvocation>

    fun findByToolInvocationId(toolInvocationId: UUID): List<ProviderResponseToolInvocation>

    fun findByProviderResponseIdIn(providerResponseIds: Collection<UUID>): List<ProviderResponseToolInvocation>
}
