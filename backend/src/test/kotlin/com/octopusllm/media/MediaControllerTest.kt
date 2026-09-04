package com.octopusllm.media

import com.octopusllm.auth.JwtTokenService
import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import com.octopusllm.testsupport.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class MediaControllerTest @Autowired constructor(
    private val web: WebTestClient,
    private val users: UserRepository,
    private val media: MediaRepository,
    private val jwt: JwtTokenService,
) : AbstractPostgresIntegrationTest() {

    companion object {
        private val tempDir: Path = Files.createTempDirectory("octopus-media-test")

        @JvmStatic
        @DynamicPropertySource
        fun mediaProps(registry: DynamicPropertyRegistry) {
            registry.add("media.local.dir") { tempDir.toString() }
            registry.add("media.local.public-base-url") { "http://localhost:8080/media" }
        }
    }

    private val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private fun pngOfSize(totalBytes: Int): ByteArray =
        pngSignature + ByteArray(totalBytes - pngSignature.size)

    private fun ftyp(brand: String): ByteArray =
        byteArrayOf(0, 0, 0, 16, 0x66, 0x74, 0x79, 0x70) +
            brand.toByteArray(Charsets.US_ASCII) + ByteArray(4)

    private fun multipart(bytes: ByteArray, filename: String, type: MediaType) =
        MultipartBodyBuilder().apply {
            part(
                "file",
                object : ByteArrayResource(bytes) {
                    override fun getFilename() = filename
                },
            ).contentType(type)
        }.build()

    @Test
    fun `upload stores opaque media reference and orphan can be deleted`() {
        val user = users.save(User(email = "media-${UUID.randomUUID()}@example.com", passwordHash = "hash"))
        val bearer = jwt.issue(user.id, user.sessionEpoch)

        web.post().uri("/api/v2/media")
            .header("Authorization", "Bearer $bearer")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(multipart(pngOfSize(64), "pic.png", MediaType.IMAGE_PNG)))
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.media_type").isEqualTo("image")
            .jsonPath("$.mime_type").isEqualTo("image/png")
            .jsonPath("$.size_bytes").isEqualTo(64)
            .jsonPath("$.url").value<String> { assertTrue(it.contains("/media/")) }

        val saved = media.findAll().single { it.ownerUserId == user.id }
        assertNull(saved.turnId)
        assertEquals("local", saved.storageBackend)
        assertTrue(Files.exists(tempDir.resolve(saved.storageKey)))

        web.get().uri("/media/${saved.storageKey}")
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
            .expectHeader().value("Content-Security-Policy") { value ->
                assertTrue(value.contains("default-src 'none'"))
            }

        web.delete().uri("/api/v2/media/${saved.id}")
            .header("Authorization", "Bearer $bearer")
            .exchange()
            .expectStatus().isNoContent

        assertTrue(media.findAll().none { it.ownerUserId == user.id })
    }

    @Test
    fun `upload rejects scriptable and unknown declared media types`() {
        val user = users.save(User(email = "media-unsafe-${UUID.randomUUID()}@example.com", passwordHash = "hash"))
        val bearer = jwt.issue(user.id, user.sessionEpoch)

        web.post().uri("/api/v2/media")
            .header("Authorization", "Bearer $bearer")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(
                BodyInserters.fromMultipartData(
                    multipart("<svg><script>alert(1)</script></svg>".toByteArray(), "unsafe.svg", MediaType.valueOf("image/svg+xml")),
                ),
            )
            .exchange()
            .expectStatus().isBadRequest

        web.post().uri("/api/v2/media")
            .header("Authorization", "Bearer $bearer")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(
                BodyInserters.fromMultipartData(
                    multipart("not-media".toByteArray(), "unsafe.bin", MediaType.valueOf("image/x-made-up")),
                ),
            )
            .exchange()
            .expectStatus().isBadRequest

        assertTrue(media.findAll().none { it.ownerUserId == user.id })
    }

    @Test
    fun `upload preserves ISO base media image and audio classifications`() {
        val user = users.save(User(email = "media-iso-${UUID.randomUUID()}@example.com", passwordHash = "hash"))
        val bearer = jwt.issue(user.id, user.sessionEpoch)

        web.post().uri("/api/v2/media")
            .header("Authorization", "Bearer $bearer")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(multipart(ftyp("heic"), "photo.heic", MediaType.valueOf("image/heic"))))
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.media_type").isEqualTo("image")
            .jsonPath("$.mime_type").isEqualTo("image/heic")

        web.post().uri("/api/v2/media")
            .header("Authorization", "Bearer $bearer")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(multipart(ftyp("mif1"), "photo.heif", MediaType.valueOf("image/heif"))))
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.media_type").isEqualTo("image")
            .jsonPath("$.mime_type").isEqualTo("image/heif")

        web.post().uri("/api/v2/media")
            .header("Authorization", "Bearer $bearer")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(multipart(ftyp("M4A "), "audio.m4a", MediaType.valueOf("audio/mp4"))))
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.media_type").isEqualTo("audio")
            .jsonPath("$.mime_type").isEqualTo("audio/mp4")
    }

    @Test
    fun `oversize image is rejected before storage`() {
        val user = users.save(User(email = "media-big-${UUID.randomUUID()}@example.com", passwordHash = "hash"))
        val bearer = jwt.issue(user.id, user.sessionEpoch)

        // Default image limit is 1 MB; send ~1.1 MB.
        web.post().uri("/api/v2/media")
            .header("Authorization", "Bearer $bearer")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(multipart(pngOfSize(1_153_433), "big.png", MediaType.IMAGE_PNG)))
            .exchange()
            .expectStatus().isEqualTo(413)

        assertTrue(media.findAll().none { it.ownerUserId == user.id })
    }
}
