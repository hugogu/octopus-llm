package com.octopusllm.admin

import com.octopusllm.auth.JwtTokenService
import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import com.octopusllm.testsupport.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

class SiteSettingsControllerTest @Autowired constructor(
    private val web: WebTestClient,
    private val users: UserRepository,
    private val jwt: JwtTokenService,
    private val siteSettings: SiteSettingsService,
) : AbstractPostgresIntegrationTest() {

    // site_settings is a single shared row; blank it after each test so other tests see defaults.
    @org.junit.jupiter.api.AfterEach
    fun resetToBlank() {
        val admin = users.save(
            User(email = "site-reset-${UUID.randomUUID()}@example.com", passwordHash = "hash", isAdmin = true, isActive = true),
        )
        siteSettings.update(
            admin.id,
            SiteSettingsUpdate(
                siteName = "  ",
                footerText = null,
                chinaFilingEnabled = false,
                icpRecordNo = "  ",
                policeRecordNo = "  ",
                googleAnalyticsMeasurementId = "  ",
            ),
        )
    }

    private fun adminToken(): String {
        val admin = users.save(
            User(email = "site-${UUID.randomUUID()}@example.com", passwordHash = "hash", isAdmin = true, isActive = true),
        )
        return jwt.issue(admin.id, admin.sessionEpoch)
    }

    private fun nonexemptUser(): String {
        val u = users.save(
            User(email = "site-u-${UUID.randomUUID()}@example.com", passwordHash = "hash", isAdmin = false, isActive = true),
        )
        return jwt.issue(u.id, u.sessionEpoch)
    }

    @Test
    fun `admin can read and write site settings`() {
        web.get().uri("/api/v2/admin/site-settings")
            .header("Authorization", "Bearer ${adminToken()}")
            .exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.siteName").doesNotExist()
            .jsonPath("$.chinaFilingEnabled").isEqualTo(false)
            .jsonPath("$.icpRecordNo").doesNotExist()

        web.put().uri("/api/v2/admin/site-settings")
            .header("Authorization", "Bearer ${adminToken()}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "siteName" to "  ",
                    "footerText" to "  © Octopus LLM  ",
                    "chinaFilingEnabled" to true,
                    "icpRecordNo" to " 京ICP备12345678号-1  ",
                    "policeRecordNo" to " 京公网安备11010102000001号  ",
                    "googleAnalyticsMeasurementId" to " G-ABC123  ",
                ),
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.siteName").doesNotExist()
            .jsonPath("$.footerText").isEqualTo("© Octopus LLM")
            .jsonPath("$.chinaFilingEnabled").isEqualTo(true)
            .jsonPath("$.icpRecordNo").isEqualTo("京ICP备12345678号-1")
            .jsonPath("$.policeRecordNo").isEqualTo("京公网安备11010102000001号")
            .jsonPath("$.googleAnalyticsMeasurementId").isEqualTo("G-ABC123")
            .jsonPath("$.updatedAt").exists()
            .jsonPath("$.updatedBy").exists()
    }

    @Test
    fun `admin endpoint rejects non-admin token`() {
        web.get().uri("/api/v2/admin/site-settings")
            .header("Authorization", "Bearer ${nonexemptUser()}")
            .exchange()
            .expectStatus().isEqualTo(403)

        web.put().uri("/api/v2/admin/site-settings")
            .header("Authorization", "Bearer ${nonexemptUser()}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("siteName" to "hijack"))
            .exchange()
            .expectStatus().isEqualTo(403)
    }

    @Test
    fun `public endpoint is anonymous and only returns the safe shape`() {
        val token = adminToken()
        web.put().uri("/api/v2/admin/site-settings")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "siteName" to "Octopus",
                    "footerText" to "© Octopus",
                    "chinaFilingEnabled" to true,
                    "icpRecordNo" to "京ICP备12345678号",
                    "policeRecordNo" to "京公网安备11010102000001号",
                    "googleAnalyticsMeasurementId" to "G-SITE123",
                ),
            )
            .exchange()
            .expectStatus().isOk

        web.get().uri("/api/v2/site-settings")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.siteName").isEqualTo("Octopus")
            .jsonPath("$.footerText").isEqualTo("© Octopus")
            .jsonPath("$.chinaFilingEnabled").isEqualTo(true)
            .jsonPath("$.icpRecordNo").isEqualTo("京ICP备12345678号")
            .jsonPath("$.policeRecordNo").isEqualTo("京公网安备11010102000001号")
            .jsonPath("$.googleAnalyticsMeasurementId").isEqualTo("G-SITE123")
            .jsonPath("$.updatedAt").doesNotExist()
            .jsonPath("$.updatedBy").doesNotExist()
    }

    @Test
    fun `blank and whitespace-only values are normalized to null`() {
        web.put().uri("/api/v2/admin/site-settings")
            .header("Authorization", "Bearer ${adminToken()}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("siteName" to "   ", "footerText" to "\t\n", "icpRecordNo" to "", "policeRecordNo" to "    ", "googleAnalyticsMeasurementId" to "  "))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.siteName").doesNotExist()
            .jsonPath("$.footerText").doesNotExist()
            .jsonPath("$.chinaFilingEnabled").isEqualTo(false)
            .jsonPath("$.icpRecordNo").doesNotExist()
            .jsonPath("$.policeRecordNo").doesNotExist()
            .jsonPath("$.googleAnalyticsMeasurementId").doesNotExist()
    }

    @Test
    fun `rejects an invalid Google Analytics Measurement ID`() {
        web.put().uri("/api/v2/admin/site-settings")
            .header("Authorization", "Bearer ${adminToken()}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("googleAnalyticsMeasurementId" to "UA-12345"))
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `enabling Chinese filing requires an ICP number and disabled records stay private`() {
        val token = adminToken()

        web.put().uri("/api/v2/admin/site-settings")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("chinaFilingEnabled" to true))
            .exchange()
            .expectStatus().isBadRequest

        web.put().uri("/api/v2/admin/site-settings")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "chinaFilingEnabled" to false,
                    "icpRecordNo" to "京ICP备12345678号",
                    "policeRecordNo" to "京公网安备11010102000001号",
                ),
            )
            .exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.chinaFilingEnabled").isEqualTo(false)

        web.get().uri("/api/v2/site-settings")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.chinaFilingEnabled").isEqualTo(false)
            .jsonPath("$.icpRecordNo").doesNotExist()
            .jsonPath("$.policeRecordNo").doesNotExist()
    }
}
