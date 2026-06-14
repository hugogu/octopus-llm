package com.octopusllm.media

import com.octopusllm.admin.StorageSettingsService
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.UUID

/**
 * Uniform media upload surface (feature 007, contracts/media-upload.md). One endpoint for
 * image/video/audio regardless of the target AI protocol; returns an opaque public reference that the
 * chat submit then attaches by id.
 */
@RestController
@RequestMapping("/api/v2/media")
class MediaController(
    private val mediaService: MediaService,
    private val storageSettingsService: StorageSettingsService,
) {
    /** Current media limits (admin-configurable) so the attachment tray messages them accurately. */
    @GetMapping("/limits")
    fun limits(): Mono<Map<String, Any>> =
        Mono.fromCallable {
            val s = storageSettingsService.get()
            mapOf<String, Any>(
                "maxImageBytes" to s.maxImageBytes,
                "maxVideoBytes" to s.maxVideoBytes,
                "maxAudioBytes" to s.maxAudioBytes,
                "maxFilesPerPrompt" to s.maxFilesPerPrompt,
                "maxTotalBytesPerPrompt" to s.maxTotalBytesPerPrompt,
            )
        }.subscribeOn(Schedulers.boundedElastic())

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun upload(
        @AuthenticationPrincipal principal: String,
        @RequestPart("file") file: FilePart,
    ): Mono<Map<String, Any?>> {
        val ownerId = UUID.fromString(principal)
        val contentType = file.headers().contentType?.toString()
        val filename = file.filename().ifBlank { null }
        return DataBufferUtils.join(file.content())
            .map { buffer ->
                val bytes = ByteArray(buffer.readableByteCount())
                buffer.read(bytes)
                DataBufferUtils.release(buffer)
                bytes
            }
            .flatMap { bytes ->
                Mono.fromCallable { mediaService.upload(ownerId, bytes, contentType, filename) }
                    .subscribeOn(Schedulers.boundedElastic())
            }
            .map(::mediaDto)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @AuthenticationPrincipal principal: String,
        @PathVariable id: UUID,
    ): Mono<Void> =
        Mono.fromRunnable<Void> { mediaService.deleteOrphan(id, UUID.fromString(principal)) }
            .subscribeOn(Schedulers.boundedElastic())

    private fun mediaDto(media: Media): Map<String, Any?> = mapOf(
        "media_id" to media.id,
        "media_type" to media.mediaType,
        "mime_type" to media.mimeType,
        "size_bytes" to media.sizeBytes,
        "url" to media.publicUrl,
        "original_filename" to media.originalFilename,
    )
}
