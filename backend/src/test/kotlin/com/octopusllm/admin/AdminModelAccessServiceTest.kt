package com.octopusllm.admin

import com.octopusllm.auth.UserRepository
import com.octopusllm.connection.ConfiguredModel
import com.octopusllm.connection.ConfiguredModelRepository
import com.octopusllm.connection.Connection
import com.octopusllm.testsupport.Feature003Fixtures
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional

class AdminModelAccessServiceTest {
    private val configuredModelRepository = mockk<ConfiguredModelRepository>()
    private val userRepository = mockk<UserRepository>()
    private val operationRepository = mockk<AdminModelBulkOperationRepository>()
    private val itemRepository = mockk<AdminModelBulkOperationItemRepository>()
    private val auditService = mockk<AdminAuditService>(relaxed = true)

    @Test
    fun `a failed item is recorded and the bulk operation reaches a terminal state`() {
        val admin = Feature003Fixtures.user("bulk-operation-test@example.com")
        val connection = Connection(
            user = admin,
            protocol = "openai-compatible",
            label = "Built-in",
            baseUrl = "https://example.com/v1",
            encryptedKey = byteArrayOf(1),
            keyIv = byteArrayOf(2),
            isBuiltin = true,
        )
        val model = ConfiguredModel(
            user = admin,
            connection = connection,
            modelId = "failing-model",
            displayName = "Failing model",
        )
        val operation = AdminModelBulkOperation(
            adminUser = admin,
            action = "ALLOW_ANONYMOUS",
            selectionMode = "IDS",
            status = "PREVIEWED",
            targetCount = 1,
            expiresAt = Instant.now().plusSeconds(60),
        )
        val item = AdminModelBulkOperationItem(
            operationId = operation.id,
            configuredModelId = model.id,
            modelIdSnapshot = model.modelId,
            displayNameSnapshot = model.displayName,
        )
        every { operationRepository.findByIdAndAdminUserId(operation.id, admin.id) } returns operation
        every { operationRepository.save(any()) } answers { firstArg() }
        every { itemRepository.findByOperationIdOrderByDisplayNameSnapshotAsc(operation.id) } returns listOf(item)
        every { itemRepository.save(any()) } answers { firstArg() }
        every { configuredModelRepository.findById(model.id) } returns Optional.of(model)
        every { configuredModelRepository.save(model) } throws IllegalStateException("database failure")

        val service = AdminModelAccessService(
            configuredModelRepository,
            userRepository,
            operationRepository,
            itemRepository,
            auditService,
            maxTargets = 10,
        )

        val result = service.execute(admin.id, operation.id, "idempotency-key").block()!!

        assertEquals("PARTIAL_FAILURE", result.status)
        assertEquals(1, result.failedCount)
        assertEquals("FAILED", item.outcome)
        assertEquals("MODEL_OPERATION_FAILED", item.errorCode)
        verify(exactly = 1) { itemRepository.save(item) }
    }
}
