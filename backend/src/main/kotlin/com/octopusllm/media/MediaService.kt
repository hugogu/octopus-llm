package com.octopusllm.media

import com.octopusllm.admin.StorageSettingsService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * Validates and persists uploaded media (feature 007). Detects the actual content type from magic
 * bytes (rejecting spoofed/unsupported types), enforces the per-type size limit, stores the bytes via
 * the active backend under an opaque id, and records a [Media] row. Orphan deletion handles the
 * "discarded from the tray before sending" case (FR-008).
 */
@Service
class MediaService(
    private val storageSettingsService: StorageSettingsService,
    private val mediaRepository: MediaRepository,
    private val storageFactory: MediaStorageFactory,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private companion object {
        const val ORPHAN_TTL_SECONDS = 86_400L // 24h
    }

    @Transactional
    fun upload(ownerUserId: UUID, bytes: ByteArray, declaredContentType: String?, originalFilename: String?): Media {
        if (bytes.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty upload")
        }
        val detected = DetectedType.detect(bytes, declaredContentType)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported or undetectable media type")

        val settings = storageSettingsService.get()
        val limit = settings.maxBytesFor(detected.mediaType)
        if (bytes.size > limit) {
            throw ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "File exceeds the ${detected.mediaType} limit of $limit bytes (was ${bytes.size})",
            )
        }

        val id = UUID.randomUUID()
        val storage = storageFactory.resolve(settings)
        val stored = storage.store(id, bytes, detected.mimeType, detected.extension)
        val media = mediaRepository.save(
            Media(
                id = id,
                ownerUserId = ownerUserId,
                mediaType = detected.mediaType,
                mimeType = detected.mimeType,
                sizeBytes = bytes.size.toLong(),
                storageBackend = stored.backend,
                storageKey = stored.storageKey,
                publicUrl = stored.publicUrl,
                originalFilename = originalFilename,
            ),
        )
        log.info(
            "media_upload user={} mediaType={} mime={} sizeBytes={} backend={}",
            ownerUserId.toString().take(8), media.mediaType, media.mimeType, media.sizeBytes, media.storageBackend,
        )
        return media
    }

    /**
     * Scheduled orphan sweep (feature 007, FR-023): remove media uploaded but never bound to a turn
     * once it is older than the TTL, deleting both the stored object and the row. Idempotent and
     * lock-free, so it is safe to run on every instance.
     */
    @Scheduled(fixedDelayString = "\${media.orphan-sweep.interval-ms:3600000}")
    @Transactional
    fun sweepOrphans() {
        val cutoff = Instant.now().minusSeconds(ORPHAN_TTL_SECONDS)
        val orphans = mediaRepository.findByTurnIdIsNullAndCreatedAtBefore(cutoff)
        if (orphans.isEmpty()) return
        orphans.forEach { media ->
            runCatching { storageFactory.resolveByBackend(media.storageBackend)?.delete(media.storageKey) }
            mediaRepository.delete(media)
        }
        log.info("media_orphan_sweep removed={}", orphans.size)
    }

    /** Delete stored objects for the given turns' media (rows are cascade-removed by the DB, FR-024). */
    @Transactional
    fun purgeStoredForTurns(turnIds: Collection<UUID>) {
        turnIds.forEach { turnId ->
            mediaRepository.findByTurnId(turnId).forEach { media ->
                runCatching { storageFactory.resolveByBackend(media.storageBackend)?.delete(media.storageKey) }
            }
        }
    }

    @Transactional
    fun deleteOrphan(id: UUID, ownerUserId: UUID) {
        val media = mediaRepository.findByIdAndOwnerUserId(id, ownerUserId) ?: return
        if (media.turnId != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Media is attached to a saved turn and cannot be deleted")
        }
        runCatching { storageFactory.resolveByBackend(media.storageBackend)?.delete(media.storageKey) }
            .onFailure { log.warn("media_delete_storage_failed id={} error={}", id, it.message) }
        mediaRepository.delete(media)
    }

    /**
     * Detected media classification. Bytes win when a known signature is present. For formats that
     * cannot be recognized cheaply, the declared type is accepted only from an explicit allowlist
     * and is mapped to a canonical extension. This prevents an upload such as `image/svg+xml` or
     * `image/html` from becoming executable same-origin static content.
     */
    data class DetectedType(val mediaType: String, val mimeType: String, val extension: String) {
        companion object {
            private val declaredFallbacks = mapOf(
                "image/png" to DetectedType("image", "image/png", "png"),
                "image/jpeg" to DetectedType("image", "image/jpeg", "jpg"),
                "image/gif" to DetectedType("image", "image/gif", "gif"),
                "image/webp" to DetectedType("image", "image/webp", "webp"),
                "image/avif" to DetectedType("image", "image/avif", "avif"),
                "image/bmp" to DetectedType("image", "image/bmp", "bmp"),
                "image/heic" to DetectedType("image", "image/heic", "heic"),
                "image/heif" to DetectedType("image", "image/heif", "heif"),
                "video/mp4" to DetectedType("video", "video/mp4", "mp4"),
                "video/webm" to DetectedType("video", "video/webm", "webm"),
                "video/ogg" to DetectedType("video", "video/ogg", "ogv"),
                "video/quicktime" to DetectedType("video", "video/quicktime", "mov"),
                "video/x-m4v" to DetectedType("video", "video/x-m4v", "m4v"),
                "audio/mpeg" to DetectedType("audio", "audio/mpeg", "mp3"),
                "audio/mp4" to DetectedType("audio", "audio/mp4", "m4a"),
                "audio/aac" to DetectedType("audio", "audio/aac", "aac"),
                "audio/wav" to DetectedType("audio", "audio/wav", "wav"),
                "audio/x-wav" to DetectedType("audio", "audio/x-wav", "wav"),
                "audio/ogg" to DetectedType("audio", "audio/ogg", "ogg"),
                "audio/webm" to DetectedType("audio", "audio/webm", "webm"),
                "audio/flac" to DetectedType("audio", "audio/flac", "flac"),
                "audio/x-flac" to DetectedType("audio", "audio/x-flac", "flac"),
            )

            fun detect(bytes: ByteArray, declaredContentType: String?): DetectedType? {
                magicSniff(bytes, declaredContentType)?.let { return it }
                val declared = declaredContentType?.substringBefore(';')?.trim()?.lowercase()
                return declared?.let(declaredFallbacks::get)
            }

            private fun magicSniff(b: ByteArray, declared: String?): DetectedType? {
                fun startsWith(vararg sig: Int): Boolean =
                    b.size >= sig.size && sig.withIndex().all { (i, v) -> (b[i].toInt() and 0xFF) == v }
                return when {
                    startsWith(0x89, 0x50, 0x4E, 0x47) -> DetectedType("image", "image/png", "png")
                    startsWith(0xFF, 0xD8, 0xFF) -> DetectedType("image", "image/jpeg", "jpg")
                    startsWith(0x47, 0x49, 0x46, 0x38) -> DetectedType("image", "image/gif", "gif")
                    b.size >= 12 && startsWith(0x52, 0x49, 0x46, 0x46) &&
                        (b[8].toInt() and 0xFF) == 0x57 && (b[9].toInt() and 0xFF) == 0x45 ->
                        DetectedType("image", "image/webp", "webp")
                    b.size >= 12 && b[4].toInt() == 0x66 && b[5].toInt() == 0x74 &&
                        b[6].toInt() == 0x79 && b[7].toInt() == 0x70 -> { // ISO base media 'ftyp'
                        val brand = String(b, 8, 4, Charsets.US_ASCII)
                        if (brand in setOf("avif", "avis")) DetectedType("image", "image/avif", "avif")
                        else DetectedType("video", "video/mp4", "mp4")
                    }
                    startsWith(0x1A, 0x45, 0xDF, 0xA3) -> { // EBML — webm/mkv, audio or video
                        val family = declared?.substringBefore('/')?.lowercase()
                        if (family == "audio") DetectedType("audio", "audio/webm", "webm")
                        else DetectedType("video", "video/webm", "webm")
                    }
                    startsWith(0x49, 0x44, 0x33) -> DetectedType("audio", "audio/mpeg", "mp3") // ID3
                    startsWith(0xFF, 0xFB) || startsWith(0xFF, 0xF3) -> DetectedType("audio", "audio/mpeg", "mp3")
                    b.size >= 12 && startsWith(0x52, 0x49, 0x46, 0x46) &&
                        (b[8].toInt() and 0xFF) == 0x57 && (b[9].toInt() and 0xFF) == 0x41 ->
                        DetectedType("audio", "audio/wav", "wav")
                    startsWith(0x4F, 0x67, 0x67, 0x53) -> { // 'OggS'
                        val family = declared?.substringBefore('/')?.lowercase()
                        if (family == "video") DetectedType("video", "video/ogg", "ogv")
                        else DetectedType("audio", "audio/ogg", "ogg")
                    }
                    else -> null
                }
            }
        }
    }
}
