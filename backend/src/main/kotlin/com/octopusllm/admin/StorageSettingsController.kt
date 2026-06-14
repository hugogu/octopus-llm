package com.octopusllm.admin

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant
import java.util.UUID

data class StorageSettingsView(
    val backend: String,
    val localPublicBaseUrl: String?,
    val s3Endpoint: String?,
    val s3Region: String?,
    val s3Bucket: String?,
    val s3AccessKey: String?,
    val s3SecretKeySet: Boolean,
    val s3PublicBaseUrl: String?,
    val maxImageBytes: Long,
    val maxVideoBytes: Long,
    val maxAudioBytes: Long,
    val maxFilesPerPrompt: Int,
    val maxTotalBytesPerPrompt: Long,
    val updatedAt: Instant,
    val updatedBy: UUID?,
)

/**
 * Admin storage configuration (feature 007). The S3 secret is never returned — only `s3SecretKeySet`.
 * Lives under the admin-only `/api/v2/admin` path space (gated in SecurityConfig).
 */
@RestController
@RequestMapping("/api/v2/admin/storage-settings")
class StorageSettingsController(
    private val service: StorageSettingsService,
) {
    @GetMapping
    fun get(): Mono<StorageSettingsView> =
        Mono.fromCallable { view(service.get()) }.subscribeOn(Schedulers.boundedElastic())

    @PutMapping
    fun update(
        @AuthenticationPrincipal principal: String,
        @RequestBody request: StorageSettingsUpdate,
    ): Mono<StorageSettingsView> =
        Mono.fromCallable { view(service.update(UUID.fromString(principal), request)) }
            .subscribeOn(Schedulers.boundedElastic())

    private fun view(s: StorageSettings) = StorageSettingsView(
        backend = s.backend,
        localPublicBaseUrl = s.localPublicBaseUrl,
        s3Endpoint = s.s3Endpoint,
        s3Region = s.s3Region,
        s3Bucket = s.s3Bucket,
        s3AccessKey = s.s3AccessKey,
        s3SecretKeySet = !s.s3SecretKey.isNullOrBlank(),
        s3PublicBaseUrl = s.s3PublicBaseUrl,
        maxImageBytes = s.maxImageBytes,
        maxVideoBytes = s.maxVideoBytes,
        maxAudioBytes = s.maxAudioBytes,
        maxFilesPerPrompt = s.maxFilesPerPrompt,
        maxTotalBytesPerPrompt = s.maxTotalBytesPerPrompt,
        updatedAt = s.updatedAt,
        updatedBy = s.updatedBy,
    )
}
