package com.octopusllm.tool

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Persists tool executions and their per-response lineage (feature 009, FR-010). Recording is
 * deduplicated per turn: the first model to run a tool+args combination inserts the row; later models
 * reuse it, so shared executions appear once. Each consuming provider_response is then linked via the
 * join table, letting analytics and share/replay reconstruct exactly what each model saw.
 */
@Service
class ToolInvocationService(
    private val invocations: ToolInvocationRepository,
    private val joins: ProviderResponseToolInvocationRepository,
) {
    /**
     * Returns the single [ToolInvocation] for this tool+arguments within the turn, inserting it on first
     * sight and reusing it thereafter. Safe under the concurrent fan-out: a lost dedup race surfaces as a
     * unique-constraint violation, which we resolve by re-reading the winning row.
     */
    fun record(
        questId: UUID,
        turnId: UUID,
        toolName: String,
        arguments: Map<String, Any?>,
        result: ToolResult,
    ): ToolInvocation {
        val hash = ToolArguments.hash(arguments)
        invocations.findByQuestIdAndTurnIdAndToolNameAndArgumentsHash(questId, turnId, toolName, hash)
            ?.let { return it }

        val now = Instant.now()
        val entity = ToolInvocation(
            questId = questId,
            turnId = turnId,
            toolName = toolName,
            argumentsHash = hash,
            arguments = arguments,
            result = (result as? ToolResult.Success)?.data,
            errorMessage = (result as? ToolResult.Failure)?.errorMessage,
            status = result.status.value,
            completedAt = now,
            updatedAt = now,
        )
        return try {
            invocations.saveAndFlush(entity)
        } catch (race: DataIntegrityViolationException) {
            invocations.findByQuestIdAndTurnIdAndToolNameAndArgumentsHash(questId, turnId, toolName, hash)
                ?: throw race
        }
    }

    /** Links a provider_response to an executed tool invocation; idempotent under the unique constraint. */
    fun link(providerResponseId: UUID, toolInvocationId: UUID) {
        if (joins.findByProviderResponseId(providerResponseId).any { it.toolInvocationId == toolInvocationId }) {
            return
        }
        try {
            joins.saveAndFlush(
                ProviderResponseToolInvocation(
                    providerResponseId = providerResponseId,
                    toolInvocationId = toolInvocationId,
                ),
            )
        } catch (_: DataIntegrityViolationException) {
            // Already linked by a concurrent writer — nothing to do.
        }
    }
}
