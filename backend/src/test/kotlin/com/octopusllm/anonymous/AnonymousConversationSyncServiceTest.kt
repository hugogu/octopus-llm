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
}
