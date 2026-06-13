package com.octopusllm.admin

import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import com.octopusllm.testsupport.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class LastAdminConcurrencyTest @Autowired constructor(
    private val adminUserService: AdminUserService,
    private val userRepository: UserRepository,
) : AbstractPostgresIntegrationTest() {

    private val bcrypt = BCryptPasswordEncoder(12)

    private fun newAdmin(): User =
        userRepository.save(
            User(
                email = "adm-${UUID.randomUUID()}@example.com",
                passwordHash = bcrypt.encode("Password123!"),
                emailVerified = true,
                isAdmin = true,
                isActive = true,
            ),
        )

    @Test
    fun `two concurrent disables on the last two admins leave exactly one usable admin`() {
        // Ensure these are the only two usable admins in the database for this assertion.
        userRepository.findAll().filter { it.isAdmin && !it.isDisabled }.forEach {
            it.isDisabled = true
            userRepository.save(it)
        }
        val admin1 = newAdmin()
        val admin2 = newAdmin()

        val pool = Executors.newFixedThreadPool(2)
        val tasks = listOf(
            Callable { runCatching { adminUserService.disable(admin2.id, admin1.id).block() }.exceptionOrNull() },
            Callable { runCatching { adminUserService.disable(admin1.id, admin2.id).block() }.exceptionOrNull() },
        )
        val errors = pool.invokeAll(tasks).map { it.get() }
        pool.shutdown()

        val refusals = errors.filterIsInstance<ResponseStatusException>()
            .count { it.statusCode.value() == 409 }

        assertEquals(1, refusals, "exactly one disable must be refused")
        assertEquals(1L, userRepository.countByIsAdminTrueAndIsDisabledFalse(), "one enabled admin must remain")
        assertTrue(userRepository.countUsableAdmins() >= 1, "at least one usable admin must remain")
    }
}
