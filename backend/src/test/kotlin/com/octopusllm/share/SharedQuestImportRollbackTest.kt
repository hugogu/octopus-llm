package com.octopusllm.share

import com.ninjasquad.springmockk.MockkBean
import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import com.octopusllm.chat.ChatService
import com.octopusllm.chat.ChatSession
import com.octopusllm.chat.ChatSessionRepository
import com.octopusllm.chat.ChatTurn
import com.octopusllm.chat.ChatTurnRepository
import com.octopusllm.chat.SharedQuestImportTxOps
import com.octopusllm.media.MediaRepository
import com.octopusllm.media.MediaService
import com.octopusllm.migration.MigrationOperationRepository
import com.octopusllm.migration.MigrationStagedMediaRepository
import com.octopusllm.testsupport.AbstractPostgresIntegrationTest
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class SharedQuestImportRollbackTest @Autowired constructor(
    private val chatService: ChatService,
    private val users: UserRepository,
    private val sessions: ChatSessionRepository,
    private val turns: ChatTurnRepository,
    private val shares: SessionShareRepository,
    private val media: MediaRepository,
    private val mediaService: MediaService,
    private val operations: MigrationOperationRepository,
    private val stagedMedia: MigrationStagedMediaRepository,
) : AbstractPostgresIntegrationTest() {

    companion object {
        private val tempDir: Path = Files.createTempDirectory("octopus-shared-import-rollback")

        @JvmStatic
        @DynamicPropertySource
        fun mediaProps(registry: DynamicPropertyRegistry) {
            registry.add("media.local.dir") { tempDir.toString() }
            registry.add("media.local.public-base-url") { "http://localhost:8080/media" }
        }
    }

    @MockkBean
    private lateinit var txOps: SharedQuestImportTxOps

    @Test
    fun `clone transaction failure removes staged objects and creates no imported rows`() {
        val owner = users.save(User(email = "rollback-owner-${UUID.randomUUID()}@example.com", passwordHash = "h"))
        val importer = users.save(User(email = "rollback-importer-${UUID.randomUUID()}@example.com", passwordHash = "h"))
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val sourceMedia = mediaService.upload(owner.id, bytes, "image/png", "rollback.png")
        val session = sessions.save(ChatSession(user = owner, title = "Rollback source"))
        val turn = turns.save(
            ChatTurn(
                session = session,
                sequenceNum = 1,
                promptText = "copy me",
                attachments = listOf(mapOf("media_id" to sourceMedia.id.toString(), "url" to sourceMedia.publicUrl)),
                selectedModelIds = arrayOf("snapshot"),
                selectedConfiguredModelIds = arrayOf(UUID.randomUUID()),
            ),
        )
        sourceMedia.turnId = turn.id
        media.save(sourceMedia)
        val share = shares.save(SessionShare(session = session, token = UUID.randomUUID().toString().replace("-", "")))
        val filesBefore = Files.list(tempDir).use { it.map { path -> path.fileName.toString() }.toList().toSet() }
        every { txOps.commit(any(), any(), importer.id) } throws IllegalStateException("injected clone failure")

        assertThrows(IllegalStateException::class.java) {
            chatService.importFromShare(share.token, importer.id, UUID.randomUUID().toString()).block()
        }

        assertTrue(sessions.findByUserIdOrderByCreatedAtDesc(importer.id, org.springframework.data.domain.Pageable.unpaged()).isEmpty)
        assertTrue(media.findAll().none { it.ownerUserId == importer.id })
        val importerOperationIds = operations.findAll().filter { it.actorUserId == importer.id }.map { it.id }.toSet()
        assertTrue(stagedMedia.findAll().none { it.id.operationId in importerOperationIds })
        val filesAfter = Files.list(tempDir).use { it.map { path -> path.fileName.toString() }.toList().toSet() }
        assertEquals(filesBefore, filesAfter)
    }
}
