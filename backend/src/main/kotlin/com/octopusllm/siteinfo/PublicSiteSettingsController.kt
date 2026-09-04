package com.octopusllm.siteinfo

import com.octopusllm.admin.SiteSettings
import com.octopusllm.admin.SiteSettingsPublicView
import com.octopusllm.admin.SiteSettingsService
import com.octopusllm.admin.toPublicView
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Public site-info endpoint used by the frontend footer and analytics loader. Unauthenticated
 * (whitelisted in SecurityConfig) so these shared site features work before any token exists.
 * Returns only the safe public shape — no audit metadata.
 */
@RestController
@RequestMapping("/api/v2/site-settings")
class PublicSiteSettingsController(
    private val service: SiteSettingsService,
) {
    @GetMapping
    fun get(): Mono<SiteSettingsPublicView> =
        Mono.fromCallable { service.get().toPublicView() }.subscribeOn(Schedulers.boundedElastic())
}
