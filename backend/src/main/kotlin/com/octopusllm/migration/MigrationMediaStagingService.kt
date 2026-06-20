package com.octopusllm.migration

import com.octopusllm.admin.StorageSettingsService
import com.octopusllm.media.MediaStorageFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

data class MediaToStage(
    val sourceMediaId: UUID,
    val mediaType: String,
    val mimeType: String,
    val sizeBytes: Long,
    val originalFilename: String?,
    val content: ByteArray,
)

/**
 * Shared external-side-effect boundary for artifact and shared-Quest imports. The cleanup ledger is
 * flushed before each deterministic object write; callers then commit DB rows in one transaction.
 */
@Service
class MigrationMediaStagingService(
    private val storageSettingsService: StorageSettingsService,
    private val mediaStorageFactory: MediaStorageFactory,
    private val stagedMediaRepository: MigrationStagedMediaRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun stage(operationId: UUID, media: List<MediaToStage>): List<StagedMedia> {
        if (media.isEmpty()) return emptyList()
        val storage = mediaStorageFactory.resolve(storageSettingsService.get())
        return media.map { item ->
            val newId = UUID.randomUUID()
            val extension = item.mimeType.substringAfter('/').substringBefore('+').ifBlank { "bin" }
            stagedMediaRepository.saveAndFlush(
                MigrationStagedMedia(
                    id = MigrationStagedMediaId(operationId, newId),
                    storageBackend = storage.backend,
                    storageKey = storage.storageKey(newId, extension),
                ),
            )
            val stored = storage.store(newId, item.content, item.mimeType, extension)
            StagedMedia(
                artifactMediaId = item.sourceMediaId,
                newId = newId,
                mediaType = item.mediaType,
                mimeType = item.mimeType,
                sizeBytes = item.sizeBytes,
                originalFilename = item.originalFilename,
                backend = stored.backend,
                storageKey = stored.storageKey,
                publicUrl = stored.publicUrl,
            )
        }
    }

    fun complete(operationId: UUID) {
        stagedMediaRepository.deleteByIdOperationId(operationId)
    }

    fun compensate(operationId: UUID, staged: List<StagedMedia>) {
        staged.forEach { item ->
            runCatching { mediaStorageFactory.resolveByBackend(item.backend)?.delete(item.storageKey) }
                .onFailure { log.warn("migration_import_compensate_failed key={}", item.storageKey, it) }
        }
        stagedMediaRepository.deleteByIdOperationId(operationId)
    }
}
