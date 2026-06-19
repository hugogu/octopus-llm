package com.octopusllm.migration

import jakarta.validation.constraints.NotNull
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant
import java.util.UUID

/**
 * Admin migration endpoints under the admin-migration prefix (feature 008, T027). `ROLE_ADMIN` only
 * (enforced by [com.octopusllm.config.SecurityConfig] on the admin path prefix). The artifact passphrase
 * is resolved from the request, falling back to the optional `MIGRATION_ARTIFACT_PASSPHRASE` config
 * property; it is held only in memory and never bound into a logged object or persisted (Constitution
 * VI). Blocking JPA/crypto/IO work runs on the bounded-elastic scheduler.
 */
@RestController
@RequestMapping("/api/v2/admin/migration")
class MigrationController(
    private val exportService: MigrationExportService,
    private val importService: MigrationImportService,
    private val operationService: MigrationOperationService,
    @Value("\${migration.artifact-passphrase:}") private val configuredPassphrase: String,
) {
    private val bufferFactory = DefaultDataBufferFactory()

    data class ExportRequest(
        @field:NotNull val acknowledgeSensitiveExport: Boolean? = null,
        val passphrase: String? = null,
    )

    @PostMapping("/export")
    fun export(
        @AuthenticationPrincipal principal: String,
        @RequestBody request: ExportRequest,
    ): Mono<ResponseEntity<Flux<DataBuffer>>> {
        if (request.acknowledgeSensitiveExport != true) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "sensitive_export_ack_required")
        }
        val passphrase = resolveExportPassphrase(request.passphrase)
        val adminId = UUID.fromString(principal)
        return Mono.fromCallable {
            operationService.startExport(adminId)
            exportService.export(passphrase)
        }.subscribeOn(Schedulers.boundedElastic()).map { bytes ->
            val filename = "octopus-export-${Instant.now().toEpochMilli()}.octopus"
            ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(Flux.just(bufferFactory.wrap(bytes)))
        }
    }

    @PostMapping("/import")
    fun import(
        @AuthenticationPrincipal principal: String,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestPart("file") file: FilePart,
        @RequestPart(value = "passphrase", required = false) passphrase: String?,
    ): Mono<MigrationImportResult> {
        val key = idempotencyKey?.takeIf { it.isNotBlank() }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "idempotency_key_required")
        val effectivePassphrase = passphrase?.takeIf { it.isNotBlank() } ?: configuredPassphrase
        val adminId = UUID.fromString(principal)
        return DataBufferUtils.join(file.content())
            .map { buffer ->
                val bytes = ByteArray(buffer.readableByteCount())
                buffer.read(bytes)
                DataBufferUtils.release(buffer)
                bytes
            }
            .flatMap { bytes ->
                Mono.fromCallable { importService.import(bytes, effectivePassphrase, adminId, key) }
                    .subscribeOn(Schedulers.boundedElastic())
            }
    }

    /**
     * Request passphrase wins; otherwise the configured one; otherwise reject. A supplied passphrase
     * must be at least 16 chars. The configured fallback is trusted as-is (operator-controlled).
     */
    private fun resolveExportPassphrase(requested: String?): String {
        val supplied = requested?.takeIf { it.isNotBlank() }
        if (supplied != null) {
            if (supplied.length < 16) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "passphrase_too_short")
            }
            return supplied
        }
        return configuredPassphrase.takeIf { it.isNotBlank() }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "passphrase_required")
    }
}
