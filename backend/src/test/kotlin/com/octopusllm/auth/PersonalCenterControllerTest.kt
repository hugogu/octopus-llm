package com.octopusllm.auth

import com.octopusllm.testsupport.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

class PersonalCenterControllerTest @Autowired constructor(
    private val web: WebTestClient,
    private val users: UserRepository,
    private val jwt: JwtTokenService,
) : AbstractPostgresIntegrationTest() {
    private val bcrypt = BCryptPasswordEncoder(12)

    @Test
    fun `profile update and password change return replacement token and reject stale token`() {
        val user = users.save(
            User(
                email = "profile-${UUID.randomUUID()}@example.com",
                passwordHash = bcrypt.encode("OldPassword123!"),
                emailVerified = true,
            ),
        )
        val oldToken = jwt.issue(user.id, user.sessionEpoch)

        web.patch().uri("/api/v2/me")
            .header("Authorization", "Bearer $oldToken")
            .bodyValue(mapOf("displayName" to "Ada"))
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.displayName").isEqualTo("Ada")

        val replacement = web.post().uri("/api/v2/me/password")
            .header("Authorization", "Bearer $oldToken")
            .bodyValue(mapOf("currentPassword" to "OldPassword123!", "newPassword" to "NewPassword123!"))
            .exchange().expectStatus().isOk
            .expectBody(ChangePasswordResponse::class.java)
            .returnResult().responseBody ?: error("missing replacement token")

        web.get().uri("/api/v2/me")
            .header("Authorization", "Bearer $oldToken")
            .exchange().expectStatus().isUnauthorized

        web.get().uri("/api/v2/me")
            .header("Authorization", "Bearer ${replacement.token}")
            .exchange().expectStatus().isOk
    }
}
