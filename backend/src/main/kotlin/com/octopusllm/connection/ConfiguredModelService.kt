package com.octopusllm.connection

import com.octopusllm.api.v2.boundedPageRequest
import com.octopusllm.auth.UserRepository
import com.octopusllm.model.ProtocolDefinitions
import org.springframework.data.domain.Page
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant
import java.util.UUID

@Service
class ConfiguredModelService(
    private val userRepository: UserRepository,
    private val connectionService: ConnectionService,
    private val repository: ConfiguredModelRepository,
) {
    fun list(userId: UUID, enabled: Boolean?, page: Int, size: Int): Mono<Page<ConfiguredModel>> =
        blocking {
            val pageable = boundedPageRequest(
                page,
                size,
                Sort.Order.asc("sortOrder"),
                Sort.Order.asc("createdAt"),
                Sort.Order.asc("id"),
            )
            if (enabled == null) repository.findOwnedOrAllocated(userId, pageable)
            else repository.findOwnedOrAllocatedByEnabled(userId, enabled, pageable)
        }

    fun add(
        userId: UUID,
        connectionId: UUID,
        modelId: String,
        displayName: String,
        capabilityOverrides: Map<String, Any?>,
        customParams: Map<String, Any?>,
        isEnabled: Boolean,
    ): Mono<ConfiguredModel> = blocking {
        val user = userRepository.findById(userId).orElseThrow { notFound() }
        val connection = connectionService.requireOwned(userId, connectionId)
        validateCapabilities(connection, capabilityOverrides)
        repository.save(
            ConfiguredModel(
                user = user,
                connection = connection,
                modelId = requiredText(modelId, "modelId"),
                displayName = requiredText(displayName, "displayName"),
                capabilityOverrides = capabilityOverrides,
                customParams = customParams,
                isEnabled = isEnabled,
                sortOrder = repository.countByConnectionId(connectionId).toInt(),
            ),
        )
    }

    fun patch(
        userId: UUID,
        id: UUID,
        displayName: String?,
        isEnabled: Boolean?,
        capabilityOverrides: Map<String, Any?>?,
        customParams: Map<String, Any?>?,
        sortOrder: Int?,
    ): Mono<ConfiguredModel> = blocking {
        val model = requireOwned(userId, id)
        if (displayName != null) model.displayName = requiredText(displayName, "displayName")
        if (isEnabled != null) model.isEnabled = isEnabled
        if (capabilityOverrides != null) {
            model.capabilityOverrides = mergePatch(model.capabilityOverrides, capabilityOverrides)
            validateCapabilities(model.connection, model.capabilityOverrides)
        }
        if (customParams != null) model.customParams = mergePatch(model.customParams, customParams)
        if (sortOrder != null) {
            if (sortOrder < 0) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "sortOrder must not be negative")
            model.sortOrder = sortOrder
        }
        model.updatedAt = Instant.now()
        repository.save(model)
    }

    fun delete(userId: UUID, id: UUID): Mono<Unit> =
        blocking {
            repository.delete(requireOwned(userId, id))
            Unit
        }

    fun requireOwned(userId: UUID, id: UUID): ConfiguredModel =
        repository.findByIdAndUserId(id, userId) ?: throw notFound()

    fun requireOwned(userId: UUID, ids: List<UUID>): List<ConfiguredModel> {
        if (ids.distinct().size != ids.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate configured model IDs are not allowed")
        }
        val models = repository.findByIdInAndUserId(ids, userId).associateBy { it.id }
        if (models.size != ids.size) throw notFound()
        return ids.map { id -> models.getValue(id) }
    }

    /**
     * Resolve models the user may use for chat: owned by the user, or on a built-in connection
     * allocated to the user. Foreign/unallocated/missing IDs are rejected with a non-disclosing 404.
     */
    fun requireSelectable(userId: UUID, ids: List<UUID>): List<ConfiguredModel> {
        if (ids.distinct().size != ids.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate configured model IDs are not allowed")
        }
        val models = repository.findSelectableByIds(ids, userId).associateBy { it.id }
        if (models.size != ids.size) throw notFound()
        return ids.map { id -> models.getValue(id) }
    }

    private fun validateCapabilities(connection: Connection, overrides: Map<String, Any?>) {
        ProtocolDefinitions.mergeCapabilities(
            ProtocolDefinitions.require(connection.protocol).baseline,
            overrides,
        )
    }

    private fun mergePatch(existing: Map<String, Any?>, patch: Map<String, Any?>): Map<String, Any?> =
        existing.toMutableMap().apply {
            patch.forEach { (key, value) ->
                if (value == null) remove(key) else put(key, value)
            }
        }.toMap()

    private fun requiredText(value: String, field: String): String =
        value.trim().takeIf { it.isNotEmpty() }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$field must not be blank")

    private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND, "Configured model not found")

    private fun <T> blocking(block: () -> T): Mono<T> =
        Mono.fromCallable(block).subscribeOn(Schedulers.boundedElastic())
}
