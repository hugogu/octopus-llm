package com.octopusllm.media

import com.octopusllm.admin.StorageSettings
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

/**
 * Resolves the active [MediaStorage] from the configured backend (feature 007). All [MediaStorage]
 * beans are indexed by their [MediaStorage.backend] id; adding a new backend (e.g. S3 in US5) is just
 * a new bean — no change here.
 */
@Component
class MediaStorageFactory(storages: List<MediaStorage>) {
    private val byBackend: Map<String, MediaStorage> = storages.associateBy { it.backend }

    fun resolve(settings: StorageSettings): MediaStorage =
        byBackend[settings.backend]
            ?: throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Storage backend '${settings.backend}' is not available",
            )

    /** Resolve the backend an object was stored in (may differ from the current active backend). */
    fun resolveByBackend(backend: String): MediaStorage? = byBackend[backend]
}
