package com.octopusllm.admin

import com.octopusllm.auth.JwtTokenService
import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import com.octopusllm.testsupport.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.MinIOContainer
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import java.net.URI
import java.util.UUID

class StorageSettingsControllerTest @Autowired constructor(
    private val web: WebTestClient,
    private val users: UserRepository,
    private val jwt: JwtTokenService,
    private val storageSettings: StorageSettingsService,
) : AbstractPostgresIntegrationTest() {

    // The storage_settings row is a single shared row; reset it to local so this test does not
    // leave the platform on S3 for other integration tests that assume local storage.
    @org.junit.jupiter.api.AfterEach
    fun resetToLocal() {
        val admin = users.save(
            User(email = "reset-${UUID.randomUUID()}@example.com", passwordHash = "hash", isAdmin = true, isActive = true),
        )
        storageSettings.update(admin.id, StorageSettingsUpdate(backend = "local"))
    }

    companion object {
        @JvmStatic
        private val minio: MinIOContainer = MinIOContainer("minio/minio:RELEASE.2024-08-29T01-40-52Z").apply { start() }
        private const val BUCKET = "octopus-test"

        @JvmStatic
        @BeforeAll
        fun createBucket() {
            S3Client.builder()
                .endpointOverride(URI.create(minio.s3URL))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(minio.userName, minio.password)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()
                .use { runCatching { it.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build()) } }
        }
    }

    private fun adminToken(): String {
        val admin = users.save(
            User(email = "store-${UUID.randomUUID()}@example.com", passwordHash = "hash", isAdmin = true, isActive = true),
        )
        return jwt.issue(admin.id, admin.sessionEpoch)
    }

    @Test
    fun `get returns defaults and never exposes the secret`() {
        web.get().uri("/api/v2/admin/storage-settings")
            .header("Authorization", "Bearer ${adminToken()}")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.backend").isEqualTo("local")
            .jsonPath("$.s3SecretKeySet").isEqualTo(false)
            .jsonPath("$.s3SecretKey").doesNotExist()
            .jsonPath("$.maxImageBytes").isEqualTo(1048576)
    }

    @Test
    fun `update to s3 validates connectivity and hides the secret`() {
        val token = adminToken()
        web.put().uri("/api/v2/admin/storage-settings")
            .header("Authorization", "Bearer $token")
            .bodyValue(
                mapOf(
                    "backend" to "s3",
                    "s3Endpoint" to minio.s3URL,
                    "s3Region" to "us-east-1",
                    "s3Bucket" to BUCKET,
                    "s3AccessKey" to minio.userName,
                    "s3SecretKey" to minio.password,
                    "s3PublicBaseUrl" to "${minio.s3URL}/$BUCKET",
                ),
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.backend").isEqualTo("s3")
            .jsonPath("$.s3SecretKeySet").isEqualTo(true)
            .jsonPath("$.s3SecretKey").doesNotExist()
    }

    @Test
    fun `update to s3 with unreachable endpoint is rejected`() {
        web.put().uri("/api/v2/admin/storage-settings")
            .header("Authorization", "Bearer ${adminToken()}")
            .bodyValue(
                mapOf(
                    "backend" to "s3",
                    "s3Endpoint" to "http://127.0.0.1:1",
                    "s3Bucket" to BUCKET,
                    "s3AccessKey" to "x",
                    "s3SecretKey" to "y",
                    "s3PublicBaseUrl" to "http://127.0.0.1:1/$BUCKET",
                ),
            )
            .exchange()
            .expectStatus().isEqualTo(422)
    }
}
