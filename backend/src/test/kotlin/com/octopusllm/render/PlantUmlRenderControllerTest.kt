package com.octopusllm.render

import com.ninjasquad.springmockk.MockkBean
import com.octopusllm.testsupport.AbstractPostgresIntegrationTest
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono

/**
 * Contract test for the public PlantUML render proxy. The upstream PlantUML server is mocked so the
 * test exercises the endpoint contract (size cap, empty guard, success, renderer-unavailable mapping)
 * without a real renderer.
 */
@TestPropertySource(properties = ["app.render.plantuml.max-source-bytes=20"])
class PlantUmlRenderControllerTest @Autowired constructor(
    private val web: WebTestClient,
) : AbstractPostgresIntegrationTest() {

    @MockkBean
    private lateinit var service: PlantUmlRenderService

    @Test
    fun `renders svg for valid source without authentication`() {
        every { service.renderSvg(any()) } returns Mono.just("<svg>ok</svg>")

        web.post().uri("/api/v2/render/plantuml")
            .header("Content-Type", "text/plain")
            .bodyValue("A->B")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith("image/svg+xml")
            .expectBody(String::class.java).isEqualTo("<svg>ok</svg>")
    }

    @Test
    fun `rejects empty source with 400`() {
        web.post().uri("/api/v2/render/plantuml")
            .header("Content-Type", "text/plain")
            .bodyValue("   ")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `rejects oversize source with 413`() {
        web.post().uri("/api/v2/render/plantuml")
            .header("Content-Type", "text/plain")
            .bodyValue("this source is definitely longer than twenty bytes")
            .exchange()
            .expectStatus().isEqualTo(413)
    }

    @Test
    fun `maps renderer failure to 502`() {
        every { service.renderSvg(any()) } returns Mono.error(RuntimeException("connection refused"))

        web.post().uri("/api/v2/render/plantuml")
            .header("Content-Type", "text/plain")
            .bodyValue("A->B")
            .exchange()
            .expectStatus().isEqualTo(502)
    }
}
