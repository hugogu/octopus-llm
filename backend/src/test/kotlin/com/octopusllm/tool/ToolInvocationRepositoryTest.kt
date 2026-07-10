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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ToolInvocationRepositoryTest {

    @Autowired private lateinit var toolInvocations: ToolInvocationRepository
    @Autowired private lateinit var joins: ProviderResponseToolInvocationRepository
    @Autowired private lateinit var turnRepository: ChatTurnRepository
    @Autowired private lateinit var sessionRepository: ChatSessionRepository
    @Autowired private lateinit var responseRepository: ProviderResponseRepository
    @Autowired private lateinit var userRepository: UserRepository

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
            User(email = "tool-${UUID.randomUUID()}@example.com", passwordHash = "h", emailVerified = true),
        )
        val session = sessionRepository.save(ChatSession(user = user))
        return turnRepository.save(
            ChatTurn(session = session, sequenceNum = 1, promptText = "hi", selectedModelIds = arrayOf("m1")),
        )
    }

    private fun response(turn: ChatTurn): ProviderResponse = responseRepository.save(
        ProviderResponse(
            turn = turn,
            modelId = "m1",
            configuredModelId = UUID.randomUUID(),
            modelDisplayName = "Model 1",
            protocol = "openai-compatible",
            status = "complete",
            latencyMs = 10,
        ),
    )

    @Test
    fun `persists an invocation and finds it by dedup key`() {
        val turn = newTurn()
        val saved = toolInvocations.save(
            ToolInvocation(
                questId = turn.session.id,
                turnId = turn.id,
                toolName = "stock_quote",
                argumentsHash = ToolArguments.hash(mapOf("symbol" to "600519")),
                arguments = mapOf("symbol" to "600519"),
                result = mapOf("price" to 1680.5),
                status = ToolInvocationStatus.SUCCESS.value,
            ),
        )

        val found = toolInvocations.findByQuestIdAndTurnIdAndToolNameAndArgumentsHash(
            turn.session.id, turn.id, "stock_quote", saved.argumentsHash,
        )
        assertEquals(saved.id, found?.id)
        assertEquals(1680.5, (found?.result?.get("price") as Number).toDouble())
    }

    @Test
    fun `enforces per-turn deduplication via the unique constraint`() {
        val turn = newTurn()
        val hash = ToolArguments.hash(mapOf("symbol" to "600519"))
        toolInvocations.saveAndFlush(
            ToolInvocation(
                questId = turn.session.id, turnId = turn.id, toolName = "stock_quote",
                argumentsHash = hash, arguments = mapOf("symbol" to "600519"),
            ),
        )

        assertThrows(DataIntegrityViolationException::class.java) {
            toolInvocations.saveAndFlush(
                ToolInvocation(
                    questId = turn.session.id, turnId = turn.id, toolName = "stock_quote",
                    argumentsHash = hash, arguments = mapOf("symbol" to "600519"),
                ),
            )
        }
    }

    @Test
    fun `a single invocation links to multiple provider responses`() {
        val turn = newTurn()
        val invocation = toolInvocations.save(
            ToolInvocation(
                questId = turn.session.id, turnId = turn.id, toolName = "stock_quote",
                argumentsHash = ToolArguments.hash(mapOf("symbol" to "600519")),
                arguments = mapOf("symbol" to "600519"), status = ToolInvocationStatus.SUCCESS.value,
            ),
        )
        val responseA = response(turn)
        val responseB = response(turn)
        joins.save(ProviderResponseToolInvocation(providerResponseId = responseA.id, toolInvocationId = invocation.id))
        joins.save(ProviderResponseToolInvocation(providerResponseId = responseB.id, toolInvocationId = invocation.id))

        assertEquals(2, joins.findByToolInvocationId(invocation.id).size)
        assertEquals(invocation.id, joins.findByProviderResponseId(responseA.id).single().toolInvocationId)
    }
}
