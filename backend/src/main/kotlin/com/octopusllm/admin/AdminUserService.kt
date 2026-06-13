package com.octopusllm.admin

import com.octopusllm.api.v2.boundedPageRequest
import com.octopusllm.auth.EmailService
import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import org.springframework.dao.ConcurrencyFailureException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.UUID

@Service
class AdminUserService(
    private val userRepository: UserRepository,
    private val txOps: AdminUserTxOps,
    private val auditService: AdminAuditService,
    private val emailService: EmailService,
) {
    fun list(query: String?, testOnly: Boolean, page: Int, size: Int): Mono<Page<User>> = blocking {
        val pageable = boundedPageRequest(
            page,
            size,
            Sort.Order.asc("createdAt"),
            Sort.Order.asc("id"),
        )
        val trimmed = query?.trim()
        when {
            testOnly -> userRepository.findSuspectedTestAccounts(pageable)
            trimmed.isNullOrEmpty() -> userRepository.findAll(pageable)
            else -> userRepository.findByEmailContainingIgnoreCase(trimmed, pageable)
        }
    }

    fun delete(adminId: UUID, userId: UUID): Mono<Unit> = blocking {
        withSerializableRetry { txOps.delete(userId) }
        auditService.record(adminId, AdminAuditAction.DELETE_USER, AdminAuditTargetType.USER, userId)
        Unit
    }

    fun purgeTestAccounts(adminId: UUID): Mono<Int> = blocking {
        val ids = withSerializableRetry { txOps.deleteAllTestAccounts() }
        ids.forEach { id ->
            auditService.record(adminId, AdminAuditAction.DELETE_USER, AdminAuditTargetType.USER, id)
        }
        ids.size
    }

    fun activate(adminId: UUID, userId: UUID): Mono<User> = blocking {
        val user = withSerializableRetry { txOps.activate(userId) }
        auditService.record(adminId, AdminAuditAction.ACTIVATE, AdminAuditTargetType.USER, userId)
        user
    }

    fun deactivate(adminId: UUID, userId: UUID): Mono<User> = blocking {
        val user = withSerializableRetry { txOps.deactivate(userId) }
        auditService.record(adminId, AdminAuditAction.DEACTIVATE, AdminAuditTargetType.USER, userId)
        user
    }

    fun disable(adminId: UUID, userId: UUID): Mono<User> = blocking {
        val user = withSerializableRetry { txOps.disable(userId) }
        auditService.record(adminId, AdminAuditAction.DISABLE, AdminAuditTargetType.USER, userId)
        user
    }

    fun enable(adminId: UUID, userId: UUID): Mono<User> = blocking {
        val user = withSerializableRetry { txOps.enable(userId) }
        auditService.record(adminId, AdminAuditAction.ENABLE, AdminAuditTargetType.USER, userId)
        user
    }

    fun resetPassword(adminId: UUID, userId: UUID): Mono<Unit> = blocking {
        val (user, reset) = withSerializableRetry { txOps.prepareReset(userId) }
        emailService.sendPasswordResetEmail(user.email, reset.token)
        auditService.record(adminId, AdminAuditAction.RESET_PASSWORD, AdminAuditTargetType.USER, userId)
        Unit
    }

    private fun <T> withSerializableRetry(maxAttempts: Int = 5, block: () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (ex: ConcurrencyFailureException) {
                if (++attempt >= maxAttempts) throw ex
            }
        }
    }

    private fun <T> blocking(block: () -> T): Mono<T> =
        Mono.fromCallable(block).subscribeOn(Schedulers.boundedElastic())
}
