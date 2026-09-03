package com.octopusllm.admin

import com.octopusllm.api.v2.PageResponse
import com.octopusllm.connection.ConfiguredModel
import com.octopusllm.connection.ConfiguredModelRepository
import com.octopusllm.llm.CapabilityMatrix
import com.octopusllm.model.ProtocolDefinitions
import com.octopusllm.auth.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

data class AdminModelAccessView(
    val id: UUID,
    val connection: AdminModelConnectionView,
    val modelId: String,
    val displayName: String,
    val protocol: String,
    val capabilities: Map<String, Any?>,
    val isEnabled: Boolean,
    val isAnonymousAllowed: Boolean,
)

data class AdminModelConnectionView(val id: UUID, val label: String?)

data class AdminModelFilter(
    val q: String? = null,
    val connectionId: UUID? = null,
    val protocol: String? = null,
    val enabled: Boolean? = null,
    val anonymousAllowed: Boolean? = null,
)

data class AdminModelSelection(
    val mode: String,
    val ids: List<UUID> = emptyList(),
    val filter: AdminModelFilter? = null,
    val excludeIds: List<UUID> = emptyList(),
)

data class AdminModelBulkPreviewRequest(val action: String, val selection: AdminModelSelection)

data class AdminModelBulkPreviewResponse(
    val operationId: UUID,
    val action: String,
    val targetCount: Int,
    val expiresAt: Instant,
    val summary: Map<String, Int>,
)

data class AdminModelBulkItemResponse(
    val configuredModelId: UUID,
    val displayName: String,
    val outcome: String,
    val errorCode: String?,
    val errorMessage: String?,
)

data class AdminModelBulkOperationResponse(
    val operationId: UUID,
    val status: String,
    val action: String,
    val targetCount: Int,
    val changedCount: Int,
    val alreadySatisfiedCount: Int,
    val failedCount: Int,
    val items: List<AdminModelBulkItemResponse>,
)

