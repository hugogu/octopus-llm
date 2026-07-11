package com.octopusllm.analytics

import com.octopusllm.testsupport.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient

class PublicAnalyticsControllerTest @Autowired constructor(
    private val web: WebTestClient,
) : AbstractPostgresIntegrationTest() {
    @Test
    fun `public analytics is unauthenticated bounded and excludes prohibited fields`() {
        val body = web.get().uri("/api/v2/analytics/public/by-model?page=0&size=25")
            .exchange().expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult().responseBody.orEmpty()

        listOf(
            "userId",
            "sessionId",
            "clientIp",
            "connectionId",
            "configuredModelId",
            "promptText",
            "responseText",
        ).forEach { prohibited ->
            check(!body.contains(prohibited)) { "public payload leaked $prohibited" }
        }

        web.get().uri("/api/v2/analytics/public/by-model?page=0&size=301")
            .exchange().expectStatus().isBadRequest
    }
}
