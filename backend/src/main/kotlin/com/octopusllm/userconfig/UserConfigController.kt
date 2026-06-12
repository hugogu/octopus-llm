package com.octopusllm.userconfig

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.util.UUID

data class PreferencesResponse(
    val lastSelectedConfiguredModelId: UUID?,
    val themePreference: String,
    val sidebarCollapsed: Boolean,
)

data class UpdatePreferencesRequest(
    val lastSelectedConfiguredModelId: UUID? = null,
    val themePreference: String? = null,
    val sidebarCollapsed: Boolean? = null,
)

@RestController
@RequestMapping("/api/v2/user/preferences")
class UserConfigController(private val service: UserConfigService) {
    @GetMapping
    fun get(@AuthenticationPrincipal principal: String): Mono<PreferencesResponse> =
        service.getPreferences(userId(principal)).map(::response)

    @PutMapping
    fun put(
        @AuthenticationPrincipal principal: String,
        @RequestBody request: UpdatePreferencesRequest,
    ): Mono<PreferencesResponse> =
        service.updatePreferences(
            userId(principal),
            request.lastSelectedConfiguredModelId,
            request.themePreference,
            request.sidebarCollapsed,
            updateLastSelectedConfiguredModelId = true,
        ).map(::response)

    @PatchMapping
    fun patch(
        @AuthenticationPrincipal principal: String,
        @RequestBody request: UpdatePreferencesRequest,
    ): Mono<PreferencesResponse> =
        service.updatePreferences(
            userId(principal),
            request.lastSelectedConfiguredModelId,
            request.themePreference,
            request.sidebarCollapsed,
            updateLastSelectedConfiguredModelId = request.lastSelectedConfiguredModelId != null,
        ).map(::response)

    private fun response(preference: UserPreference) = PreferencesResponse(
        preference.lastSelectedConfiguredModelId,
        preference.themePreference,
        preference.sidebarCollapsed,
    )

    private fun userId(principal: String) = UUID.fromString(principal)
}
