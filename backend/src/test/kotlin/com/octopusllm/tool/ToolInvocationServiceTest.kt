package com.octopusllm.tool

import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import com.octopusllm.chat.ChatSession
import com.octopusllm.chat.ChatSessionRepository
import com.octopusllm.chat.ChatTurn
import com.octopusllm.chat.ChatTurnRepository
import com.octopusllm.chat.ProviderResponse
import com.octopusllm.chat.ProviderResponseRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ToolInvocationServiceTest {

    @Autowired private lateinit var invocations: ToolInvocationRepository
    @Autowired private lateinit var joins: ProviderResponseToolInvocationRepository
    @Autowired private lateinit var turnRepository: ChatTurnRepository
    @Autowired private lateinit var sessionRepository: ChatSessionRepository
    @Autowired private lateinit var responseRepository: ProviderResponseRepository
    @Autowired private lateinit var userRepository: UserRepository

    private val service by lazy { ToolInvocationService(invocations, joins) }

    companion object {
        @Container
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    private fun newTurn(): ChatTurn {
        val user = userRepository.save(
            User(email = "svc-${UUID.randomUUID()}@example.com", passwordHash = "h", emailVerified = true),
        )
        val session = sessionRepository.save(ChatSession(user = user))
        return turnRepository.save(
            ChatTurn(session = session, sequenceNum = 1, promptText = "hi", selectedModelIds = arrayOf("m1")),
        )
    }

    private fun response(turn: ChatTurn) = responseRepository.save(
        ProviderResponse(
            turn = turn, modelId = "m1", configuredModelId = UUID.randomUUID(),
            modelDisplayName = "Model 1", protocol = "openai-compatible", status = "complete", latencyMs = 10,
        ),
    )

    @Test
    fun `records a success once and reuses it for identical arguments`() {
        val turn = newTurn()
        val args = mapOf("symbol" to "600519")

        val first = service.record(turn.session.id, turn.id, "stock_quote", args, ToolResult.Success(mapOf("price" to 1680.5)))
        val second = service.record(turn.session.id, turn.id, "stock_quote", args, ToolResult.Success(mapOf("price" to 1680.5)))

        assertEquals(first.id, second.id)
        assertEquals("success", first.status)
        assertEquals(1680.5, (first.result?.get("price") as Number).toDouble())
        assertEquals(1, invocations.findByQuestIdAndTurnId(turn.session.id, turn.id).size)
    }

    @Test
    fun `records a failure with its status and error message`() {
        val turn = newTurn()

        val invocation = service.record(
            turn.session.id, turn.id, "weather", mapOf("city" to "上海"),
            ToolResult.Failure("provider 503 after retry", timedOut = false),
        )

        assertEquals("failed", invocation.status)
        assertEquals("provider 503 after retry", invocation.errorMessage)
        assertNull(invocation.result)
    }

    @Test
    fun `links one shared invocation to multiple responses and is idempotent`() {
        val turn = newTurn()
        val invocation = service.record(
            turn.session.id, turn.id, "stock_quote", mapOf("symbol" to "600519"),
            ToolResult.Success(mapOf("price" to 1680.5)),
        )
        val responseA = response(turn)
        val responseB = response(turn)

        service.link(responseA.id, invocation.id)
        service.link(responseA.id, invocation.id) // idempotent — no second row
        service.link(responseB.id, invocation.id)

        assertEquals(2, joins.findByToolInvocationId(invocation.id).size)
        assertEquals(1, joins.findByProviderResponseId(responseA.id).size)
    }

    @Test
    fun `loads invocations grouped by response for history rendering`() {
        val turn = newTurn()
        val invocation = service.record(
            turn.session.id, turn.id, "stock_quote", mapOf("symbol" to "600519"),
            ToolResult.Success(mapOf("price" to 1680.5)),
        )
        val responseA = response(turn)
        val responseB = response(turn)
        service.link(responseA.id, invocation.id)

        val byResponse = service.invocationsByResponse(listOf(responseA.id, responseB.id))

        assertEquals("stock_quote", byResponse[responseA.id]?.single()?.toolName)
        assertEquals(null, byResponse[responseB.id]) // no tools linked to B
        assertEquals(emptyMap<Any, Any>(), service.invocationsByResponse(emptyList()))
    }
}
