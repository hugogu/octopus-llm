package com.octopusllm.admin

import com.octopusllm.api.v2.PageResponse
import com.octopusllm.api.v2.toPageResponse
import com.octopusllm.auth.User
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

data class AdminUserResponse(
    val id: UUID,
    val email: String,
    val emailVerified: Boolean,
    val isActive: Boolean,
    val isDisabled: Boolean,
    val isAdmin: Boolean,
    val suspectedTest: Boolean,
    val createdAt: Instant,
)

@RestController
@RequestMapping("/api/v2/admin/users")
class AdminUserController(private val service: AdminUserService) {

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "false") testOnly: Boolean,
    ): Mono<PageResponse<AdminUserResponse>> =
        service.list(q, testOnly, page, size).map { result -> result.toPageResponse(::response) }

    @PostMapping("/{id}/activate")
    fun activate(
        @AuthenticationPrincipal principal: String,
        @PathVariable id: UUID,
    ): Mono<AdminUserResponse> =
        service.activate(adminId(principal), id).map(::response)

    @PostMapping("/{id}/deactivate")
    fun deactivate(
        @AuthenticationPrincipal principal: String,
        @PathVariable id: UUID,
    ): Mono<AdminUserResponse> =
        service.deactivate(adminId(principal), id).map(::response)

    @PostMapping("/{id}/disable")
    fun disable(
        @AuthenticationPrincipal principal: String,
        @PathVariable id: UUID,
    ): Mono<AdminUserResponse> =
        service.disable(adminId(principal), id).map(::response)

    @PostMapping("/{id}/enable")
    fun enable(
        @AuthenticationPrincipal principal: String,
        @PathVariable id: UUID,
    ): Mono<AdminUserResponse> =
        service.enable(adminId(principal), id).map(::response)

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun resetPassword(
        @AuthenticationPrincipal principal: String,
        @PathVariable id: UUID,
    ): Mono<Map<String, String>> =
        service.resetPassword(adminId(principal), id)
            .thenReturn(mapOf("status" to "reset_email_sent"))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @AuthenticationPrincipal principal: String,
        @PathVariable id: UUID,
    ): Mono<Void> = service.delete(adminId(principal), id).then()

    @PostMapping("/purge-test")
    fun purgeTestAccounts(
        @AuthenticationPrincipal principal: String,
    ): Mono<Map<String, Int>> =
        service.purgeTestAccounts(adminId(principal)).map { mapOf("deleted" to it) }

    private fun adminId(principal: String): UUID = UUID.fromString(principal)

    private fun response(user: User) = AdminUserResponse(
        id = user.id,
        email = user.email,
        emailVerified = user.emailVerified,
        isActive = user.isActive,
        isDisabled = user.isDisabled,
        isAdmin = user.isAdmin,
        suspectedTest = !user.isAdmin && TestAccountHeuristic.isTestEmail(user.email),
        createdAt = user.createdAt,
    )
}
