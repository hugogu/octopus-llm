package com.octopusllm.userconfig

import com.octopusllm.auth.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant
import java.util.UUID

@Service
class UserConfigService(
    private val preferenceRepository: UserPreferenceRepository,
    private val userRepository: UserRepository,
) {
    fun getPreferences(userId: UUID): Mono<UserPreference> =
        blocking {
            preferenceRepository.findByUserId(userId) ?: createPreference(userId)
        }

    fun updatePreferences(
        userId: UUID,
        lastSelectedConfiguredModelId: UUID?,
        themePreference: String?,
        sidebarCollapsed: Boolean?,
        updateLastSelectedConfiguredModelId: Boolean = false,
    ): Mono<UserPreference> =
        blocking {
            val preference = preferenceRepository.findByUserId(userId) ?: createPreference(userId)
            if (updateLastSelectedConfiguredModelId || lastSelectedConfiguredModelId != null) {
                preference.lastSelectedConfiguredModelId = lastSelectedConfiguredModelId
            }
            if (themePreference != null) {
                if (themePreference !in setOf("system", "light", "dark")) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid theme preference")
                }
                preference.themePreference = themePreference
            }
            if (sidebarCollapsed != null) preference.sidebarCollapsed = sidebarCollapsed
            preference.updatedAt = Instant.now()
            preferenceRepository.save(preference)
        }

    private fun createPreference(userId: UUID): UserPreference {
        val user = userRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        return preferenceRepository.save(UserPreference(user = user))
    }

    private fun <T> blocking(block: () -> T): Mono<T> =
        Mono.fromCallable(block).subscribeOn(Schedulers.boundedElastic())
}
