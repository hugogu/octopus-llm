package com.octopusllm.chat

import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class DialogRedactionRepositoryTest {

    @Autowired private lateinit var redactionRepository: DialogRedactionRepository
    @Autowired private lateinit var turnRepository: ChatTurnRepository
    @Autowired private lateinit var sessionRepository: ChatSessionRepository
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
        val user = userRepository.save(User(email = "redact-${java.util.UUID.randomUUID()}@example.com", passwordHash = "h", emailVerified = true))
        val session = sessionRepository.save(ChatSession(user = user))
        return turnRepository.save(
            ChatTurn(session = session, sequenceNum = 1, promptText = "hi", selectedModelIds = arrayOf("m1")),
        )
    }

    @Test
    fun `turn redaction is found by turn id and scope`() {
        val turn = newTurn()
        redactionRepository.save(DialogRedaction(scope = DialogRedaction.SCOPE_TURN, turnId = turn.id))

        assertTrue(redactionRepository.existsByScopeAndTurnId(DialogRedaction.SCOPE_TURN, turn.id))
        val rows = redactionRepository.findByTurnIdIn(listOf(turn.id))
        assertEquals(1, rows.size)
        assertEquals(DialogRedaction.SCOPE_TURN, rows.single().scope)
    }

    @Test
    fun `absent redaction reports false`() {
        val turn = newTurn()
        assertFalse(redactionRepository.existsByScopeAndTurnId(DialogRedaction.SCOPE_TURN, turn.id))
        assertTrue(redactionRepository.findByTurnIdIn(listOf(turn.id)).isEmpty())
    }
}