@Service
class AdminModelAccessService(
    private val configuredModelRepository: ConfiguredModelRepository,
    private val userRepository: UserRepository,
    private val operationRepository: AdminModelBulkOperationRepository,
    private val itemRepository: AdminModelBulkOperationItemRepository,
    private val auditService: AdminAuditService,
    @Value("\${app.anonymous.operation-max-targets:1000}") private val maxTargets: Int,
) {
    companion object {
        private val ACTIONS = setOf("ALLOW_ANONYMOUS", "REVOKE_ANONYMOUS", "SHOW", "HIDE", "DELETE")
        private val SORT_FIELDS = mapOf(
            "displayName" to "displayName",
            "modelId" to "modelId",
            "createdAt" to "createdAt",
        )
    }

    fun list(
        page: Int,
        size: Int,
        q: String?,
        connectionId: UUID?,
        protocol: String?,
        enabled: Boolean?,
        anonymousAllowed: Boolean?,
        sort: String,
        direction: String,
    ): Mono<PageResponse<AdminModelAccessView>> = Mono.fromCallable {
        if (page < 0 || size !in 1..100) throw badRequest("page must be at least 0 and size must be between 1 and 100")
        val property = SORT_FIELDS[sort] ?: throw badRequest("Unsupported sort field")
        val normalizedQuery = q?.trim()?.lowercase().orEmpty()
        val sortDirection = when (direction.lowercase()) {
            "asc" -> Sort.Direction.ASC
            "desc" -> Sort.Direction.DESC
            else -> throw badRequest("direction must be asc or desc")
        }
        val pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Order(sortDirection, property), Sort.Order.asc("id")),
        )
        configuredModelRepository.findBuiltinForAdmin(
            q = normalizedQuery,
            connectionId = connectionId,
            protocol = protocol?.trim()?.takeIf { it.isNotEmpty() },
            enabled = enabled,
            anonymousAllowed = anonymousAllowed,
            pageable = pageable,
        ).let { pageResult ->
            PageResponse(
                items = pageResult.content.map(::modelView),
                page = pageResult.number,
                size = pageResult.size,
                totalElements = pageResult.totalElements,
                totalPages = pageResult.totalPages,
            )
        }
    }.subscribeOn(Schedulers.boundedElastic())

    fun preview(adminId: UUID, request: AdminModelBulkPreviewRequest): Mono<AdminModelBulkPreviewResponse> =
        Mono.fromCallable {
            requireAction(request.action)
            val targets = selectTargets(request.selection)
            if (targets.isEmpty()) throw badRequest("Selection must contain at least one model")
            if (targets.size > maxTargets.coerceAtLeast(1)) throw badRequest("Selection exceeds the operation limit")
            val user = userRepository.findById(adminId).orElseThrow { forbidden() }
            val expiresAt = Instant.now().plusSeconds(15 * 60)
            val operation = operationRepository.save(
                AdminModelBulkOperation(
                    adminUser = user,
                    action = request.action,
                    selectionMode = request.selection.mode,
                    selectionFilter = selectionMap(request.selection),
                    status = "PREVIEWED",
                    targetCount = targets.size,
                    expiresAt = expiresAt,
                ),
            )
            itemRepository.saveAll(targets.map { model ->
                AdminModelBulkOperationItem(
                    operationId = operation.id,
                    configuredModelId = model.id,
                    modelIdSnapshot = model.modelId,
                    displayNameSnapshot = model.displayName,
                    connectionLabelSnapshot = model.connection.label,
                    previousIsEnabled = model.isEnabled,
                    previousIsAnonymousAllowed = model.isAnonymousAllowed,
                )
            })
            val alreadySatisfied = targets.count { alreadySatisfied(it, request.action) }
            AdminModelBulkPreviewResponse(
                operationId = operation.id,
                action = operation.action,
                targetCount = operation.targetCount,
                expiresAt = operation.expiresAt,
                summary = mapOf(
                    "alreadySatisfied" to alreadySatisfied,
                    "eligible" to targets.size - alreadySatisfied,
                    "unavailable" to 0,
                ),
            )
        }.subscribeOn(Schedulers.boundedElastic())

    fun execute(adminId: UUID, operationId: UUID, idempotencyKey: String): Mono<AdminModelBulkOperationResponse> =
        Mono.fromCallable {
            if (idempotencyKey.isBlank()) throw badRequest("Idempotency-Key is required")
            val operation = operationRepository.findByIdAndAdminUserId(operationId, adminId) ?: throw notFound()
            val keyHash = sha256(idempotencyKey)
            operation.idempotencyKeyHash?.let { existing ->
                if (existing != keyHash) throw conflict("Operation was already executed with another idempotency key")
            }
            if (operation.status != "PREVIEWED") return@fromCallable operationResponse(operation)
            if (operation.expiresAt.isBefore(Instant.now())) throw conflict("The preview has expired")
            operation.idempotencyKeyHash = keyHash
            operation.status = "RUNNING"
            operation.startedAt = Instant.now()
            operationRepository.save(operation)

            val items = itemRepository.findByOperationIdOrderByDisplayNameSnapshotAsc(operation.id)
            items.forEach { item ->
                try {
                    processItem(adminId, operation, item)
                } catch (_: Exception) {
                    failItem(item, "MODEL_OPERATION_FAILED", "The model operation could not be completed")
                }
            }
            operation.processedCount = items.size
            operation.successCount = operation.changedCount + operation.alreadySatisfiedCount
            operation.failureCount = items.count { it.outcome == "FAILED" }
            operation.status = if (operation.failureCount == 0) "COMPLETED" else "PARTIAL_FAILURE"
            operation.completedAt = Instant.now()
            operationRepository.save(operation)
            auditService.record(
                adminId,
                AdminAuditAction.MODEL_BULK_OPERATION,
                AdminAuditTargetType.MODEL_BULK_OPERATION,
                operation.id,
                mapOf("action" to operation.action, "targetCount" to operation.targetCount, "failedCount" to operation.failureCount),
            )
            operationResponse(operation)
        }.subscribeOn(Schedulers.boundedElastic())

    fun get(adminId: UUID, operationId: UUID): Mono<AdminModelBulkOperationResponse> = Mono.fromCallable {
        val operation = operationRepository.findByIdAndAdminUserId(operationId, adminId) ?: throw notFound()
        operationResponse(operation)
    }.subscribeOn(Schedulers.boundedElastic())

    private fun processItem(adminId: UUID, operation: AdminModelBulkOperation, item: AdminModelBulkOperationItem) {
        val model = configuredModelRepository.findById(item.configuredModelId).orElse(null)
        if (model == null) {
            item.outcome = "ALREADY_DELETED"
            item.processedAt = Instant.now()
            itemRepository.save(item)
            return
        }
        if (!model.connection.isBuiltin) {
            failItem(item, "MODEL_SCOPE_CHANGED", "The model is no longer administrator-managed")
            return
        }
        item.outcome = when {
            operation.action == "DELETE" -> {
                configuredModelRepository.delete(model)
                auditService.record(adminId, AdminAuditAction.MODEL_DELETE, AdminAuditTargetType.MODEL, model.id)
                operation.changedCount++
                "CHANGED"
            }
            alreadySatisfied(model, operation.action) -> {
                operation.alreadySatisfiedCount++
                "ALREADY_SATISFIED"
            }
            else -> {
                when (operation.action) {
                    "ALLOW_ANONYMOUS" -> model.isAnonymousAllowed = true
                    "REVOKE_ANONYMOUS" -> model.isAnonymousAllowed = false
                    "SHOW" -> model.isEnabled = true
                    "HIDE" -> model.isEnabled = false
                }
                configuredModelRepository.save(model)
                auditService.record(adminId, auditAction(operation.action), AdminAuditTargetType.MODEL, model.id)
                operation.changedCount++
                "CHANGED"
            }
        }
        item.processedAt = Instant.now()
        itemRepository.save(item)
    }

    private fun failItem(item: AdminModelBulkOperationItem, code: String, message: String) {
        item.outcome = "FAILED"
        item.errorCode = code
        item.errorMessage = message
        item.processedAt = Instant.now()
        itemRepository.save(item)
    }

    private fun selectTargets(selection: AdminModelSelection): List<ConfiguredModel> {
        val excluded = selection.excludeIds.toSet()
        val ids = when (selection.mode.uppercase()) {
            "IDS" -> selection.ids.distinct().filterNot(excluded::contains)
            "FILTER" -> {
                val filter = selection.filter ?: throw badRequest("Filter selection requires a filter")
                val page = configuredModelRepository.findBuiltinForAdmin(
                    q = filter.q?.trim()?.lowercase().orEmpty(),
                    connectionId = filter.connectionId,
                    protocol = filter.protocol?.trim()?.takeIf { it.isNotEmpty() },
                    enabled = filter.enabled,
                    anonymousAllowed = filter.anonymousAllowed,
                    pageable = PageRequest.of(
                        0,
                        maxTargets.coerceAtLeast(1) + 1,
                        Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("createdAt"), Sort.Order.asc("id")),
                    ),
                )
                if (page.totalElements > maxTargets.coerceAtLeast(1)) throw badRequest("Selection exceeds the operation limit")
                page.content.map { it.id }.filterNot(excluded::contains)
            }
            else -> throw badRequest("Selection mode must be IDS or FILTER")
        }
        if (ids.isEmpty()) return emptyList()
        return configuredModelRepository.findAllById(ids)
            .filter { it.connection.isBuiltin }
            .sortedBy { ids.indexOf(it.id) }
    }

    private fun selectionMap(selection: AdminModelSelection): Map<String, Any?> = mapOf(
        "mode" to selection.mode.uppercase(),
        "ids" to selection.ids.distinct().map(UUID::toString),
        "excludeIds" to selection.excludeIds.distinct().map(UUID::toString),
        "filter" to selection.filter,
    )

    private fun modelView(model: ConfiguredModel): AdminModelAccessView {
        val protocol = ProtocolDefinitions.require(model.connection.protocol)
        val capabilities: CapabilityMatrix = ProtocolDefinitions.mergeCapabilities(protocol.baseline, model.capabilityOverrides)
        return AdminModelAccessView(
            id = model.id,
            connection = AdminModelConnectionView(model.connection.id, model.connection.label),
            modelId = model.modelId,
            displayName = model.displayName,
            protocol = protocol.id,
            capabilities = mapOf(
                "streaming" to capabilities.supportsStreaming,
                "vision" to ("image" in capabilities.inputModalities),
                "tools" to capabilities.supportsFunctionCalling,
            ),
            isEnabled = model.isEnabled,
            isAnonymousAllowed = model.isAnonymousAllowed,
        )
    }

    private fun operationResponse(operation: AdminModelBulkOperation): AdminModelBulkOperationResponse {
        val items = itemRepository.findByOperationIdOrderByDisplayNameSnapshotAsc(operation.id).map {
            AdminModelBulkItemResponse(it.configuredModelId, it.displayNameSnapshot, it.outcome, it.errorCode, it.errorMessage)
        }
        return AdminModelBulkOperationResponse(
            operationId = operation.id,
            status = operation.status,
            action = operation.action,
            targetCount = operation.targetCount,
            changedCount = operation.changedCount,
            alreadySatisfiedCount = operation.alreadySatisfiedCount,
            failedCount = operation.failureCount,
            items = items,
        )
    }

    private fun alreadySatisfied(model: ConfiguredModel, action: String): Boolean = when (action) {
        "ALLOW_ANONYMOUS" -> model.isAnonymousAllowed
        "REVOKE_ANONYMOUS" -> !model.isAnonymousAllowed
        "SHOW" -> model.isEnabled
        "HIDE" -> !model.isEnabled
        "DELETE" -> false
        else -> false
    }

    private fun auditAction(action: String): AdminAuditAction = when (action) {
        "ALLOW_ANONYMOUS" -> AdminAuditAction.MODEL_ANONYMOUS_ALLOW
        "REVOKE_ANONYMOUS" -> AdminAuditAction.MODEL_ANONYMOUS_REVOKE
        "SHOW" -> AdminAuditAction.MODEL_SHOW
        "HIDE" -> AdminAuditAction.MODEL_HIDE
        else -> throw badRequest("Unsupported action")
    }

    private fun requireAction(action: String) {
        if (action !in ACTIONS) throw badRequest("Unsupported bulk action")
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun badRequest(message: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, message)
    private fun forbidden() = ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access required")
    private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND, "Operation not found")
    private fun conflict(message: String) = ResponseStatusException(HttpStatus.CONFLICT, message)
}
