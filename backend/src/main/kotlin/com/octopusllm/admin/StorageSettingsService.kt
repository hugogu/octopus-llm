package com.octopusllm.admin

import com.octopusllm.media.S3RuntimeConfig
import com.octopusllm.media.S3Support
import com.octopusllm.userconfig.ApiKeyEncryptionService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import java.time.Instant
import java.util.Base64
import java.util.UUID

/** Admin-supplied storage configuration update (feature 007). Null fields are left unchanged. */
data class StorageSettingsUpdate(
    val backend: String? = null,
    val localPublicBaseUrl: String? = null,
    val s3Endpoint: String? = null,
    val s3Region: String? = null,
    val s3Bucket: String? = null,
    val s3AccessKey: String? = null,
    val s3SecretKey: String? = null,
    val s3PublicBaseUrl: String? = null,
    val maxImageBytes: Long? = null,
    val maxVideoBytes: Long? = null,
    val maxAudioBytes: Long? = null,
    val maxFilesPerPrompt: Int? = null,
    val maxTotalBytesPerPrompt: Long? = null,
)

/**
 * Platform storage configuration (feature 007). Read path seeds a default local row; the admin update
 * path validates field coherence, verifies S3/OSS connectivity before persisting, and stores the
 * secret encrypted (never returned by the API).
 */
@Service
class StorageSettingsService(
    private val repository: StorageSettingsRepository,
    private val encryptionService: ApiKeyEncryptionService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun get(): StorageSettings =
        repository.findById(StorageSettings.SINGLETON_ID).orElseGet { repository.save(StorageSettings()) }

    /** Resolved, decrypted S3 runtime config, or null when S3 is not fully configured. */
    fun currentS3Config(): S3RuntimeConfig? {
        val s = get()
        val secret = s.s3SecretKey?.let { runCatching { decryptSecret(it) }.getOrNull() }
        if (s.s3Endpoint.isNullOrBlank() || s.s3Bucket.isNullOrBlank() || s.s3AccessKey.isNullOrBlank() ||
            secret.isNullOrBlank() || s.s3PublicBaseUrl.isNullOrBlank()
        ) {
            return null
        }
        return S3RuntimeConfig(s.s3Endpoint!!, s.s3Region, s.s3Bucket!!, s.s3AccessKey!!, secret, s.s3PublicBaseUrl!!)
    }

    @Transactional
    fun update(adminId: UUID, req: StorageSettingsUpdate): StorageSettings {
        val s = get()
        val backend = (req.backend ?: s.backend).also {
            if (it !in setOf("local", "s3")) throw badRequest("backend must be 'local' or 's3'")
        }

        val endpoint = req.s3Endpoint ?: s.s3Endpoint
        val bucket = req.s3Bucket ?: s.s3Bucket
        val accessKey = req.s3AccessKey ?: s.s3AccessKey
        val region = req.s3Region ?: s.s3Region
        val publicBaseUrl = req.s3PublicBaseUrl ?: s.s3PublicBaseUrl
        val newSecretPlain = req.s3SecretKey?.takeIf { it.isNotBlank() }
        val secretPlain = newSecretPlain ?: s.s3SecretKey?.let { runCatching { decryptSecret(it) }.getOrNull() }

        if (backend == "s3") {
            if (endpoint.isNullOrBlank() || bucket.isNullOrBlank() || accessKey.isNullOrBlank() ||
                secretPlain.isNullOrBlank() || publicBaseUrl.isNullOrBlank()
            ) {
                throw badRequest("S3 backend requires endpoint, bucket, access key, secret, and public base URL")
            }
            verifyS3Connectivity(S3RuntimeConfig(endpoint, region, bucket, accessKey, secretPlain, publicBaseUrl))
        } else if (req.localPublicBaseUrl != null && req.localPublicBaseUrl.isBlank()) {
            throw badRequest("local public base URL must not be blank")
        }

        // All checks passed — apply.
        s.backend = backend
        req.localPublicBaseUrl?.let { s.localPublicBaseUrl = it }
        req.s3Endpoint?.let { s.s3Endpoint = it }
        req.s3Region?.let { s.s3Region = it }
        req.s3Bucket?.let { s.s3Bucket = it }
        req.s3AccessKey?.let { s.s3AccessKey = it }
        req.s3PublicBaseUrl?.let { s.s3PublicBaseUrl = it }
        newSecretPlain?.let { s.s3SecretKey = encryptSecret(it) }
        req.maxImageBytes?.let { s.maxImageBytes = requirePositive(it, "maxImageBytes") }
        req.maxVideoBytes?.let { s.maxVideoBytes = requirePositive(it, "maxVideoBytes") }
        req.maxAudioBytes?.let { s.maxAudioBytes = requirePositive(it, "maxAudioBytes") }
        req.maxFilesPerPrompt?.let { s.maxFilesPerPrompt = requirePositive(it.toLong(), "maxFilesPerPrompt").toInt() }
        req.maxTotalBytesPerPrompt?.let { s.maxTotalBytesPerPrompt = requirePositive(it, "maxTotalBytesPerPrompt") }
        s.updatedBy = adminId
        s.updatedAt = Instant.now()
        log.info("storage_settings_updated by={} backend={}", adminId.toString().take(8), backend)
        return repository.save(s)
    }

    private fun verifyS3Connectivity(config: S3RuntimeConfig) {
        runCatching {
            S3Support.client(config).use { it.headBucket(HeadBucketRequest.builder().bucket(config.bucket).build()) }
        }.onFailure {
            log.warn("storage_settings_s3_unreachable: {}", it.message)
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "S3/OSS endpoint unreachable or credentials invalid")
        }
    }

    private fun encryptSecret(plain: String): String {
        val enc = encryptionService.encrypt(plain)
        val b64 = Base64.getEncoder()
        return "${b64.encodeToString(enc.iv)}:${b64.encodeToString(enc.ciphertext)}"
    }

    private fun decryptSecret(stored: String): String {
        val parts = stored.split(":", limit = 2)
        require(parts.size == 2) { "malformed stored secret" }
        val dec = Base64.getDecoder()
        return encryptionService.decrypt(dec.decode(parts[1]), dec.decode(parts[0]))
    }

    private fun requirePositive(value: Long, field: String): Long =
        if (value > 0) value else throw badRequest("$field must be positive")

    private fun badRequest(message: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, message)
}
