package com.octopusllm.migration

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Raised when an idempotency key is reused for materially different request material (→ 409). */
class IdempotencyConflictException :
    ResponseStatusException(HttpStatus.CONFLICT, "idempotency_conflict")

/**
 * Lifecycle + idempotency operations for the migration ledger (feature 008, T013). Claiming an import
 * is atomic across instances via the partial unique index on
 * `(actor_user_id, operation_type, idempotency_key_hash)`; no distributed lock is used (Constitution
 * VII). The raw idempotency key is never stored — only its SHA-256.
 */
@Service
class MigrationOperationService(
    private val repository: MigrationOperationRepository,
) {
    /** Result of claiming an operation: the row plus whether this caller created it. */
    data class Claim(val operation: MigrationOperation, val isNew: Boolean)

    /** Records an export operation (no idempotency key; exports are not deduplicated). */
    fun startExport(actorUserId: UUID): MigrationOperation =
        repository.save(
            MigrationOperation(
                actorUserId = actorUserId,
                operationType = MigrationOperation.TYPE_ADMIN_EXPORT,
            ),
        )

    /**
     * Idempotently claims an import operation. A retry with the same actor/type/key returns the
     * existing operation; the same key with a different `sourceDigest` is a conflict.
     */
    fun claimImport(
        actorUserId: UUID,
        operationType: String,
        idempotencyKey: String,
        sourceDigest: ByteArray,
    ): Claim {
        val keyHash = sha256(idempotencyKey.toByteArray(Charsets.UTF_8))
        existingClaim(actorUserId, operationType, keyHash, sourceDigest)?.let { return it }
        return try {
            Claim(
                repository.saveAndFlush(
                    MigrationOperation(
                        actorUserId = actorUserId,
                        operationType = operationType,
                        idempotencyKeyHash = keyHash,
                        sourceDigest = sourceDigest,
                    ),
                ),
                isNew = true,
            )
        } catch (_: DataIntegrityViolationException) {
            // Lost the race to a concurrent retry on another instance — adopt the winner's row.
            existingClaim(actorUserId, operationType, keyHash, sourceDigest)
                ?: throw IdempotencyConflictException()
        }
    }

    fun succeed(operation: MigrationOperation, result: Map<String, Any?>): MigrationOperation {
        operation.status = MigrationOperation.STATUS_SUCCEEDED
        operation.result = result
        operation.updatedAt = Instant.now()
        return repository.save(operation)
    }

    fun fail(operation: MigrationOperation): MigrationOperation {
        operation.status = MigrationOperation.STATUS_FAILED
        operation.updatedAt = Instant.now()
        return repository.save(operation)
    }

    private fun existingClaim(
        actorUserId: UUID,
        operationType: String,
        keyHash: ByteArray,
        sourceDigest: ByteArray,
    ): Claim? {
        val existing = repository.findByActorUserIdAndOperationTypeAndIdempotencyKeyHash(
            actorUserId, operationType, keyHash,
        ) ?: return null
        if (existing.sourceDigest != null && !existing.sourceDigest.contentEquals(sourceDigest)) {
            throw IdempotencyConflictException()
        }
        return Claim(existing, isNew = false)
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
