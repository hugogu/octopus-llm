package com.octopusllm.admin

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Platform-wide media storage configuration (feature 007). Single mutable row (id = 1): the active
 * backend, its connection parameters, and per-type size limits. The S3 secret is stored encrypted and
 * is never exposed through the API.
 */
@Entity
@Table(name = "storage_settings")
class StorageSettings(
    @Id
    @Column(name = "id")
    val id: Short = 1,

    @Column(name = "backend", nullable = false, length = 16)
    var backend: String = "local",

    @Column(name = "local_public_base_url", columnDefinition = "TEXT")
    var localPublicBaseUrl: String? = null,

    @Column(name = "s3_endpoint", columnDefinition = "TEXT")
    var s3Endpoint: String? = null,

    @Column(name = "s3_region", columnDefinition = "TEXT")
    var s3Region: String? = null,

    @Column(name = "s3_bucket", columnDefinition = "TEXT")
    var s3Bucket: String? = null,

    @Column(name = "s3_access_key", columnDefinition = "TEXT")
    var s3AccessKey: String? = null,

    @Column(name = "s3_secret_key", columnDefinition = "TEXT")
    var s3SecretKey: String? = null,

    @Column(name = "s3_public_base_url", columnDefinition = "TEXT")
    var s3PublicBaseUrl: String? = null,

    @Column(name = "max_image_bytes", nullable = false)
    var maxImageBytes: Long = 1_048_576,

    @Column(name = "max_video_bytes", nullable = false)
    var maxVideoBytes: Long = 10_485_760,

    @Column(name = "max_audio_bytes", nullable = false)
    var maxAudioBytes: Long = 10_485_760,

    @Column(name = "max_files_per_prompt", nullable = false)
    var maxFilesPerPrompt: Int = 5,

    @Column(name = "max_total_bytes_per_prompt", nullable = false)
    var maxTotalBytesPerPrompt: Long = 15_728_640,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "updated_by", columnDefinition = "UUID")
    var updatedBy: UUID? = null,
) {
    companion object {
        const val SINGLETON_ID: Short = 1
    }

    fun maxBytesFor(mediaType: String): Long = when (mediaType) {
        "image" -> maxImageBytes
        "video" -> maxVideoBytes
        "audio" -> maxAudioBytes
        else -> maxImageBytes
    }
}
