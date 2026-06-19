package com.octopusllm.chat

import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Single source of truth for which Dialogs are hidden by redaction markers (feature 008). Centralised
 * so owned-Quest reads, shared reads, and export all apply the exact same filter and cannot drift.
 */
@Service
class DialogRedactionService(
    private val redactionRepository: DialogRedactionRepository,
) {
    /** Redacted turn ids and response ids across the given turns. */
    data class Redactions(val turnIds: Set<UUID>, val responseIds: Set<UUID>) {
        fun isTurnRedacted(turnId: UUID): Boolean = turnId in turnIds
        fun isResponseRedacted(responseId: UUID): Boolean = responseId in responseIds
    }

    fun forTurns(turnIds: Collection<UUID>): Redactions {
        if (turnIds.isEmpty()) return EMPTY
        val rows = redactionRepository.findByTurnIdIn(turnIds)
        if (rows.isEmpty()) return EMPTY
        val redactedTurns = rows.asSequence()
            .filter { it.scope == DialogRedaction.SCOPE_TURN }
            .map { it.turnId }
            .toSet()
        val redactedResponses = rows.asSequence()
            .filter { it.scope == DialogRedaction.SCOPE_RESPONSE }
            .mapNotNull { it.responseId }
            .toSet()
        return Redactions(redactedTurns, redactedResponses)
    }

    /** Filters a turn -> responses listing, dropping redacted turns and redacted responses. */
    fun <T> filter(
        turns: List<T>,
        turnIdOf: (T) -> UUID,
        responsesOf: (T) -> List<ProviderResponse>,
        rebuild: (T, List<ProviderResponse>) -> T,
    ): List<T> {
        val redactions = forTurns(turns.map(turnIdOf))
        if (redactions.turnIds.isEmpty() && redactions.responseIds.isEmpty()) return turns
        return turns
            .filterNot { redactions.isTurnRedacted(turnIdOf(it)) }
            .map { turn ->
                val visible = responsesOf(turn).filterNot { redactions.isResponseRedacted(it.id) }
                rebuild(turn, visible)
            }
    }

    private companion object {
        val EMPTY = Redactions(emptySet(), emptySet())
    }
}
