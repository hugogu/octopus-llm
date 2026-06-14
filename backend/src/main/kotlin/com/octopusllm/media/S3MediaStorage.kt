package com.octopusllm.media

import com.octopusllm.admin.StorageSettingsService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * S3/OSS-compatible media storage (feature 007). Reads its connection config from the admin storage
 * settings at runtime and caches an S3 client keyed by that config. Objects are stored under opaque
 * keys; public URLs come from the configured public/CDN base (the bucket is assumed publicly
 * readable, per the public-by-default media decision).
 */
@Component
class S3MediaStorage(
    private val storageSettingsService: StorageSettingsService,
) : MediaStorage {
    override val backend = "s3"

    private val cached = AtomicReference<Pair<String, S3Client>?>(null)

    private fun config(): S3RuntimeConfig = storageSettingsService.currentS3Config()
        ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "S3 storage is not configured")

    private fun client(config: S3RuntimeConfig): S3Client {
        val signature = "${config.endpoint}|${config.region}|${config.bucket}|${config.accessKey}"
        cached.get()?.let { if (it.first == signature) return it.second }
        val client = S3Support.client(config)
        cached.getAndSet(signature to client)?.second?.let { runCatching { it.close() } }
        return client
    }

    override fun store(id: UUID, bytes: ByteArray, mimeType: String, extension: String): StoredMedia {
        val cfg = config()
        val key = if (extension.isBlank()) id.toString() else "$id.$extension"
        client(cfg).putObject(
            PutObjectRequest.builder().bucket(cfg.bucket).key(key).contentType(mimeType).build(),
            RequestBody.fromBytes(bytes),
        )
        return StoredMedia(
            storageKey = key,
            publicUrl = "${cfg.publicBaseUrl.trimEnd('/')}/$key",
            backend = backend,
        )
    }

    override fun read(storageKey: String): ByteArray? {
        val cfg = config()
        return runCatching {
            client(cfg).getObjectAsBytes(GetObjectRequest.builder().bucket(cfg.bucket).key(storageKey).build()).asByteArray()
        }.getOrNull()
    }

    override fun delete(storageKey: String) {
        val cfg = config()
        runCatching {
            client(cfg).deleteObject(DeleteObjectRequest.builder().bucket(cfg.bucket).key(storageKey).build())
        }
    }
}
