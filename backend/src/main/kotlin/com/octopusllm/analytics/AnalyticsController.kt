package com.octopusllm.analytics

import com.octopusllm.api.v2.PageResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v2/analytics")
class AnalyticsController(private val service: AnalyticsService) {
    @GetMapping("/summary")
    fun summary(
        @AuthenticationPrincipal principal: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam configuredModelId: UUID?,
    ) = service.summary(UUID.fromString(principal), from, to, configuredModelId)

    @GetMapping("/by-model")
    fun byModel(
        @AuthenticationPrincipal principal: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam configuredModelId: UUID?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): PageResponse<Map<String, Any?>> =
        service.byModel(UUID.fromString(principal), from, to, configuredModelId, page, size)

    @GetMapping("/by-session")
    fun bySession(
        @AuthenticationPrincipal principal: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam configuredModelId: UUID?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): PageResponse<Map<String, Any?>> =
        service.bySession(UUID.fromString(principal), from, to, configuredModelId, page, size)

    @GetMapping("/responses")
    fun responses(
        @AuthenticationPrincipal principal: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam configuredModelId: UUID?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<Map<String, Any?>> =
        service.responses(UUID.fromString(principal), from, to, configuredModelId, page, size)

    @GetMapping("/public/by-model")
    fun publicByModel(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam protocol: String?,
        @RequestParam modelId: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): PageResponse<Map<String, Any?>> =
        service.publicByModel(from, to, protocol, modelId, page, size)
}
