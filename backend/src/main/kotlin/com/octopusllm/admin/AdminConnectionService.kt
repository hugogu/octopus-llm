package com.octopusllm.admin

import com.octopusllm.api.v2.boundedPageRequest
import com.octopusllm.auth.UserRepository
import com.octopusllm.connection.ConfiguredModel
import com.octopusllm.connection.ConfiguredModelRepository
import com.octopusllm.connection.Connection
import com.octopusllm.connection.ConnectionEndpointPolicy
import com.octopusllm.connection.ConnectionRepository
import com.octopusllm.model.ProtocolDefinitions
import com.octopusllm.userconfig.ApiKeyEncryptionService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant
import java.math.BigDecimal
import java.util.UUID

/**
 * Manages platform-owned built-in connections and their allocations. Built-in connections are
 * ordinary [Connection] rows with `isBuiltin = true`, owned by the admin that created them; any admin
 * can manage them. Key material is encrypted at rest and never returned.
 */
@Service
class AdminConnectionService(
    private val userRepository: UserRepository,
    private val connectionRepository: ConnectionRepository,
    private val configuredModelRepository: ConfiguredModelRepository,
    private val allocationRepository: ConnectionAllocationRepository,
    private val encryptionService: ApiKeyEncryptionService,
    private val endpointPolicy: ConnectionEndpointPolicy,
    private val auditService: AdminAuditService,
    private val connectionService: com.octopusllm.connection.ConnectionService,
    private val capabilityDetector: com.octopusllm.model.CapabilityDetector,
    private val capabilityFiller: com.octopusllm.connection.CapabilityFiller,
) {
    // --- Connection CRUD -----------------------------------------------------

    fun createBuiltin(adminId: UUID, protocol: String, label: String?, baseUrl: String, apiKey: String): Mono<Connection> =
        blocking {
            ProtocolDefinitions.require(protocol)
            val owner = userRepository.findById(adminId).orElseThrow { userNotFound() }
            val encrypted = encryptionService.encrypt(apiKey)
            val saved = connectionRepository.save(
                Connection(
                    user = owner,
                    protocol = protocol,
                    label = normalizeLabel(label),
                    baseUrl = endpointPolicy.normalizeAndValidate(baseUrl),
                    encryptedKey = encrypted.ciphertext,
                    keyIv = encrypted.iv,
                    isBuiltin = true,
                ),
            )
            auditService.record(adminId, AdminAuditAction.BUILTIN_CONNECTION_CREATE, AdminAuditTargetType.CONNECTION, saved.id)
            saved
        }

    fun listBuiltin(page: Int, size: Int): Mono<Page<Connection>> =
        blocking {
            connectionRepository.findByIsBuiltinTrue(
                boundedPageRequest(page, size, Sort.Order.asc("createdAt"), Sort.Order.asc("id")),
            )
        }

    fun patchBuiltin(adminId: UUID, id: UUID, label: String?, baseUrl: String?): Mono<Connection> =
        blocking {
            val connection = requireBuiltin(id)
            if (label != null) connection.label = normalizeLabel(label)
            if (baseUrl != null) connection.baseUrl = endpointPolicy.normalizeAndValidate(baseUrl)
            connection.updatedAt = Instant.now()
            val saved = connectionRepository.save(connection)
            auditService.record(adminId, AdminAuditAction.BUILTIN_CONNECTION_UPDATE, AdminAuditTargetType.CONNECTION, id)
            saved
        }

    fun rotateKey(adminId: UUID, id: UUID, apiKey: String): Mono<Unit> =
        blocking {
            val connection = requireBuiltin(id)
            val encrypted = encryptionService.encrypt(apiKey)
            connection.encryptedKey = encrypted.ciphertext
            connection.keyIv = encrypted.iv
            connection.updatedAt = Instant.now()
            connectionRepository.save(connection)
            auditService.record(adminId, AdminAuditAction.BUILTIN_CONNECTION_UPDATE, AdminAuditTargetType.CONNECTION, id)
            Unit
        }

    fun deleteBuiltin(adminId: UUID, id: UUID): Mono<Unit> =
        blocking {
            connectionRepository.delete(requireBuiltin(id))
            auditService.record(adminId, AdminAuditAction.BUILTIN_CONNECTION_DELETE, AdminAuditTargetType.CONNECTION, id)
            Unit
        }

    fun modelCount(connectionId: UUID): Long = configuredModelRepository.countByConnectionId(connectionId)
    fun allocatedUserCount(connectionId: UUID): Long = allocationRepository.countByIdConnectionId(connectionId)

    // --- Model management ----------------------------------------------------

    fun addModel(
        connectionId: UUID,
        modelId: String,
        displayName: String,
        capabilityOverrides: Map<String, Any?>,
        customParams: Map<String, Any?>,
        isEnabled: Boolean,
        inputPricePerMtok: BigDecimal?,
        outputPricePerMtok: BigDecimal?,
        priceCurrency: String?,
    ): Mono<ConfiguredModel> = blocking {
        val connection = requireBuiltin(connectionId)
        // Auto-detect modalities from the catalogue when not provided (feature 007, US7).
        val effectiveOverrides = if (capabilityOverrides.containsKey("input_modalities")) {
            capabilityOverrides
        } else {
            capabilityDetector.detectCached(connection.protocol, modelId.trim())
                ?.let {
                    capabilityOverrides.toMutableMap().apply {
                        put("input_modalities", it)
                        put("capability_autodetected", true)
                    }
                }
                ?: capabilityOverrides
        }
        validateCapabilities(connection, effectiveOverrides)
        val pricing = validatePricing(inputPricePerMtok, outputPricePerMtok, priceCurrency)
        configuredModelRepository.save(
            ConfiguredModel(
                user = connection.user,
                connection = connection,
                modelId = requiredText(modelId, "modelId"),
                displayName = requiredText(displayName, "displayName"),
                capabilityOverrides = effectiveOverrides,
                customParams = customParams,
                isEnabled = isEnabled,
                sortOrder = configuredModelRepository.countByConnectionId(connectionId).toInt(),
                inputPricePerMtok = pricing.first,
                outputPricePerMtok = pricing.second,
                priceCurrency = pricing.third,
            ),
        )
    }

    /** Discover model IDs from the built-in connection's provider endpoint (for bulk-add). */
    fun listEndpointModels(connectionId: UUID): Mono<List<String>> =
        blocking { requireBuiltin(connectionId) }.flatMap { connectionService.fetchEndpointModels(it) }

    /**
     * Detect media capability for a built-in connection's models (feature 007, US7). Fill-only —
     * manual settings are preserved. Returns how many models were updated.
     */
    fun detectCapabilities(connectionId: UUID): Mono<Int> = blocking {
        requireBuiltin(connectionId)
        val models = configuredModelRepository.findByConnectionId(connectionId, org.springframework.data.domain.Pageable.unpaged()).content
        capabilityFiller.fill(models).map { configuredModelRepository.save(it) }.size
    }

    fun listModels(connectionId: UUID, page: Int, size: Int): Mono<Page<ConfiguredModel>> = blocking {
        requireBuiltin(connectionId)
        configuredModelRepository.findByConnectionId(
            connectionId,
            boundedPageRequest(page, size, Sort.Order.asc("sortOrder"), Sort.Order.asc("createdAt"), Sort.Order.asc("id")),
        )
    }

    fun patchModel(
        connectionId: UUID,
        configuredModelId: UUID,
        displayName: String?,
        isEnabled: Boolean?,
        sortOrder: Int?,
        inputPricePerMtok: BigDecimal?,
        outputPricePerMtok: BigDecimal?,
        priceCurrency: String?,
        capabilityOverrides: Map<String, Any?>? = null,
    ): Mono<ConfiguredModel> = blocking {
        val model = requireModel(connectionId, configuredModelId)
        if (displayName != null) model.displayName = requiredText(displayName, "displayName")
        if (isEnabled != null) model.isEnabled = isEnabled
        if (capabilityOverrides != null) {
            // Merge so toggles (which send only input_modalities) preserve other override keys.
            model.capabilityOverrides = model.capabilityOverrides.toMutableMap().apply { putAll(capabilityOverrides) }
            validateCapabilities(model.connection, model.capabilityOverrides)
        }
        if (sortOrder != null) {
            if (sortOrder < 0) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "sortOrder must not be negative")
            model.sortOrder = sortOrder
        }
        if (inputPricePerMtok != null || outputPricePerMtok != null || priceCurrency != null) {
            val pricing = validatePricing(inputPricePerMtok, outputPricePerMtok, priceCurrency)
            model.inputPricePerMtok = pricing.first
            model.outputPricePerMtok = pricing.second
            model.priceCurrency = pricing.third
        }
        model.updatedAt = Instant.now()
        configuredModelRepository.save(model)
    }

    fun deleteModel(connectionId: UUID, configuredModelId: UUID): Mono<Unit> = blocking {
        configuredModelRepository.delete(requireModel(connectionId, configuredModelId))
        Unit
    }

    // --- Allocation ----------------------------------------------------------

    fun allocate(adminId: UUID, connectionId: UUID, userId: UUID): Mono<Unit> = blocking {
        requireBuiltin(connectionId)
        val target = userRepository.findById(userId).orElseThrow { userNotFound() }
        if (!target.isActive) {
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "User must be activated before allocation")
        }
        val key = ConnectionAllocationId(connectionId, userId)
        if (!allocationRepository.existsById(key)) {
            allocationRepository.save(ConnectionAllocation(id = key, allocatedBy = adminId))
        }
        auditService.record(
            adminId,
            AdminAuditAction.ALLOCATE,
            AdminAuditTargetType.CONNECTION,
            connectionId,
            mapOf("userId" to userId.toString()),
        )
        Unit
    }

    fun revoke(adminId: UUID, connectionId: UUID, userId: UUID): Mono<Unit> = blocking {
        requireBuiltin(connectionId)
        allocationRepository.deleteAllocation(connectionId, userId)
        auditService.record(
            adminId,
            AdminAuditAction.REVOKE,
            AdminAuditTargetType.CONNECTION,
            connectionId,
            mapOf("userId" to userId.toString()),
        )
        Unit
    }

    fun listAllocations(connectionId: UUID, page: Int, size: Int): Mono<Page<AdminAllocationView>> = blocking {
        requireBuiltin(connectionId)
        val pageResult = allocationRepository.findByIdConnectionId(
            connectionId,
            boundedPageRequest(page, size, Sort.Order.asc("createdAt")),
        )
        val emails = userRepository.findAllById(pageResult.content.map { it.id.userId })
            .associate { it.id to it.email }
        pageResult.map { allocation ->
            AdminAllocationView(
                userId = allocation.id.userId,
                email = emails[allocation.id.userId] ?: "",
                createdAt = allocation.createdAt,
            )
        }
    }

    // --- helpers -------------------------------------------------------------

    private fun requireBuiltin(id: UUID): Connection =
        connectionRepository.findByIdAndIsBuiltinTrue(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Built-in connection not found")

    private fun requireModel(connectionId: UUID, configuredModelId: UUID): ConfiguredModel {
        requireBuiltin(connectionId)
        return configuredModelRepository.findByIdAndConnectionId(configuredModelId, connectionId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Configured model not found")
    }

    private fun validateCapabilities(connection: Connection, overrides: Map<String, Any?>) {
        ProtocolDefinitions.mergeCapabilities(
            ProtocolDefinitions.require(connection.protocol).baseline,
            overrides,
        )
    }

    private fun normalizeLabel(label: String?): String? = label?.trim()?.takeIf { it.isNotEmpty() }

    private fun requiredText(value: String, field: String): String =
        value.trim().takeIf { it.isNotEmpty() }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$field must not be blank")

    private fun userNotFound() = ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

    private fun validatePricing(
        input: BigDecimal?,
        output: BigDecimal?,
        currency: String?,
    ): Triple<BigDecimal?, BigDecimal?, String?> {
        if (input != null && input.signum() < 0 || output != null && output.signum() < 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Model prices must not be negative")
        }
        if (input == null && output == null && currency.isNullOrBlank()) return Triple(null, null, null)
        val normalized = currency?.trim()?.uppercase()
        if (normalized == null || !normalized.matches(Regex("^[A-Z]{3}$"))) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "priceCurrency must be a three-letter uppercase code")
        }
        return Triple(input, output, normalized)
    }

    private fun <T> blocking(block: () -> T): Mono<T> =
        Mono.fromCallable(block).subscribeOn(Schedulers.boundedElastic())
}

data class AdminAllocationView(
    val userId: UUID,
    val email: String,
    val createdAt: Instant,
)
