package com.octopusllm.admin

import com.octopusllm.auth.JwtTokenService
import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import com.octopusllm.testsupport.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

class AdminUserControllerTest @Autowired constructor(
    private val webTestClient: WebTestClient,
    private val userRepository: UserRepository,
    private val jwtTokenService: JwtTokenService,
) : AbstractPostgresIntegrationTest() {

    private val bcrypt = BCryptPasswordEncoder(12)

    private fun newUser(admin: Boolean = false, active: Boolean = false): User =
        userRepository.save(
            User(
                email = "u-${UUID.randomUUID()}@example.com",
                passwordHash = bcrypt.encode("Password123!"),
                emailVerified = true,
                isAdmin = admin,
                isActive = active,
            ),
        )

    private fun adminToken(admin: User) = jwtTokenService.issue(admin.id, admin.sessionEpoch)

    /** Make last-admin assertions deterministic on the shared test database. */
    private fun disableAllExistingAdmins() {
        userRepository.findAll().filter { it.isAdmin && !it.isDisabled }.forEach {
            it.isDisabled = true
            userRepository.save(it)
        }
    }

    @Test
    fun `list never exposes password hashes and is paginated with items`() {
        val admin = newUser(admin = true, active = true)
        newUser()
        webTestClient.get().uri("/api/v2/admin/users?size=5")
            .header("Authorization", "Bearer ${adminToken(admin)}")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.items").isArray
            .jsonPath("$.items[0].email").exists()
            .jsonPath("$.items[0].passwordHash").doesNotExist()
            .jsonPath("$.items[0].password_hash").doesNotExist()
    }

    @Test
    fun `activate is idempotent`() {
        val admin = newUser(admin = true, active = true)
        val target = newUser()
        val token = adminToken(admin)
        repeat(2) {
            webTestClient.post().uri("/api/v2/admin/users/${target.id}/activate")
                .header("Authorization", "Bearer $token")
                .exchange().expectStatus().isOk
                .expectBody().jsonPath("$.isActive").isEqualTo(true)
        }
    }

    @Test
    fun `activate then deactivate toggles isActive and is idempotent`() {
        val admin = newUser(admin = true, active = true)
        val target = newUser(active = true)
        val token = adminToken(admin)
        repeat(2) {
            webTestClient.post().uri("/api/v2/admin/users/${target.id}/deactivate")
                .header("Authorization", "Bearer $token")
                .exchange().expectStatus().isOk
                .expectBody().jsonPath("$.isActive").isEqualTo(false)
        }
        webTestClient.post().uri("/api/v2/admin/users/${target.id}/activate")
            .header("Authorization", "Bearer $token")
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.isActive").isEqualTo(true)
    }

    @Test
    fun `deactivating the only usable admin is refused`() {
        disableAllExistingAdmins()
        val admin = newUser(admin = true, active = true)
        webTestClient.post().uri("/api/v2/admin/users/${admin.id}/deactivate")
            .header("Authorization", "Bearer ${adminToken(admin)}")
            .exchange().expectStatus().isEqualTo(409)
    }

    @Test
    fun `disable then enable preserves the account`() {
        val admin = newUser(admin = true, active = true)
        val target = newUser()
        val token = adminToken(admin)
        webTestClient.post().uri("/api/v2/admin/users/${target.id}/disable")
            .header("Authorization", "Bearer $token")
            .exchange().expectStatus().isOk.expectBody().jsonPath("$.isDisabled").isEqualTo(true)
        webTestClient.post().uri("/api/v2/admin/users/${target.id}/enable")
            .header("Authorization", "Bearer $token")
            .exchange().expectStatus().isOk.expectBody().jsonPath("$.isDisabled").isEqualTo(false)
    }

    @Test
    fun `disabling the only usable admin is refused`() {
        disableAllExistingAdmins()
        val admin = newUser(admin = true, active = true)
        webTestClient.post().uri("/api/v2/admin/users/${admin.id}/disable")
            .header("Authorization", "Bearer ${adminToken(admin)}")
            .exchange().expectStatus().isEqualTo(org.springframework.http.HttpStatus.CONFLICT)
    }

    @Test
    fun `resetting the only usable admin password is refused`() {
        disableAllExistingAdmins()
        val admin = newUser(admin = true, active = true)
        webTestClient.post().uri("/api/v2/admin/users/${admin.id}/reset-password")
            .header("Authorization", "Bearer ${adminToken(admin)}")
            .exchange().expectStatus().isEqualTo(org.springframework.http.HttpStatus.CONFLICT)
    }

    @Test
    fun `disabling a non-last admin when two usable admins exist succeeds`() {
        disableAllExistingAdmins()
        val admin1 = newUser(admin = true, active = true)
        val admin2 = newUser(admin = true, active = true)
        webTestClient.post().uri("/api/v2/admin/users/${admin2.id}/disable")
            .header("Authorization", "Bearer ${adminToken(admin1)}")
            .exchange().expectStatus().isOk
    }
}
