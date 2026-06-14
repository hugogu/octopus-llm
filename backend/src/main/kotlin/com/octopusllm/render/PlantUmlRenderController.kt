package com.octopusllm.render

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

/**
 * Same-origin render proxy for PlantUML (contract: contracts/plantuml-render.md). Unauthenticated so it
 * can serve anonymous share-page visitors, but hardened: a request-size cap and a fixed upstream URL
 * (no SSRF). Returns `image/svg+xml`; renderer failures map to 502 so the frontend can fall back to the
 * source view (FR-006a).
 */
@RestController
@RequestMapping("/api/v2/render")
class PlantUmlRenderController(
    private val service: PlantUmlRenderService,
    private val objectMapper: ObjectMapper,
    @Value("\${app.render.plantuml.max-source-bytes}") private val maxSourceBytes: Int,
) {
    private val svgMediaType = MediaType.valueOf("image/svg+xml")

    @PostMapping(
        "/plantuml",
        consumes = [MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE],
        produces = ["image/svg+xml"],
    )
    fun render(
        @RequestBody body: String,
        @RequestHeader(name = "Content-Type", required = false) contentType: String?,
    ): Mono<ResponseEntity<String>> {
        val source = extractSource(body, contentType)
        if (source.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "PlantUML source is empty")
        }
        if (source.toByteArray(Charsets.UTF_8).size > maxSourceBytes) {
            throw ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "PlantUML source exceeds the size limit")
        }
        return service.renderSvg(source)
            .map { svg -> ResponseEntity.ok().contentType(svgMediaType).body(svg) }
            .onErrorMap { cause ->
                if (cause is ResponseStatusException) cause
                else ResponseStatusException(HttpStatus.BAD_GATEWAY, "PlantUML renderer unavailable")
            }
    }

    private fun extractSource(body: String, contentType: String?): String {
        if (contentType != null && contentType.contains("application/json", ignoreCase = true)) {
            return runCatching {
                objectMapper.readTree(body).path("source").asText("")
            }.getOrElse {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON body")
            }
        }
        return body
    }
}
