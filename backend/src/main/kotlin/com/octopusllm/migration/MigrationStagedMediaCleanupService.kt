package com.octopusllm.migration

import com.octopusllm.media.MediaStorageFactory
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

/**
 * Deletes storage objects left behind by failed or interrupted imports (feature 008, T014). Idempotent
 * and lock-free (Constitution VII): deleting an already-removed object is a no-op and deleting a row
 * twice is harmless, so every instance may run the sweep concurrently. The successful import commit
 * removes its own staging rows; this sweep is the safety net for crashes.
 */
@Service
class MigrationStagedMediaCleanupService(
    private val stagedMediaRepository: MigrationStagedMediaRepository,
    private val mediaStorageFactory: MediaStorageFactory,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val staleAfter: Duration = Duration.ofHours(1)

    @Scheduled(fixedDelayString = "\${migration.staged-media-sweep.interval-ms:3600000}")
    fun sweep() {
        val staleBefore = Instant.now().minus(staleAfter)
        val sweepable = stagedMediaRepository.findSweepable(staleBefore)
        if (sweepable.isEmpty()) return
        sweepable.forEach { staged ->
            runCatching {
                mediaStorageFactory.resolveByBackend(staged.storageBackend)?.delete(staged.storageKey)
            }.onFailure { log.warn("staged_media_sweep_delete_failed key={}", staged.storageKey, it) }
            stagedMediaRepository.delete(staged)
        }
        log.info("staged_media_swept count={}", sweepable.size)
    }
}
