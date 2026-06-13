package com.octopusllm.admin

import com.octopusllm.auth.JwtTokenService
import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import com.octopusllm.testsupport.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class AdminUserDeletionTest @Autowired constructor(
    private val webTestClient: WebTestClient,
    private val userRepository: UserRepository,
    private val jwtTokenService: JwtTokenService,
) : AbstractPostgresIntegrationTest() {

    private val bcrypt = BCryptPasswordEncoder(12)

    private fun newUser(email: String, admin: Boolean = false): User =
        userRepository.save(
            User(
                email = email,
                passwordHash = bcrypt.encode("Password123!"),
                emailVerified = true,
                isAdmin = admin,
                isActive = true,
            ),
        )

    private fun adminToken(admin: User) = jwtTokenService.issue(admin.id, admin.sessionEpoch)

    @Test
    fun `deletes a non-admin account completely`() {
        val admin = newUser("realadmin-${UUID.randomUUID()}@company.io", admin = true)
        val target = newUser("garbage-${UUID.randomUUID()}@example.com")

        webTestClient.delete().uri("/api/v2/admin/users/${target.id}")
            .header("Authorization", "Bearer ${adminToken(admin)}")
            .exchange().expectStatus().isNoContent

        assertFalse(userRepository.findById(target.id).isPresent, "user should be hard-deleted")
    }

    @Test
    fun `refuses to delete an administrator`() {
        val admin = newUser("admin1-${UUID.randomUUID()}@company.io", admin = true)
        val other = newUser("admin2-${UUID.randomUUID()}@company.io", admin = true)

        webTestClient.delete().uri("/api/v2/admin/users/${other.id}")
            .header("Authorization", "Bearer ${adminToken(admin)}")
            .exchange().expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)

        assertTrue(userRepository.findById(other.id).isPresent, "admin must not be deleted")
    }

    @Test
    fun `flags suspected test accounts and lists them with testOnly`() {
        val admin = newUser("flagadmin-${UUID.randomUUID()}@company.io", admin = true)
        val marker = UUID.randomUUID().toString()
        val garbage = newUser("garbage-$marker@example.com")
        val real = newUser("person-$marker@company.io")

        // the garbage account is flagged, the real one is not
        webTestClient.get().uri("/api/v2/admin/users?q=$marker&size=50")
            .header("Authorization", "Bearer ${adminToken(admin)}")
            .exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.items[?(@.id == '${garbage.id}')].suspectedTest").isEqualTo(true)
            .jsonPath("$.items[?(@.id == '${real.id}')].suspectedTest").isEqualTo(false)

        // testOnly excludes the real account
        webTestClient.get().uri("/api/v2/admin/users?testOnly=true&size=100")
            .header("Authorization", "Bearer ${adminToken(admin)}")
            .exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.items[?(@.id == '${real.id}')]").doesNotExist()
    }

    @Test
    fun `purge deletes test accounts and keeps admins and real users`() {
        val admin = newUser("keepadmin-${UUID.randomUUID()}@company.io", admin = true)
        val realUser = newUser("keepuser-${UUID.randomUUID()}@company.io")
        val garbage1 = newUser("g1-${UUID.randomUUID()}@example.com")
        val garbage2 = newUser("g2-${UUID.randomUUID()}@example.org")

        webTestClient.post().uri("/api/v2/admin/users/purge-test")
            .header("Authorization", "Bearer ${adminToken(admin)}")
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.deleted").value<Int> { assertTrue(it >= 2) }

        assertFalse(userRepository.findById(garbage1.id).isPresent)
        assertFalse(userRepository.findById(garbage2.id).isPresent)
        assertTrue(userRepository.findById(admin.id).isPresent, "admin must survive purge")
        assertTrue(userRepository.findById(realUser.id).isPresent, "real user must survive purge")
    }
}
