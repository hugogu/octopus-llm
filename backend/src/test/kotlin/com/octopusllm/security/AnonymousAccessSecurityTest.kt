package com.octopusllm.security

import com.octopusllm.testsupport.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

class AnonymousAccessSecurityTest : AbstractPostgresIntegrationTest() {
    @Autowired
    private lateinit var client: WebTestClient

    @Test
    fun `only catalogue and chat are public and API responses are not cached`() {
        client.get().uri("/api/v2/anonymous/models")
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals("Cache-Control", "no-store")

        client.post().uri("/api/v2/anonymous/conversations/sync")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"conversations\":[]}")
            .exchange()
            .expectStatus().isUnauthorized

        client.get().uri("/api/v2/chat/sessions")
            .exchange()
            .expectStatus().isUnauthorized
    }
}
