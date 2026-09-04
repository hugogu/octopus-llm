package com.octopusllm.admin

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.UUID

/**
 * Admin site-info and analytics configuration. Lives under the admin-only `/api/v2/admin` path
 * space (gated in SecurityConfig). Persisted to the single-row `site_settings` table by
 * [SiteSettingsService].
 */
@RestController
@RequestMapping("/api/v2/admin/site-settings")
class SiteSettingsController(
    private val service: SiteSettingsService,
) {
    @GetMapping
    fun get(): Mono<SiteSettingsAdminView> =
        Mono.fromCallable { service.get().toAdminView() }.subscribeOn(Schedulers.boundedElastic())

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    fun update(
        @AuthenticationPrincipal principal: String,
        @Valid @RequestBody request: SiteSettingsUpdate,
    ): Mono<SiteSettingsAdminView> =
        Mono.fromCallable {
            service.update(UUID.fromString(principal), request).toAdminView()
        }.subscribeOn(Schedulers.boundedElastic())
}
