package com.octopusllm.migration

import com.fasterxml.jackson.databind.ObjectMapper
import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import com.octopusllm.chat.ChatSession
import com.octopusllm.chat.ChatSessionRepository
import com.octopusllm.chat.ChatTurn
import com.octopusllm.chat.ChatTurnRepository
import com.octopusllm.chat.ProviderResponse
import com.octopusllm.chat.ProviderResponseRepository
import com.octopusllm.connection.ConfiguredModel
import com.octopusllm.connection.ConfiguredModelRepository
import com.octopusllm.connection.Connection
import com.octopusllm.connection.ConnectionRepository
import com.octopusllm.media.MediaRepository
import com.octopusllm.media.MediaService
import com.octopusllm.testsupport.AbstractPostgresIntegrationTest
import com.octopusllm.userconfig.ApiKeyEncryptionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Pageable
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MigrationRoundTripTest @Autowired constructor(
    private val exportService: MigrationExportService,
    private val importService: MigrationImportService,
    private val userRepository: UserRepository,
    private val connectionRepository: ConnectionRepository,
    private val configuredModelRepository: ConfiguredModelRepository,
    private val sessionRepository: ChatSessionRepository,
    private val turnRepository: ChatTurnRepository,
    private val responseRepository: ProviderResponseRepository,
    private val encryptionService: ApiKeyEncryptionService,
    private val mediaService: MediaService,
    private val mediaRepository: MediaRepository,
    private val objectMapper: ObjectMapper,
) : AbstractPostgresIntegrationTest() {

    private val passphrase = "test-passphrase-1234567890"

    private data class Source(val key: String, val title: String, val prompt: String)

    private fun seedSource(): Source {
        val tag = UUID.randomUUID().toString().take(8)
        val source = Source(key = "sk-$tag", title = "Quest-$tag", prompt = "hello-$tag")
        val owner = userRepository.save(User(email = "src-$tag@example.com", passwordHash = "h", emailVerified = true))
        val enc = encryptionService.encrypt(source.key)
        val connection = connectionRepository.save(
            Connection(
                user = owner, protocol = "openai-compatible", label = "Conn-$tag",
                baseUrl = "https://8.8.8.8/v1", encryptedKey = enc.ciphertext, keyIv = enc.iv,
            ),
        )
        val model = configuredModelRepository.save(
            ConfiguredModel(user = owner, connection = connection, modelId = "gpt-4o", displayName = "GPT-4o-$tag"),
        )
        val session = sessionRepository.save(ChatSession(user = owner, title = source.title))
        val turn = turnRepository.save(
            ChatTurn(
                session = session, sequenceNum = 1, promptText = source.prompt,
                selectedModelIds = arrayOf("gpt-4o"), selectedConfiguredModelIds = arrayOf(model.id),
            ),
        )
        responseRepository.save(
            ProviderResponse(
                turn = turn, modelId = "gpt-4o", configuredModelId = model.id, modelDisplayName = "GPT-4o-$tag",
                protocol = "openai-compatible", status = "complete", responseText = "answer-$tag", latencyMs = 12,
            ),
        )
        return source
    }

    private fun newAdmin() = userRepository.save(
        User(email = "admin-${UUID.randomUUID().toString().take(8)}@example.com", passwordHash = "h", emailVerified = true),
    )

    @Test
    fun `round-trips connections and quests under the importing admin with keys re-encrypted`() {
        val source = seedSource()
        val admin = newAdmin()

        val artifact = exportService.export(passphrase)
        val result = importService.import(artifact, passphrase, admin.id, UUID.randomUUID().toString())

        assertTrue(result.connectionsImported >= 1 && result.questsImported >= 1)

        // The admin now owns a connection whose key decrypts to the source plaintext (re-encrypted
        // with the target master key, not copied ciphertext).
        val adminConnections = connectionRepository.findByUserId(admin.id, Pageable.unpaged()).content
        val match = adminConnections.firstOrNull {
            encryptionService.decrypt(it.encryptedKey, it.keyIv) == source.key
        }
        assertNotNull(match, "imported connection with re-encrypted key not found")

        // The admin owns a copy of the source Quest, flagged as imported, with the turn + response.
        val quest = sessionRepository.findByUserIdOrderByCreatedAtDesc(admin.id, Pageable.unpaged()).content
            .firstOrNull { it.title == source.title }
        assertNotNull(quest, "imported quest not found")
        assertNotNull(quest!!.importedFromLabel)
        val turns = turnRepository.findBySessionIdOrderBySequenceNum(quest.id)
        assertEquals(1, turns.size)
        assertEquals(source.prompt, turns.single().promptText)
        assertEquals(1, responseRepository.findByTurnId(turns.single().id).size)
    }

    // A minimal valid PNG (1x1) so DetectedType accepts the bytes during upload.
    private val pngBytes = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0, 0, 0, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0,
    )

    @Test
    fun `round-trips referenced media as fresh objects owned by the admin`() {
        val tag = UUID.randomUUID().toString().take(8)
        val owner = userRepository.save(User(email = "m-$tag@example.com", passwordHash = "h", emailVerified = true))
        val media = mediaService.upload(owner.id, pngBytes, "image/png", "pic-$tag.png")
        val session = sessionRepository.save(ChatSession(user = owner, title = "MediaQuest-$tag"))
        val turn = turnRepository.save(
            ChatTurn(
                session = session, sequenceNum = 1, promptText = "look",
                attachments = listOf(mapOf("media_id" to media.id.toString(), "url" to media.publicUrl, "order" to 0)),
                selectedModelIds = arrayOf("gpt-4o"), selectedConfiguredModelIds = arrayOf(UUID.randomUUID()),
            ),
        )
        media.turnId = turn.id
        mediaRepository.save(media)

        val admin = newAdmin()
        val artifact = exportService.export(passphrase)
        val result = importService.import(artifact, passphrase, admin.id, UUID.randomUUID().toString())

        assertTrue(result.mediaImported >= 1, "expected at least one media imported")
        val importedQuest = sessionRepository.findByUserIdOrderByCreatedAtDesc(admin.id, Pageable.unpaged())
            .content.first { it.title == "MediaQuest-$tag" }
        val importedTurn = turnRepository.findBySessionIdOrderBySequenceNum(importedQuest.id).single()
        val newMediaId = UUID.fromString(importedTurn.attachments!!.single()["media_id"] as String)
        // The attachment points at a fresh media row owned by the admin, not the source media id.
        assertTrue(newMediaId != media.id, "media id should be remapped")
        val importedMedia = mediaRepository.findById(newMediaId).orElseThrow()
        assertEquals(admin.id, importedMedia.ownerUserId)
        assertEquals(importedTurn.id, importedMedia.turnId)
    }

    @Test
    fun `wrong passphrase is rejected as invalid credentials`() {
        seedSource()
        val admin = newAdmin()
        val artifact = exportService.export(passphrase)
        val error = assertThrows(ResponseStatusException::class.java) {
            importService.import(artifact, "a-different-passphrase-000", admin.id, UUID.randomUUID().toString())
        }
        assertTrue(error.reason?.contains("invalid_artifact_credentials") == true)
    }

    @Test
    fun `incompatible format version is rejected`() {
        val admin = newAdmin()
        val badEnvelope = mapOf("formatVersion" to 999, "createdAt" to "2026-01-01T00:00:00Z", "kdf" to "x", "saltHex" to "00", "entries" to emptyList<Any>())
        val zip = zipOf("envelope.json" to objectMapper.writeValueAsBytes(badEnvelope))
        val error = assertThrows(ResponseStatusException::class.java) {
            importService.import(zip, passphrase, admin.id, UUID.randomUUID().toString())
        }
        assertTrue(error.reason?.contains("incompatible_version") == true)
    }

    @Test
    fun `missing envelope is an invalid bundle`() {
        val admin = newAdmin()
        val zip = zipOf("quests/x.enc" to byteArrayOf(1, 2, 3))
        val error = assertThrows(ResponseStatusException::class.java) {
            importService.import(zip, passphrase, admin.id, UUID.randomUUID().toString())
        }
        assertTrue(error.reason?.contains("invalid_bundle") == true)
    }

    @Test
    fun `idempotent replay returns the original result without duplicating`() {
        val source = seedSource()
        val admin = newAdmin()
        val artifact = exportService.export(passphrase)
        val key = UUID.randomUUID().toString()

        val first = importService.import(artifact, passphrase, admin.id, key)
        val second = importService.import(artifact, passphrase, admin.id, key)

        assertEquals(first.toResultMap(), second.toResultMap())
        val copies = sessionRepository.findByUserIdOrderByCreatedAtDesc(admin.id, Pageable.unpaged())
            .content.count { it.title == source.title }
        assertEquals(1, copies, "replay must not create a second copy")
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
