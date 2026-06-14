package com.octopusllm.media

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import java.net.URI

/** Runtime S3/OSS connection parameters (feature 007), resolved from admin storage settings. */
data class S3RuntimeConfig(
    val endpoint: String,
    val region: String?,
    val bucket: String,
    val accessKey: String,
    val secretKey: String,
    val publicBaseUrl: String,
)

/** Builds path-style S3 clients (OSS/MinIO-compatible) for both storage and connectivity checks. */
object S3Support {
    fun client(config: S3RuntimeConfig): S3Client =
        S3Client.builder()
            .endpointOverride(URI.create(config.endpoint))
            .region(Region.of(config.region?.ifBlank { null } ?: "us-east-1"))
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create(config.accessKey, config.secretKey)),
            )
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build()
}
