package com.octopusllm.anonymous

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.octopusllm.auth.UserRepository
import com.octopusllm.chat.ChatSession
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.time.Instant
import java.util.Optional
import java.util.UUID

class AnonymousConversationSyncServiceTest {
    private val users = mockk<UserRepository>()
    private val imports = mockk<AnonymousConversationImportRepository>()
    private val transactions = mockk<AnonymousConversationSyncTransactionService>()
    private val service = AnonymousConversationSyncService(
        users,
        imports,
        transactions,
        jacksonObjectMapper().registerModule(JavaTimeModule()),
        20,
        5_000_000,
    )

    @Test
    fun `digest mismatch is skipped without importing or calling a provider`() {
        val user = com.octopusllm.testsupport.Feature003Fixtures.user()
        val conversationId = UUID.randomUUID()
        every { users.findById(user.id) } returns Optional.of(user)
        every { imports.findByUserIdAndSourceConversationId(user.id, conversationId) } returns null

        val response = service.sync(
            user.id,
            AnonymousSyncRequest(
                listOf(
                    AnonymousSyncConversationInput(
                        sourceConversationId = conversationId,
                        sourceDigest = "0".repeat(64),
                        title = "Local",
                        createdAt = Instant.parse("2026-09-02T00:00:00.000Z"),
                        updatedAt = Instant.parse("2026-09-02T00:01:00.000Z"),
                        turns = listOf(
                            AnonymousSyncTurnInput(
                                sourceTurnId = UUID.randomUUID(),
                                clientRequestId = UUID.randomUUID().toString(),
                                promptText = "hello",
                                createdAt = Instant.parse("2026-09-02T00:00:00.000Z"),
                                responses = listOf(
                                    AnonymousSyncResponseInput(
                                        configuredModelId = UUID.randomUUID(),
                                        modelId = "model",
                                        modelDisplayName = "Model",
                                        protocol = "openai-compatible",
                                        status = "COMPLETE",
                                        responseText = "world",
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        StepVerifier.create(response)
            .assertNext { assertEquals("SKIPPED", it.items.single().status) }
            .verifyComplete()
        verify(exactly = 0) { transactions.importConversation(any(), any(), any()) }
    }

    @Test
    fun `frontend digest imports a conversation and repeated sync is idempotent`() {
        val user = com.octopusllm.testsupport.Feature003Fixtures.user()
        val conversation = compatibleConversation()
        val importedSession = ChatSession(user = user, title = conversation.title)
        every { users.findById(user.id) } returns Optional.of(user)
        every { imports.findByUserIdAndSourceConversationId(user.id, conversation.sourceConversationId) } returns null
        every { transactions.importConversation(user.id, conversation, FRONTEND_DIGEST) } returns importedSession

        StepVerifier.create(service.sync(user.id, AnonymousSyncRequest(listOf(conversation))))
            .assertNext { result ->
                assertEquals("IMPORTED", result.items.single().status)
                assertEquals(importedSession.id, result.items.single().sessionId)
            }
            .verifyComplete()

        val existingImport = AnonymousConversationImport(
            user = user,
            sourceConversationId = conversation.sourceConversationId,
            session = importedSession,
            sourceDigest = FRONTEND_DIGEST,
            status = "IMPORTED",
        )
        every { imports.findByUserIdAndSourceConversationId(user.id, conversation.sourceConversationId) } returns existingImport

        StepVerifier.create(service.sync(user.id, AnonymousSyncRequest(listOf(conversation))))
            .assertNext { result ->
                assertEquals("ALREADY_IMPORTED", result.items.single().status)
                assertEquals(importedSession.id, result.items.single().sessionId)
            }
            .verifyComplete()
        verify(exactly = 1) { transactions.importConversation(user.id, conversation, FRONTEND_DIGEST) }
    }

    private fun compatibleConversation() = AnonymousSyncConversationInput(
        sourceConversationId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        sourceDigest = FRONTEND_DIGEST,
        title = "Local",
        createdAt = Instant.parse("2026-09-02T00:00:00.000Z"),
        updatedAt = Instant.parse("2026-09-02T00:01:00.000Z"),
        turns = listOf(
            AnonymousSyncTurnInput(
                sourceTurnId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                clientRequestId = "00000000-0000-0000-0000-000000000003",
                promptText = "hello",
                createdAt = Instant.parse("2026-09-02T00:00:00.000Z"),
                responses = listOf(
                    AnonymousSyncResponseInput(
                        configuredModelId = UUID.fromString("00000000-0000-0000-0000-000000000004"),
                        modelId = "model",
                        modelDisplayName = "Model",
                        protocol = "openai-compatible",
                        status = "COMPLETE",
                        responseText = "world",
                    ),
                ),
            ),
        ),
    )

    private companion object {
        // Generated by frontend anonymousConversationDigest for compatibleConversation().
        const val FRONTEND_DIGEST = "3d3f4fe8e161ae3502d1e7dc75a471b1ab59e9ff59039d8f583a427e80236845"
    }
}
