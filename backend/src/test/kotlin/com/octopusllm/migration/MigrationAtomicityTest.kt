package com.octopusllm.migration

import com.ninjasquad.springmockk.MockkBean
import com.octopusllm.admin.StorageSettingsService
import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import com.octopusllm.chat.ChatSession
import com.octopusllm.chat.ChatSessionRepository
import com.octopusllm.chat.ChatTurn
import com.octopusllm.chat.ChatTurnRepository
import com.octopusllm.connection.ConfiguredModelRepository
import com.octopusllm.connection.ConnectionRepository
import com.octopusllm.media.MediaRepository
import com.octopusllm.media.MediaService
import com.octopusllm.media.MediaStorageFactory
import com.octopusllm.testsupport.AbstractPostgresIntegrationTest
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class MigrationAtomicityTest @Autowired constructor(
    private val exportService: MigrationExportService,
    private val importService: MigrationImportService,
    private val cleanupService: MigrationStagedMediaCleanupService,
    private val operationRepository: MigrationOperationRepository,
    private val stagedRepository: MigrationStagedMediaRepository,
    private val userRepository: UserRepository,
    private val connectionRepository: ConnectionRepository,
    private val configuredModelRepository: ConfiguredModelRepository,
    private val sessionRepository: ChatSessionRepository,
    private val turnRepository: ChatTurnRepository,
    private val mediaRepository: MediaRepository,
    private val mediaService: MediaService,
    private val storageSettingsService: StorageSettingsService,
    private val storageFactory: MediaStorageFactory,
) : AbstractPostgresIntegrationTest() {

    companion object {
        private val tempDir: Path = Files.createTempDirectory("octopus-migration-atomicity")

        @JvmStatic
        @DynamicPropertySource
        fun mediaProps(registry: DynamicPropertyRegistry) {
            registry.add("media.local.dir") { tempDir.toString() }
            registry.add("media.local.public-base-url") { "http://localhost:8080/media" }
        }
    }

    @MockkBean
    private lateinit var txOps: MigrationImportTxOps

    private val passphrase = "atomicity-test-passphrase-1234"
    private val png = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0, 0, 0, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0,
    )

    @Test
    fun `database failure after media staging compensates blobs and creates no business rows`() {
        val source = userRepository.save(
            User(email = "atomic-source-${UUID.randomUUID()}@example.com", passwordHash = "h", emailVerified = true),
        )
        val sourceMedia = mediaService.upload(source.id, png, "image/png", "atomic.png")
        val sourceSession = sessionRepository.save(ChatSession(user = source, title = "Atomic source"))
        val sourceTurn = turnRepository.save(
            ChatTurn(
                session = sourceSession,
                sequenceNum = 1,
                promptText = "with media",
                attachments = listOf(mapOf("media_id" to sourceMedia.id.toString(), "url" to sourceMedia.publicUrl)),
                selectedModelIds = arrayOf("snapshot"),
                selectedConfiguredModelIds = arrayOf(UUID.randomUUID()),
            ),
        )
        sourceMedia.turnId = sourceTurn.id
        mediaRepository.save(sourceMedia)

        val admin = userRepository.save(
            User(email = "atomic-admin-${UUID.randomUUID()}@example.com", passwordHash = "h", emailVerified = true),
        )
        val filesBefore = Files.list(tempDir).use { stream -> stream.map { it.fileName.toString() }.toList().toSet() }
        val artifact = exportService.export(passphrase)
        every { txOps.commit(any(), any(), admin.id) } throws IllegalStateException("injected DB failure")

        assertThrows(IllegalStateException::class.java) {
            importService.import(artifact, passphrase, admin.id, UUID.randomUUID().toString())
        }

        assertTrue(sessionRepository.findByUserIdOrderByCreatedAtDesc(admin.id, org.springframework.data.domain.Pageable.unpaged()).isEmpty)
        assertTrue(connectionRepository.findByUserId(admin.id, org.springframework.data.domain.Pageable.unpaged()).isEmpty)
        assertTrue(configuredModelRepository.findByUserId(admin.id, org.springframework.data.domain.Pageable.unpaged()).isEmpty)
        assertTrue(mediaRepository.findAll().none { it.ownerUserId == admin.id })
        assertTrue(stagedRepository.findAll().none { it.id.operationId in operationRepository.findAll().filter { op -> op.actorUserId == admin.id }.map { op -> op.id } })
        val filesAfter = Files.list(tempDir).use { stream -> stream.map { it.fileName.toString() }.toList().toSet() }
        assertEquals(filesBefore, filesAfter, "staged media objects must be compensated")
    }

    @Test
    fun `stale interrupted staging is removed by the idempotent sweep`() {
        val admin = userRepository.save(
            User(email = "stale-admin-${UUID.randomUUID()}@example.com", passwordHash = "h", emailVerified = true),
        )
        val operation = operationRepository.save(
            MigrationOperation(
                actorUserId = admin.id,
                operationType = MigrationOperation.TYPE_ADMIN_IMPORT,
                idempotencyKeyHash = ByteArray(32) { 7 },
                sourceDigest = ByteArray(32) { 9 },
                updatedAt = Instant.now().minusSeconds(7_200),
            ),
        )
        val storage = storageFactory.resolve(storageSettingsService.get())
        val mediaId = UUID.randomUUID()
        val stored = storage.store(mediaId, png, "image/png", "png")
        stagedRepository.saveAndFlush(
            MigrationStagedMedia(
                id = MigrationStagedMediaId(operation.id, mediaId),
                storageBackend = stored.backend,
                storageKey = stored.storageKey,
            ),
        )
        assertTrue(storage.read(stored.storageKey)?.contentEquals(png) == true)

        cleanupService.sweep()
        cleanupService.sweep()

        assertFalse(stagedRepository.existsById(MigrationStagedMediaId(operation.id, mediaId)))
        assertEquals(null, storage.read(stored.storageKey))
    }
}
